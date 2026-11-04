package com.msa.productquery.domain.projection.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

/** 빌더가 최초 버전 이벤트 수용과 처리 이력의 INSERT 판정을 바꾸지 않는지 검증합니다. */
class ProjectionBuildersTest {
    @Test
    void projectionBuildersPreserveInitialVersionBeforeFirstEvent() {
        var productId = UUID.randomUUID();
        var inquiryId = UUID.randomUUID();
        var product = ProductProjection.builder().id(productId).build();
        var inquiry = InquiryProjection.builder().id(inquiryId).build();

        assertThat(product.id).isEqualTo(productId);
        assertThat(inquiry.id).isEqualTo(inquiryId);
        assertThat(product.sourceVersion).isEqualTo(-1);
        assertThat(inquiry.sourceVersion).isEqualTo(-1);
        assertThat(product.published).isFalse();
        assertThat(product.snapshot).isNull();
    }

    @Test
    void processedEventBuilderPreservesAssignedIdAndNewEntityVersion() {
        var eventId = UUID.randomUUID();
        var aggregateId = UUID.randomUUID();
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var event =
                ProcessedEvent.builder()
                        .eventId(eventId)
                        .aggregateId(aggregateId)
                        .aggregateVersion(0)
                        .hash("fixture-hash")
                        .now(now)
                        .build();

        assertThat(event.eventId).isEqualTo(eventId);
        assertThat(event.aggregateId).isEqualTo(aggregateId);
        assertThat(event.aggregateVersion).isZero();
        assertThat(event.eventHash).isEqualTo("fixture-hash");
        assertThat(event.processedAt).isEqualTo(now);
        assertThat(event.lockVersion).isNull();
    }
}
