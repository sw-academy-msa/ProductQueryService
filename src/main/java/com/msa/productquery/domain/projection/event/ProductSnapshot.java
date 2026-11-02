package com.msa.productquery.domain.projection.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.*;

/** 승인된 상품의 공개 필드만 받는 v1 스냅샷입니다. 원본 엔티티·주소·계좌·심사 사유는 전달하지 않습니다. */
public record ProductSnapshot(
        @NotNull Approval status,
        @NotNull UUID sellerId,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 20000) String description,
        @Size(max = 200) String brandOrStore,
        @NotBlank @Size(max = 200) String category,
        @NotBlank @Size(max = 2000) String sourceUrl,
        @Positive long price,
        @PositiveOrZero long shippingFee,
        @Positive int dispatchBusinessDays,
        @Positive int perAccountLimit,
        @Size(max = 4000) String shippingNotice,
        @Size(max = 4000) String purchaseNotice,
        @Size(max = 4000) String disposalReason,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull Instant publishedAt,
        Instant soldOutAt,
        @NotNull List<@NotNull @Valid Attribute> attributes,
        @NotNull List<@NotNull @Valid Dimension> dimensions,
        @NotEmpty List<@NotNull @Valid Option> options,
        @NotEmpty @Size(max = 10) List<@NotNull @Valid Image> images,
        @NotNull List<@NotNull @Valid Faq> faqs) {
    public enum Approval {
        APPROVED
    }

    /** 상세 항목은 카테고리에 고정하지 않습니다. */
    public record Attribute(
            @NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 4000) String value) {}

    /** 옵션 정의와 순서를 보존하고 조합별 선택값과 구분합니다. */
    public record Dimension(
            @NotBlank @Size(max = 200) String name,
            @NotEmpty List<@NotBlank @Size(max = 200) String> values) {}

    /** 임시 확보를 판매 완료로 오인하지 않도록 세 수량을 분리합니다. */
    public record Option(
            @NotNull UUID id,
            @NotBlank @Size(max = 500) String label,
            @Positive int initialQuantity,
            @PositiveOrZero int reservedQuantity,
            @PositiveOrZero int confirmedQuantity,
            @NotNull Map<@NotBlank String, @NotBlank String> selections) {
        public int availableQuantity() {
            return initialQuantity - reservedQuantity - confirmedQuantity;
        }
    }

    /** 업로드 권한 URL을 받지 않고 서버 검증을 마친 최종 객체 키만 받습니다. */
    public record Image(
            @NotNull UUID id,
            @NotBlank String objectKey,
            @NotBlank String contentType,
            @Positive long sizeBytes,
            @PositiveOrZero int position,
            boolean representative) {}

    /** 판매자 확인 시각이 있는 FAQ만 조회 모델에 저장합니다. */
    public record Faq(
            @NotNull UUID id,
            @NotBlank @Size(max = 2000) String question,
            @NotBlank @Size(max = 8000) String answer,
            @NotNull Instant confirmedAt) {}
}
