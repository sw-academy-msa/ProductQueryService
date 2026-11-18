package com.msa.productquery.domain.projection.consumer;

import com.msa.productquery.domain.projection.service.ProjectionService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 바이트 원문은 로그에 출력하지 않습니다. 서비스의 DB 커밋이 끝나야 listener가 성공 반환합니다. */
@Component
@ConditionalOnProperty(name = "query.events.enabled", havingValue = "true")
public class ProjectionConsumer {
    private final ProjectionService service;

    public ProjectionConsumer(ProjectionService service) {
        this.service = service;
    }

    @KafkaListener(
            id = "product-query-projection",
            idIsGroup = false,
            topics = {"${query.events.product-topic}", "${query.events.inquiry-topic}"})
    public void consume(ConsumerRecord<String, byte[]> record) {
        service.apply(record.topic(), record.key(), record.value());
    }
}
