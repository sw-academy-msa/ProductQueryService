package com.msa.productquery.global.config.kafka;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 로컬에서 Kafka를 끈 상태에서도 조회 서비스와 이벤트 계약을 구성할 수 있게 합니다. */
@Configuration
@EnableConfigurationProperties(ProjectionProperties.class)
public class ProjectionConfig {}
