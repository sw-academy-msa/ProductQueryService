package com.msa.productquery.domain.product.query.dto.response;

import com.msa.productquery.domain.projection.event.ProductSnapshot;

import java.time.*;
import java.util.*;

/** 구매자 화면 전용 응답입니다. 원본 엔티티·판매자 주소·다른 구매자의 문의 식별자를 노출하지 않습니다. */
public final class QueryResponses {
    private QueryResponses() {}

    public enum SaleStatus {
        UPCOMING,
        ON_SALE,
        SOLD_OUT,
        ENDED
    }

    /** 서버 시각은 카운터 표시 기준이고 metadata는 조회 반영 시점을 알려줍니다. */
    public record Page<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            Instant serverTime) {}

    public record Metadata(long version, Instant occurredAt, Instant projectedAt) {}

    /** 표시 재고이며 확보 보장이 아닙니다. 임시 확보를 결제 완료 수량에 더하지 않습니다. */
    public record Stock(
            int initialQuantity,
            int confirmedQuantity,
            int availableQuantity,
            boolean approximate) {}

    public record Image(
            UUID id,
            String objectKey,
            String url,
            String contentType,
            int position,
            boolean representative) {}

    public record Option(
            UUID id, String label, Map<String, String> selections, int availableQuantity) {}

    public record Card(
            UUID id,
            String name,
            String brandOrStore,
            String category,
            long price,
            Image image,
            SaleStatus status,
            Instant startsAt,
            Instant endsAt,
            Instant soldOutAt,
            Stock stock,
            Metadata metadata) {}

    public record Detail(
            UUID id,
            UUID sellerId,
            String name,
            String description,
            String brandOrStore,
            String category,
            String sourceUrl,
            long price,
            long shippingFee,
            int dispatchBusinessDays,
            int perAccountLimit,
            String shippingNotice,
            String purchaseNotice,
            String disposalReason,
            List<ProductSnapshot.Attribute> attributes,
            List<ProductSnapshot.Dimension> dimensions,
            List<Option> options,
            List<Image> images,
            List<ProductSnapshot.Faq> faqs,
            SaleStatus status,
            Instant startsAt,
            Instant endsAt,
            Instant soldOutAt,
            Stock stock,
            Metadata metadata,
            boolean canAttemptPurchase,
            Instant serverTime) {}

    public record Inquiry(
            UUID id,
            String question,
            String answer,
            Instant createdAt,
            Instant answeredAt,
            Instant projectedAt) {}

    public record SaleDay(LocalDate date, long productCount, Instant firstStartsAt) {}

    public record Schedule(
            LocalDate from, LocalDate to, String zone, List<SaleDay> days, Instant serverTime) {}
}
