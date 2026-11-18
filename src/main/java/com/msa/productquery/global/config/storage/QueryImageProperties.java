package com.msa.productquery.global.config.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 이미지를 비활성화하면 AWS 자격증명 없이 실행되며 URL은 null로 반환합니다. */
@ConfigurationProperties("query.images")
public record QueryImageProperties(
        boolean enabled, boolean local, String bucket, String region, String endpoint) {}
