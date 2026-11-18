package com.msa.productquery.domain.projection.service.impl;

import static com.msa.productquery.domain.projection.EventFixtures.*;

import static org.assertj.core.api.Assertions.*;

import com.msa.productquery.domain.projection.EventFixtures;
import com.msa.productquery.domain.projection.event.*;
import com.msa.productquery.domain.projection.repository.*;
import com.msa.productquery.domain.projection.service.ProjectionService;
import com.msa.productquery.global.config.database.QueryDatabaseTestSupport;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 실제 PostgreSQL로 복제본을 가정한 동시 반영·멱등·역전·tombstone을 검증합니다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(QueryDatabaseTestSupport.Time.class)
class ProjectionServiceImplTest extends QueryDatabaseTestSupport {
    @Autowired ProjectionService service;
    @Autowired ProductProjectionRepository products;
    @Autowired InquiryProjectionRepository inquiries;
    @Autowired ProcessedEventRepository receipts;
    @Autowired ObjectMapper mapper;
    @Autowired MutableClock clock;

    @BeforeEach
    void clean() {
        receipts.deleteAll();
        inquiries.deleteAll();
        products.deleteAll();
        clock.set(NOW);
    }

    void apply(ProjectionEvent event) {
        service.apply(
                PRODUCT_TOPIC, event.aggregateId().toString(), mapper.writeValueAsBytes(event));
    }

    /** 크기는 문자열 길이가 아니라 실제 Kafka 값의 UTF-8 바이트 수로 판정합니다. */
    @Test
    void acceptsMultibyteProductSnapshotBetweenTwoAndThreeMiB() {
        UUID id = UUID.randomUUID();
        var tree = mapper.valueToTree(product(id, 1, "대용량 합성 상품", NOW, 0, 0));
        var attributes =
                ((tools.jackson.databind.node.ObjectNode) tree.path("product"))
                        .putArray("attributes");
        for (int n = 0; n < 750; n++) {
            attributes.addObject().put("name", "항목" + n).put("value", "가".repeat(1000));
        }
        byte[] payload = mapper.writeValueAsBytes(tree);
        assertThat(payload.length).isGreaterThan(2 * 1024 * 1024).isLessThan(3 * 1024 * 1024);
        service.apply(PRODUCT_TOPIC, id.toString(), payload);
        assertThat(products.findById(id).orElseThrow().name).isEqualTo("대용량 합성 상품");
        assertThat(receipts.count()).isEqualTo(1);
    }

    /** 유효한 JSON 뒤의 공백도 전송 크기에 포함하여 경계값과 초과 시 원자적 거부를 검증합니다. */
    @Test
    void acceptsExactlyThreeMiBProductButRejectsOneAdditionalByteWithoutDatabaseChanges() {
        UUID id = UUID.randomUUID();
        byte[] exact =
                padded(
                        mapper.writeValueAsBytes(product(id, 1, "경계 상품", NOW, 0, 0)),
                        3 * 1024 * 1024);
        service.apply(PRODUCT_TOPIC, id.toString(), exact);
        var next =
                padded(
                        mapper.writeValueAsBytes(product(id, 2, "반영되면 안 되는 상품", NOW, 0, 0)),
                        3 * 1024 * 1024 + 1);
        assertThatThrownBy(() -> service.apply(PRODUCT_TOPIC, id.toString(), next))
                .isInstanceOf(InvalidProjectionEventException.class);
        assertThat(products.findById(id).orElseThrow().name).isEqualTo("경계 상품");
        assertThat(receipts.count()).isEqualTo(1);
    }

    @Test
    void inquiryStillAcceptsTwoMiBAndRejectsOneAdditionalByte() {
        UUID id = UUID.randomUUID(), product = UUID.randomUUID(), buyer = UUID.randomUUID();
        var exact =
                padded(
                        mapper.writeValueAsBytes(
                                EventFixtures.inquiry(id, product, buyer, 0, null)),
                        2 * 1024 * 1024);
        service.apply(INQUIRY_TOPIC, product.toString(), exact);
        var tooLarge =
                padded(
                        mapper.writeValueAsBytes(
                                EventFixtures.inquiry(id, product, buyer, 1, "새 답변")),
                        2 * 1024 * 1024 + 1);
        assertThatThrownBy(() -> service.apply(INQUIRY_TOPIC, product.toString(), tooLarge))
                .isInstanceOf(InvalidProjectionEventException.class);
        assertThat(inquiries.findById(id).orElseThrow().answer).isNull();
        assertThat(receipts.count()).isEqualTo(1);
    }

    private byte[] padded(byte[] json, int size) {
        byte[] payload = Arrays.copyOf(json, size);
        Arrays.fill(payload, json.length, size, (byte) ' ');
        return payload;
    }

    @Test
    void duplicateAndOldEventsCannotOverwriteNewestSnapshot() {
        UUID id = UUID.randomUUID();
        var newest = product(id, 3, "최신 이름", NOW, 2, 1);
        apply(newest);
        apply(newest);
        apply(product(id, 1, "과거 이름", NOW, 0, 0));
        var saved = products.findById(id).orElseThrow();
        assertThat(saved.name).isEqualTo("최신 이름");
        assertThat(saved.availableQuantity).isEqualTo(7);
        assertThat(saved.confirmedQuantity).isEqualTo(1);
        assertThat(receipts.count()).isEqualTo(2);
    }

    @Test
    void tombstoneSurvivesOlderApprovalAndAllowsOnlyNewerExplicitPublication() {
        UUID id = UUID.randomUUID();
        apply(hidden(id, 5));
        apply(product(id, 3, "오래된 승인", NOW, 0, 0));
        assertThat(products.findById(id).orElseThrow().published).isFalse();
        apply(product(id, 6, "새 공개", NOW, 0, 0));
        assertThat(products.findById(id).orElseThrow().published).isTrue();
    }

    @Test
    void reusedEventIdOrSameVersionWithDifferentPayloadIsRejected() {
        UUID id = UUID.randomUUID();
        var first = product(id, 1, "기존", NOW, 0, 0);
        apply(first);
        var changed = product(id, 2, "변조", NOW, 0, 0);
        var reused =
                new ProjectionEvent(
                        first.eventId(), 1, changed.type(), id, 2, NOW, changed.product(), null);
        assertThatThrownBy(() -> apply(reused)).isInstanceOf(InvalidProjectionEventException.class);
        assertThatThrownBy(() -> apply(product(id, 1, "충돌", NOW, 0, 0)))
                .isInstanceOf(InvalidProjectionEventException.class);
        assertThat(products.findById(id).orElseThrow().name).isEqualTo("기존");
        assertThat(receipts.count()).isEqualTo(1);
    }

    @Test
    void contractFailuresLeaveNoReceiptOrProjection() {
        UUID id = UUID.randomUUID();
        var valid = product(id, 1, "합성", NOW, 0, 0);
        assertThatThrownBy(
                        () ->
                                service.apply(
                                        PRODUCT_TOPIC,
                                        UUID.randomUUID().toString(),
                                        mapper.writeValueAsBytes(valid)))
                .isInstanceOf(InvalidProjectionEventException.class);
        assertThatThrownBy(
                        () ->
                                service.apply(
                                        INQUIRY_TOPIC,
                                        id.toString(),
                                        mapper.writeValueAsBytes(valid)))
                .isInstanceOf(InvalidProjectionEventException.class);
        assertThatThrownBy(() -> apply(product(id, 1, "음수 재고", NOW, 8, 8)))
                .isInstanceOf(InvalidProjectionEventException.class);
        assertThatThrownBy(
                        () ->
                                service.apply(
                                        PRODUCT_TOPIC,
                                        id.toString(),
                                        "{\"schemaVersion\":2}".getBytes()))
                .isInstanceOf(InvalidProjectionEventException.class);
        assertThat(products.count()).isZero();
        assertThat(receipts.count()).isZero();
    }

    @Test
    void concurrentDuplicatesAndReverseVersionsConvergeOnHighestVersion() throws Exception {
        UUID id = UUID.randomUUID();
        var duplicate = product(id, 10, "가장 최신", NOW, 1, 2);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 16; i++) {
                var event = i % 2 == 0 ? duplicate : product(id, i % 10, "과거", NOW, 0, 0);
                tasks.add(
                        () -> {
                            apply(event);
                            return null;
                        });
            }
            for (var result : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                result.get();
            }
        }
        assertThat(products.findById(id).orElseThrow().sourceVersion).isEqualTo(10);
    }

    @Test
    void inquiryCannotMoveToDifferentBuyerAndVersionsAreIndependentFromProduct() {
        UUID id = UUID.randomUUID(), product = UUID.randomUUID(), buyer = UUID.randomUUID();
        var created = EventFixtures.inquiry(id, product, buyer, 0, null);
        service.apply(INQUIRY_TOPIC, product.toString(), mapper.writeValueAsBytes(created));
        var invalid = EventFixtures.inquiry(id, product, UUID.randomUUID(), 1, "답변");
        assertThatThrownBy(
                        () ->
                                service.apply(
                                        INQUIRY_TOPIC,
                                        product.toString(),
                                        mapper.writeValueAsBytes(invalid)))
                .isInstanceOf(InvalidProjectionEventException.class);
        assertThat(inquiries.findById(id).orElseThrow().buyerId).isEqualTo(buyer);
    }

    @Test
    void concurrentReuseOfEventIdAcrossDifferentAggregatesCannotOverwriteReceipt()
            throws Exception {
        UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
        var first = product(firstId, 1, "첫 상품", NOW, 0, 0);
        var other = product(secondId, 1, "다른 상품", NOW, 0, 0);
        var reused =
                new ProjectionEvent(
                        first.eventId(), 1, other.type(), secondId, 1, NOW, other.product(), null);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.<Callable<Boolean>>of(() -> attempt(first), () -> attempt(reused));
            int success = 0;
            for (var result : pool.invokeAll(tasks, 20, TimeUnit.SECONDS)) {
                if (result.get()) {
                    success++;
                }
            }
            assertThat(success).isEqualTo(1);
        }
        assertThat(products.count()).isEqualTo(1);
        assertThat(receipts.count()).isEqualTo(1);
    }

    @Test
    void privateFieldsAndUnsafeSourceUrlsAreNotAcceptedInPublicSnapshots() {
        UUID id = UUID.randomUUID();
        var event = product(id, 1, "공개 데이터", NOW, 0, 0);
        var unsafe =
                mapper.writeValueAsString(event)
                        .replace("https://example.com/item", "javascript:alert(1)");
        assertThatThrownBy(
                        () ->
                                service.apply(
                                        PRODUCT_TOPIC,
                                        id.toString(),
                                        unsafe.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidProjectionEventException.class);
        var extra = mapper.readTree(mapper.writeValueAsBytes(event));
        ((tools.jackson.databind.node.ObjectNode) extra.get("product"))
                .put("dispatchAddress", "합성 비공개 필드");
        assertThatThrownBy(
                        () ->
                                service.apply(
                                        PRODUCT_TOPIC,
                                        id.toString(),
                                        mapper.writeValueAsBytes(extra)))
                .isInstanceOf(InvalidProjectionEventException.class);
        assertThat(products.count()).isZero();
        assertThat(receipts.count()).isZero();
    }

    private boolean attempt(ProjectionEvent event) {
        try {
            apply(event);
            return true;
        } catch (InvalidProjectionEventException
                | org.springframework.dao.DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Test
    void exportsSyntheticContractExamples() throws Exception {
        UUID product = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var dir = java.nio.file.Path.of("build/generated/events");
        java.nio.file.Files.createDirectories(dir);
        var examples =
                Map.of(
                        "product-upsert.json", product(product, 1, "계약 예시 상품", NOW, 0, 0),
                        "product-unpublished.json", hidden(product, 2),
                        "inquiry-upsert.json",
                                inquiry(
                                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                                        product,
                                        UUID.fromString("00000000-0000-0000-0000-000000000003"),
                                        0,
                                        null));
        for (var entry : examples.entrySet()) {
            java.nio.file.Files.writeString(
                    dir.resolve(entry.getKey()),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entry.getValue()));
        }
    }
}
