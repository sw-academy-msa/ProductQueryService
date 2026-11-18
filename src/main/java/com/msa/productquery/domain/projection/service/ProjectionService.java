package com.msa.productquery.domain.projection.service;

/** Kafka 이벤트를 자신의 조회 DB에 멱등 반영합니다. 외부 HTTP용 변경 API가 아닙니다. */
public interface ProjectionService {
    void apply(String topic, String partitionKey, byte[] value);
}
