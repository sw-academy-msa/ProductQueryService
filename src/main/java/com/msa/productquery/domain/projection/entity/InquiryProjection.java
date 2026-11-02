package com.msa.productquery.domain.projection.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** 문의와 상품 이벤트의 도착 순서가 달라도 적재할 수 있도록 Query 내부 상품 FK를 강제하지 않습니다. */
@Entity
@Table(
        name = "inquiries",
        schema = "product_query",
        indexes = @Index(name = "idx_query_inquiry_owner", columnList = "productId,buyerId"))
public class InquiryProjection {
    @Id
    public UUID id;
    public UUID productId;
    public UUID buyerId;
    public long sourceVersion;

    @Column(length = 64)
    public String payloadHash;

    @Column(length = 2000)
    public String question;

    @Column(length = 8000)
    public String answer;

    public Instant createdAt;
    public Instant answeredAt;
    public Instant projectedAt;

    protected InquiryProjection() {}

    public InquiryProjection(UUID id) {
        this.id = id;
        this.sourceVersion = -1;
    }
}
