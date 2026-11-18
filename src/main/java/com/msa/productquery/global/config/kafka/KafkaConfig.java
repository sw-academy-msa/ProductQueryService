package com.msa.productquery.global.config.kafka;

import com.msa.productquery.domain.projection.event.InvalidProjectionEventException;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

/** 영구 계약 오류는 DLT로 격리하고 DB 장애는 계속 재시도해 복구 전 offset을 넘기지 않습니다. */
@Configuration
@ConditionalOnProperty(name = "query.events.enabled", havingValue = "true")
public class KafkaConfig {
    @Bean
    DefaultErrorHandler projectionErrorHandler(KafkaTemplate<String, byte[]> template) {
        var recoverer =
                new DeadLetterPublishingRecoverer(
                        template,
                        (record, exception) ->
                                new TopicPartition(record.topic() + ".DLT", record.partition()));
        // DLT 발행 실패를 성공으로 취급하지 않아 원본 offset이 유실되지 않게 합니다.
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(10));
        var handler =
                new DefaultErrorHandler(
                        recoverer, new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(InvalidProjectionEventException.class);
        return handler;
    }
}
