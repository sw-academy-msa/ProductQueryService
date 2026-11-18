package com.msa.productquery.domain.projection.repository;

import java.time.*;

/** 일정 달력 집계의 내부 반환 타입이며 HTTP 응답은 아닙니다. */
public interface SaleDayProjection {
    LocalDate getSaleDate();

    long getProductCount();

    Instant getFirstStartsAt();
}
