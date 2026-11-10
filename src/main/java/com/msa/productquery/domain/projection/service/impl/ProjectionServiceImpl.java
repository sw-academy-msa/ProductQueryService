package com.msa.productquery.domain.projection.service.impl;

import com.msa.productquery.domain.projection.entity.*;
import com.msa.productquery.domain.projection.event.*;
import com.msa.productquery.domain.projection.repository.*;
import com.msa.productquery.domain.projection.service.ProjectionService;
import com.msa.productquery.global.config.kafka.ProjectionProperties;

import jakarta.validation.Validator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

/** 조회 버전과 eventId를 같은 로컬 트랜잭션으로 반영합니다. 원본 DB와 분산 트랜잭션을 만들지 않습니다. */
@Service
public class ProjectionServiceImpl implements ProjectionService {
    // Command 상품 Outbox의 UTF-8 3 MiB 상한과 일치시켜 발행 성공 후 조회 반영이 거절되지 않게 합니다.
    static final int MAX_PRODUCT_EVENT_BYTES = 3 * 1024 * 1024;
    // 문의와 비공개 전환은 기존 2 MiB 계약을 유지합니다.
    static final int MAX_OTHER_EVENT_BYTES = 2 * 1024 * 1024;
    private final ProductProjectionRepository products;
    private final InquiryProjectionRepository inquiries;
    private final ProcessedEventRepository processed;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final Clock clock;
    private final ProjectionProperties properties;

    public ProjectionServiceImpl(
            ProductProjectionRepository products,
            InquiryProjectionRepository inquiries,
            ProcessedEventRepository processed,
            ObjectMapper mapper,
            Validator validator,
            Clock clock,
            ProjectionProperties properties) {
        this.products = products;
        this.inquiries = inquiries;
        this.processed = processed;
        this.mapper = mapper;
        this.validator = validator;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    @Transactional(timeout = 10)
    public void apply(String topic, String partitionKey, byte[] value) {
        ProjectionEvent event;
        try {
            if (value == null || value.length > MAX_PRODUCT_EVENT_BYTES) {
                throw new InvalidProjectionEventException();
            }
            event =
                    mapper.readerFor(ProjectionEvent.class)
                            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .readValue(value);
        } catch (RuntimeException exception) {
            // JSON 예외의 원문 조각이 프레임워크 로그/DLT 헤더로 새지 않도록 원인을 포함하지 않습니다.
            throw new InvalidProjectionEventException();
        }
        if (event == null || !validator.validate(event).isEmpty()) {
            throw new InvalidProjectionEventException();
        }
        if (event.type() != ProjectionEvent.Type.PRODUCT_UPSERT
                && value.length > MAX_OTHER_EVENT_BYTES) {
            throw new InvalidProjectionEventException();
        }
        boolean inquiry = event.type() == ProjectionEvent.Type.INQUIRY_UPSERT;
        if (inquiry) {
            if (!topic.equals(properties.inquiryTopic())
                    || event.inquiry() == null
                    || event.product() != null
                    || !event.inquiry().productId().toString().equals(partitionKey)) {
                throw new InvalidProjectionEventException();
            }
        } else if (!topic.equals(properties.productTopic())
                || event.inquiry() != null
                || !event.aggregateId().toString().equals(partitionKey)
                || (event.type() == ProjectionEvent.Type.PRODUCT_UPSERT)
                        != (event.product() != null)) {
            throw new InvalidProjectionEventException();
        }
        if (event.product() != null) {
            validateProduct(event.aggregateId(), event.product());
        }
        if (event.inquiry() != null
                && ((event.inquiry().answer() == null) != (event.inquiry().answeredAt() == null)
                        || (event.inquiry().answer() != null
                                && event.inquiry().answer().isBlank()))) {
            throw new InvalidProjectionEventException();
        }

        // 없는 행의 최초 INSERT도 복제본 간 직렬화합니다. 해시 충돌은 추가 대기만 만들며 잘못된 갱신을 허용하지 않습니다.
        String lockKey = (inquiry ? "inquiry:" : "product:") + event.aggregateId();
        processed.lockAggregate(lockKey);
        String eventHash = hash(json(event));
        var receipt = processed.findById(event.eventId());
        if (receipt.isPresent()) {
            if (!receipt.get().eventHash.equals(eventHash)) {
                throw new InvalidProjectionEventException();
            }
            return;
        }
        String payloadHash =
                hash(event.type().name() + ":" + json(inquiry ? event.inquiry() : event.product()));
        if (inquiry) {
            applyInquiry(event, payloadHash);
        } else {
            applyProduct(event, payloadHash);
        }
        processed.saveAndFlush(
                ProcessedEvent.builder()
                        .eventId(event.eventId())
                        .aggregateId(event.aggregateId())
                        .aggregateVersion(event.aggregateVersion())
                        .hash(eventHash)
                        .now(clock.instant())
                        .build());
        // 서비스 트랜잭션 커밋 후 listener가 반환되어 Kafka offset을 확인 처리합니다.
    }

    private void applyProduct(ProjectionEvent e, String hash) {
        var row =
                products.findById(e.aggregateId())
                        .orElseGet(() -> ProductProjection.builder().id(e.aggregateId()).build());
        if (!newer(e.aggregateVersion(), row.sourceVersion, hash, row.payloadHash)) {
            return;
        }
        row.sourceVersion = e.aggregateVersion();
        row.payloadHash = hash;
        row.projectedAt = clock.instant();
        row.sourceOccurredAt = e.occurredAt();
        row.published = e.type() == ProjectionEvent.Type.PRODUCT_UPSERT;
        row.snapshot = row.published ? json(e.product()) : null;
        if (row.published) {
            var p = e.product();
            row.name = p.name();
            row.brandOrStore = p.brandOrStore();
            row.category = p.category();
            row.price = p.price();
            row.startsAt = p.startsAt();
            row.endsAt = p.endsAt();
            row.publishedAt = p.publishedAt();
            row.initialQuantity =
                    (int)
                            p.options().stream()
                                    .mapToLong(ProductSnapshot.Option::initialQuantity)
                                    .sum();
            row.reservedQuantity =
                    (int)
                            p.options().stream()
                                    .mapToLong(ProductSnapshot.Option::reservedQuantity)
                                    .sum();
            row.confirmedQuantity =
                    (int)
                            p.options().stream()
                                    .mapToLong(ProductSnapshot.Option::confirmedQuantity)
                                    .sum();
            row.availableQuantity =
                    row.initialQuantity - row.reservedQuantity - row.confirmedQuantity;
        }
        products.save(row);
    }

    private void applyInquiry(ProjectionEvent e, String hash) {
        var row =
                inquiries
                        .findById(e.aggregateId())
                        .orElseGet(() -> InquiryProjection.builder().id(e.aggregateId()).build());
        if (!newer(e.aggregateVersion(), row.sourceVersion, hash, row.payloadHash)) {
            return;
        }
        if (row.productId != null
                && (!row.productId.equals(e.inquiry().productId())
                        || !row.buyerId.equals(e.inquiry().buyerId()))) {
            throw new InvalidProjectionEventException();
        }
        var i = e.inquiry();
        row.productId = i.productId();
        row.buyerId = i.buyerId();
        row.sourceVersion = e.aggregateVersion();
        row.payloadHash = hash;
        row.question = i.question();
        row.answer = i.answer();
        row.createdAt = i.createdAt();
        row.answeredAt = i.answeredAt();
        row.projectedAt = clock.instant();
        inquiries.save(row);
    }

    private boolean newer(long incoming, long current, String hash, String oldHash) {
        if (incoming == current && !hash.equals(oldHash)) {
            throw new InvalidProjectionEventException();
        }
        return incoming > current;
    }

    private void validateProduct(UUID productId, ProductSnapshot p) {
        try {
            var uri = java.net.URI.create(p.sourceUrl());
            if (!Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new InvalidProjectionEventException();
            }
        } catch (RuntimeException exception) {
            throw new InvalidProjectionEventException();
        }
        boolean invalid =
                !p.endsAt().isAfter(p.startsAt())
                        || p.options().stream()
                                        .mapToLong(ProductSnapshot.Option::initialQuantity)
                                        .sum()
                                > Integer.MAX_VALUE
                        || p.options().stream()
                                .anyMatch(
                                        o ->
                                                (long) o.reservedQuantity() + o.confirmedQuantity()
                                                        > o.initialQuantity())
                        || p.options().stream().map(ProductSnapshot.Option::id).distinct().count()
                                != p.options().size()
                        || p.images().stream().map(ProductSnapshot.Image::id).distinct().count()
                                != p.images().size()
                        || p.images().stream()
                                        .map(ProductSnapshot.Image::position)
                                        .distinct()
                                        .count()
                                != p.images().size()
                        || p.images().stream().filter(ProductSnapshot.Image::representative).count()
                                != 1
                        || p.images().stream()
                                .anyMatch(
                                        i ->
                                                i.position() >= p.images().size()
                                                        || !i.objectKey()
                                                                .equals(
                                                                        "product-images/"
                                                                                + productId
                                                                                + "/"
                                                                                + i.id())
                                                        || !Set.of(
                                                                        "image/jpeg",
                                                                        "image/png",
                                                                        "image/webp")
                                                                .contains(i.contentType()))
                        || p.faqs().stream().map(ProductSnapshot.Faq::id).distinct().count()
                                != p.faqs().size();
        if (invalid) {
            throw new InvalidProjectionEventException();
        }
    }

    private String json(Object value) {
        return mapper.writer()
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(value);
    }

    private String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.");
        }
    }
}
