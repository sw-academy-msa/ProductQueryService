package com.msa.productquery.domain.projection;

import com.msa.productquery.domain.projection.event.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** 합성 상품 이벤트입니다. 실제 계정·토큰·개인정보·원본 DB를 사용하지 않습니다. */
public final class EventFixtures {
    public static final String PRODUCT_TOPIC = "stockdrop.product.public.v1";
    public static final String INQUIRY_TOPIC = "stockdrop.product.inquiries.v1";
    public static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private EventFixtures() {}

    public static UUID id(UUID product, String suffix) {
        return UUID.nameUUIDFromBytes((product + suffix).getBytes(StandardCharsets.UTF_8));
    }

    public static ProjectionEvent product(
            UUID product,
            long version,
            String name,
            Instant startsAt,
            int reserved,
            int confirmed) {
        var imageId = id(product, "image");
        var snapshot =
                new ProductSnapshot(
                        ProductSnapshot.Approval.APPROVED,
                        id(product, "seller"),
                        name,
                        "합성 상품 설명",
                        "테스트 판매처",
                        "문구",
                        "https://example.com/item",
                        1000,
                        3000,
                        2,
                        3,
                        "배송 안내",
                        "구매 안내",
                        "남은 새 상품",
                        startsAt,
                        startsAt.plus(Duration.ofHours(3)),
                        startsAt.minusSeconds(3600),
                        confirmed == 10 ? startsAt.plusSeconds(1) : null,
                        List.of(new ProductSnapshot.Attribute("규격", "A5")),
                        List.of(),
                        List.of(
                                new ProductSnapshot.Option(
                                        id(product, "option"),
                                        "기본",
                                        10,
                                        reserved,
                                        confirmed,
                                        Map.of())),
                        List.of(
                                new ProductSnapshot.Image(
                                        imageId,
                                        "product-images/" + product + "/" + imageId,
                                        "image/png",
                                        68,
                                        0,
                                        true)),
                        List.of(
                                new ProductSnapshot.Faq(
                                        id(product, "faq"),
                                        "규격은?",
                                        "A5입니다.",
                                        NOW.minusSeconds(3600))));
        return new ProjectionEvent(
                UUID.randomUUID(),
                1,
                ProjectionEvent.Type.PRODUCT_UPSERT,
                product,
                version,
                NOW,
                snapshot,
                null);
    }

    public static ProjectionEvent hidden(UUID product, long version) {
        return new ProjectionEvent(
                UUID.randomUUID(),
                1,
                ProjectionEvent.Type.PRODUCT_UNPUBLISHED,
                product,
                version,
                NOW,
                null,
                null);
    }

    public static ProjectionEvent inquiry(
            UUID inquiry, UUID product, UUID buyer, long version, String answer) {
        var snapshot =
                new InquirySnapshot(
                        product,
                        buyer,
                        "합성 상품 문의",
                        answer,
                        NOW.minusSeconds(60),
                        answer == null ? null : NOW);
        return new ProjectionEvent(
                UUID.randomUUID(),
                1,
                ProjectionEvent.Type.INQUIRY_UPSERT,
                inquiry,
                version,
                NOW,
                null,
                snapshot);
    }
}
