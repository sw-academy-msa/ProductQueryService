package com.msa.productquery.domain.projection.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

/** 엔티티별 단조 증가 조회 버전을 요구합니다. Command JPA 버전만 재고 버전으로 사용하면 안 됩니다. */
public record ProjectionEvent(
        @NotNull UUID eventId,
        @Min(1) @Max(1) int schemaVersion,
        @NotNull Type type,
        @NotNull UUID aggregateId,
        @PositiveOrZero long aggregateVersion,
        @NotNull Instant occurredAt,
        @Valid ProductSnapshot product,
        @Valid InquirySnapshot inquiry) {
    public enum Type {
        PRODUCT_UPSERT,
        PRODUCT_UNPUBLISHED,
        INQUIRY_UPSERT
    }

    @Override
    public String toString() {
        return "ProjectionEvent[eventId=" + eventId + ",type=" + type + "]";
    }
}
