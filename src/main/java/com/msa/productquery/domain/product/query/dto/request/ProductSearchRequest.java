package com.msa.productquery.domain.product.query.dto.request;

import com.msa.productquery.domain.product.query.dto.response.QueryResponses.SaleStatus;

import jakarta.validation.constraints.*;

import java.time.Instant;

/** Figma의 상품명·카테고리·판매처 검색과 판매 상태·마감순 정렬을 조회 DB에서 처리합니다. */
public record ProductSearchRequest(
        @Size(max = 100) String keyword,
        @Size(max = 200) String category,
        SaleStatus status,
        Sort sort,
        @PositiveOrZero Integer page,
        @Min(1) @Max(100) Integer size,
        Instant startsFrom,
        Instant startsTo) {
    public enum Sort {
        NEWEST,
        ENDING_SOON,
        PRICE_ASC,
        PRICE_DESC
    }

    public ProductSearchRequest {
        sort = sort == null ? Sort.NEWEST : sort;
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
