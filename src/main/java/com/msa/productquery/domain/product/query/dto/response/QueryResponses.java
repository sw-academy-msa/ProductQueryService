package com.msa.productquery.domain.product.query.dto.response;

import lombok.Builder;

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
    @Builder
    public record Page<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            Instant serverTime) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class PageBuilder<T> {
            @Override
            public String toString() {
                return "PageBuilder[REDACTED]";
            }
        }
    }

    @Builder
    public record Metadata(long version, Instant occurredAt, Instant projectedAt) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class MetadataBuilder {
            @Override
            public String toString() {
                return "MetadataBuilder[REDACTED]";
            }
        }
    }

    /** 표시 재고이며 확보 보장이 아닙니다. 임시 확보를 결제 완료 수량에 더하지 않습니다. */
    @Builder
    public record Stock(
            int initialQuantity,
            int confirmedQuantity,
            int availableQuantity,
            boolean approximate) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class StockBuilder {
            @Override
            public String toString() {
                return "StockBuilder[REDACTED]";
            }
        }
    }

    @Builder
    public record Image(
            UUID id,
            String objectKey,
            String url,
            String contentType,
            int position,
            boolean representative) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class ImageBuilder {
            @Override
            public String toString() {
                return "ImageBuilder[REDACTED]";
            }
        }
    }

    @Builder
    public record Option(
            UUID id, String label, Map<String, String> selections, int availableQuantity) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class OptionBuilder {
            @Override
            public String toString() {
                return "OptionBuilder[REDACTED]";
            }
        }
    }

    @Builder
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
            Metadata metadata) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class CardBuilder {
            @Override
            public String toString() {
                return "CardBuilder[REDACTED]";
            }
        }
    }

    /** HTTP 전용 상세 항목이며 Kafka 이벤트 스키마와 독립적으로 관리합니다. */
    @Builder
    public record Attribute(String name, String value) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class AttributeBuilder {
            @Override
            public String toString() {
                return "AttributeBuilder[REDACTED]";
            }
        }
    }

    /** 이벤트의 가변 목록을 복사하여 응답 객체와 분리합니다. */
    @Builder
    public record Dimension(String name, List<String> values) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class DimensionBuilder {
            @Override
            public String toString() {
                return "DimensionBuilder[REDACTED]";
            }
        }

        public Dimension {
            values = List.copyOf(values);
        }
    }

    /** 공개가 확인된 FAQ의 HTTP 응답이며 이벤트 DTO를 직접 노출하지 않습니다. */
    @Builder
    public record Faq(UUID id, String question, String answer, Instant confirmedAt) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class FaqBuilder {
            @Override
            public String toString() {
                return "FaqBuilder[REDACTED]";
            }
        }
    }

    /** 카테고리 배열에 이름을 부여하여 응답 계약을 명시합니다. */
    @Builder
    public record Categories(List<String> categories) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class CategoriesBuilder {
            @Override
            public String toString() {
                return "CategoriesBuilder[REDACTED]";
            }
        }

        public Categories {
            categories = List.copyOf(categories);
        }
    }

    @Builder
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
            List<Attribute> attributes,
            List<Dimension> dimensions,
            List<Option> options,
            List<Image> images,
            List<Faq> faqs,
            SaleStatus status,
            Instant startsAt,
            Instant endsAt,
            Instant soldOutAt,
            Stock stock,
            Metadata metadata,
            boolean canAttemptPurchase,
            Instant serverTime) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class DetailBuilder {
            @Override
            public String toString() {
                return "DetailBuilder[REDACTED]";
            }
        }
    }

    @Builder
    public record Inquiry(
            UUID id,
            String question,
            String answer,
            Instant createdAt,
            Instant answeredAt,
            Instant projectedAt) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class InquiryBuilder {
            @Override
            public String toString() {
                return "InquiryBuilder[REDACTED]";
            }
        }
    }

    @Builder
    public record SaleDay(LocalDate date, long productCount, Instant firstStartsAt) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class SaleDayBuilder {
            @Override
            public String toString() {
                return "SaleDayBuilder[REDACTED]";
            }
        }
    }

    @Builder
    public record Schedule(
            LocalDate from, LocalDate to, String zone, List<SaleDay> days, Instant serverTime) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class ScheduleBuilder {
            @Override
            public String toString() {
                return "ScheduleBuilder[REDACTED]";
            }
        }
    }
}
