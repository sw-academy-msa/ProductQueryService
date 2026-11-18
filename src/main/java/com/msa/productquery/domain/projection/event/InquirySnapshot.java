package com.msa.productquery.domain.projection.event;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

/** 본인 문의 조회 전용 데이터입니다. 공개 상품 응답이나 캐시와 섞지 않습니다. */
public record InquirySnapshot(
        @NotNull UUID productId,
        @NotNull UUID buyerId,
        @NotBlank @Size(max = 2000) String question,
        @Size(max = 8000) String answer,
        @NotNull Instant createdAt,
        Instant answeredAt) {
    @Override
    public String toString() {
        return "InquirySnapshot[REDACTED]";
    }
}
