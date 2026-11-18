package com.msa.productquery.domain.projection.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** RDS product_query 소유의 조회 모델입니다. 숨김도 행/버전을 남겨 오래된 공개 이벤트의 부활을 막습니다. */
@Entity
@Table(
        name = "products",
        schema = "product_query",
        indexes = {
            @Index(name = "idx_query_sale", columnList = "published,startsAt,endsAt"),
            @Index(name = "idx_query_category", columnList = "category")
        })
public class ProductProjection {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class ProductProjectionBuilder {
        @Override
        public String toString() {
            return "ProductProjectionBuilder[REDACTED]";
        }
    }

    @Id public UUID id;
    public long sourceVersion;
    public boolean published;

    @Column(length = 64)
    public String payloadHash;

    @Column(length = 200)
    public String name;

    @Column(length = 200)
    public String brandOrStore;

    @Column(length = 200)
    public String category;

    public long price;
    public int initialQuantity;
    public int reservedQuantity;
    public int confirmedQuantity;
    public int availableQuantity;
    public Instant startsAt;
    public Instant endsAt;
    public Instant publishedAt;
    public Instant sourceOccurredAt;
    public Instant projectedAt;

    // JSON 문자열은 JPA가 읽는 자체 스냅샷입니다. 조회 시 Command 엔티티를 읽지 않습니다.
    @Column(columnDefinition = "text")
    public String snapshot;

    protected ProductProjection() {}

    // 기존 생성자를 거쳐 초기 버전과 멱등 이력 상태를 보존하며 JPA 기본 생성자도 유지합니다.
    @Builder
    public ProductProjection(UUID id) {
        this.id = id;
        this.sourceVersion = -1;
    }
}
