package com.msa.productquery.global.config.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 공개 상품 토픽과 본인 문의 토픽을 구분합니다. 복제본은 같은 소비 그룹을 사용합니다. */
@ConfigurationProperties("query.events")
public record ProjectionProperties(boolean enabled, String productTopic, String inquiryTopic) {}
