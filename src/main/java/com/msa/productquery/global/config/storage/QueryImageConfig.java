package com.msa.productquery.global.config.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/** 운영은 EC2 IAM 역할, 로컬은 기존 Product S3Mock의 더미 자격증명을 사용합니다. */
@Configuration
@EnableConfigurationProperties(QueryImageProperties.class)
public class QueryImageConfig {
    @Bean
    S3Presigner queryImagePresigner(QueryImageProperties properties) {
        // 활성화된 이미지 설정 오류는 요청 시점이 아닌 시작 시점에 발견합니다.
        if (properties.enabled()
                && (properties.bucket() == null || properties.bucket().isBlank())) {
            throw new IllegalStateException("이미지 버킷 설정이 필요합니다.");
        }
        if (properties.enabled()
                && properties.local()
                && (properties.endpoint() == null || properties.endpoint().isBlank())) {
            throw new IllegalStateException("로컬 이미지 주소가 필요합니다.");
        }
        AwsCredentialsProvider credentials =
                properties.local()
                        ? StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("local", "local"))
                        : DefaultCredentialsProvider.builder().build();
        var builder =
                S3Presigner.builder()
                        .credentialsProvider(credentials)
                        .region(
                                Region.of(
                                        properties.region() == null
                                                ? "ap-northeast-2"
                                                : properties.region()))
                        .serviceConfiguration(
                                S3Configuration.builder()
                                        .pathStyleAccessEnabled(properties.local())
                                        .build());
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }
}
