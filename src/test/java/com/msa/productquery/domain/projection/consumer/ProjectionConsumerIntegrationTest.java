package com.msa.productquery.domain.projection.consumer;

import static com.msa.productquery.domain.projection.EventFixtures.*;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.msa.productquery.domain.projection.repository.*;
import com.msa.productquery.global.config.database.QueryDatabaseTestSupport;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.context.*;
import org.testcontainers.kafka.KafkaContainer;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 실제 KRaft Kafka와 PostgreSQL로 소비·DLT·재시도·일시 중단 후 조회 반영을 검증합니다. */
@SpringBootTest(
        properties = {"query.events.enabled=true", "spring.kafka.listener.auto-startup=true"})
@ActiveProfiles("test")
@Import(QueryDatabaseTestSupport.Time.class)
class ProjectionConsumerIntegrationTest extends QueryDatabaseTestSupport {
    static final KafkaContainer KAFKA =
            new KafkaContainer("apache/kafka:4.1.0")
                    .withEnv("KAFKA_HEAP_OPTS", "-Xms256m -Xmx256m");

    static {
        KAFKA.start();
        try (var admin =
                Admin.create(
                        Map.of(
                                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                                KAFKA.getBootstrapServers()))) {
            admin.createTopics(
                            List.of(
                                    new NewTopic(PRODUCT_TOPIC, 2, (short) 1),
                                    new NewTopic(INQUIRY_TOPIC, 2, (short) 1),
                                    new NewTopic(PRODUCT_TOPIC + ".DLT", 2, (short) 1),
                                    new NewTopic(INQUIRY_TOPIC + ".DLT", 2, (short) 1)))
                    .all()
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("테스트 토픽 준비 실패", exception);
        }
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "product-query-test");
        registry.add("spring.kafka.admin.auto-create", () -> "false");
    }

    @Autowired KafkaTemplate<String, byte[]> kafka;
    @Autowired ProductProjectionRepository products;
    @Autowired ProcessedEventRepository receipts;
    @Autowired InquiryProjectionRepository inquiries;
    @Autowired KafkaListenerEndpointRegistry registry;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DefaultErrorHandler errors;

    @BeforeEach
    void clean() {
        registry.getListenerContainer("product-query-projection").stop();
        receipts.deleteAll();
        inquiries.deleteAll();
        products.deleteAll();
        registry.getListenerContainer("product-query-projection").start();
    }

    @Test
    void duplicateAndOutOfOrderRecordsConvergeAfterConsumerRestart() throws Exception {
        UUID id = UUID.randomUUID();
        registry.getListenerContainer("product-query-projection").stop();
        var newest = product(id, 3, "최신", NOW, 1, 2);
        kafka.send(PRODUCT_TOPIC, 0, id.toString(), mapper.writeValueAsBytes(newest))
                .get(10, TimeUnit.SECONDS);
        kafka.send(PRODUCT_TOPIC, 0, id.toString(), mapper.writeValueAsBytes(newest))
                .get(10, TimeUnit.SECONDS);
        kafka.send(
                        PRODUCT_TOPIC,
                        0,
                        id.toString(),
                        mapper.writeValueAsBytes(product(id, 1, "과거", NOW, 0, 0)))
                .get(10, TimeUnit.SECONDS);
        assertThat(products.findById(id)).isEmpty();
        registry.getListenerContainer("product-query-projection").start();
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            var saved = products.findById(id);
                            assertThat(saved).isPresent();
                            assertThat(saved.orElseThrow().sourceVersion).isEqualTo(3);
                            assertThat(receipts.count()).isEqualTo(2);
                        });
    }

    @Test
    void invalidRecordGoesToDltAndFollowingValidRecordStillUpdatesQuery() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] invalid = "{invalid-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        kafka.send(PRODUCT_TOPIC, 0, id.toString(), invalid).get(10, TimeUnit.SECONDS);
        kafka.send(
                        PRODUCT_TOPIC,
                        0,
                        id.toString(),
                        mapper.writeValueAsBytes(product(id, 1, "정상", NOW, 0, 0)))
                .get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(products.findById(id)).isPresent());
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-probe-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer =
                new KafkaConsumer<>(props, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(PRODUCT_TOPIC + ".DLT"));
            await().atMost(Duration.ofSeconds(20))
                    .until(
                            () -> {
                                for (var record : consumer.poll(Duration.ofMillis(500))) {
                                    if (Arrays.equals(record.value(), invalid)) {
                                        return true;
                                    }
                                }
                                return false;
                            });
        }
    }

    @Test
    void databaseFailureDoesNotMarkEventProcessedAndRetriesAfterRecovery() throws Exception {
        UUID id = UUID.randomUUID();
        var retries = new AtomicInteger();
        errors.setRetryListeners((record, exception, attempt) -> retries.incrementAndGet());
        // 기존 개발 DB가 아닌 격리된 테스트 DB의 테이블 이름만 잠시 바꿔 실제 SQL 장애를 만듭니다.
        jdbc.execute(
                "alter table product_query.processed_events rename to"
                        + " processed_events_unavailable");
        try {
            kafka.send(
                            PRODUCT_TOPIC,
                            0,
                            id.toString(),
                            mapper.writeValueAsBytes(product(id, 1, "복구", NOW, 0, 0)))
                    .get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(20)).until(() -> retries.get() > 0);
            assertThat(products.findById(id)).isEmpty();
        } finally {
            jdbc.execute(
                    "alter table product_query.processed_events_unavailable rename to"
                            + " processed_events");
        }
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            assertThat(products.findById(id)).isPresent();
                            assertThat(receipts.count()).isEqualTo(1);
                        });
    }

    /** Command 실제 서비스가 만든 합성 이벤트로 Kafka 및 Query 저장 계약을 함께 검증합니다. */
    @Test
    void commandProducedSnapshotsConvergeThroughKafka() throws Exception {
        UUID productId = null;
        for (String name : List.of("approved", "held", "confirmed", "confirmed", "approved")) {
            byte[] payload;
            try (var input =
                    getClass().getResourceAsStream("/contracts/command/" + name + ".json")) {
                assertThat(input).isNotNull();
                payload = input.readAllBytes();
            }
            productId = UUID.fromString(mapper.readTree(payload).path("aggregateId").asText());
            kafka.send(PRODUCT_TOPIC, productId.toString(), payload).get(20, TimeUnit.SECONDS);
        }
        UUID id = productId;
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> {
                            var saved = products.findById(id).orElseThrow();
                            assertThat(saved.sourceVersion).isEqualTo(3);
                            assertThat(saved.initialQuantity).isEqualTo(2);
                            assertThat(saved.confirmedQuantity).isEqualTo(2);
                            assertThat(saved.reservedQuantity).isZero();
                            assertThat(saved.availableQuantity).isZero();
                        });
        byte[] faq;
        byte[] inquiry;
        try (var input = getClass().getResourceAsStream("/contracts/command/faq.json")) {
            faq = input.readAllBytes();
        }
        try (var input = getClass().getResourceAsStream("/contracts/command/inquiry.json")) {
            inquiry = input.readAllBytes();
        }
        var inquiryJson = mapper.readTree(inquiry);
        UUID inquiryId = UUID.fromString(inquiryJson.path("aggregateId").asText());
        String key = inquiryJson.path("inquiry").path("productId").asText();
        kafka.send(PRODUCT_TOPIC, key, faq).get(20, TimeUnit.SECONDS);
        kafka.send(INQUIRY_TOPIC, key, inquiry).get(20, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> {
                            var saved = inquiries.findById(inquiryId).orElseThrow();
                            assertThat(saved.sourceVersion).isEqualTo(2);
                            assertThat(saved.answer).isEqualTo("판매자 확인 답변");
                            // 키 존재뿐 아니라 이벤트의 FAQ 내용이 정확히 투영됐는지 확인합니다.
                            var snapshot =
                                    mapper.readTree(
                                            products.findById(UUID.fromString(key))
                                                    .orElseThrow()
                                                    .snapshot);
                            var expectedFaq =
                                    mapper.readTree(faq).path("product").path("faqs").get(0);
                            assertThat(snapshot.path("faqs").size()).isEqualTo(1);
                            var actualFaq = snapshot.path("faqs").get(0);
                            for (String field : java.util.List.of("id", "question", "answer")) {
                                assertThat(actualFaq.path(field))
                                        .isEqualTo(expectedFaq.path(field));
                            }
                        });
    }
}
