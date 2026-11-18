package com.msa.productquery.domain.projection.repository;

import com.msa.productquery.domain.projection.entity.ProductProjection;

import org.springframework.data.jpa.repository.*;

import java.util.UUID;

/** 조회 모델에서만 검색 조건을 실행합니다. 원본 DB 연결과 서비스 간 SQL JOIN을 두지 않습니다. */
public interface ProductProjectionRepository
        extends JpaRepository<ProductProjection, UUID>,
                JpaSpecificationExecutor<ProductProjection> {
    @Query(
            "select distinct p.category from ProductProjection p where p.published = true order by p.category")
    java.util.List<String> findPublicCategories();

    @Query(
            value =
                    """
            select (starts_at at time zone 'Asia/Seoul')::date as "saleDate",
                   count(*) as "productCount", min(starts_at) as "firstStartsAt"
              from product_query.products
             where published = true and starts_at >= :from and starts_at < :to
             group by 1 order by 1
            """,
            nativeQuery = true)
    java.util.List<SaleDayProjection> schedule(java.time.Instant from, java.time.Instant to);
}
