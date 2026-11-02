package com.msa.productquery.domain.projection.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** 조회 모델 갱신과 같은 DB 트랜잭션으로 저장하는 멱등 이력입니다. Kafka 소비 완료와 Outbox 발행 완료는 다릅니다. */
@Entity
@Table(name = "processed_events", schema = "product_query")
public class ProcessedEvent {
    @Id
    public UUID eventId;
    // 할당된 UUID도 최초 저장은 INSERT여야 하며 동시 재사용 ID로 기존 이력을 merge해서는 안 됩니다.
    @Version
    public Long lockVersion;
    public UUID aggregateId;
    public long aggregateVersion;

    @Column(nullable = false, length = 64)
    public String eventHash;

    public Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(
            UUID eventId, UUID aggregateId, long aggregateVersion, String hash, Instant now) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.eventHash = hash;
        this.processedAt = now;
    }
}
