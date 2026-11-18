package com.msa.productquery.domain.product.query.service.impl;

import com.msa.productquery.domain.product.query.service.ImageUrlService;
import com.msa.productquery.global.config.storage.QueryImageProperties;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

/** 권한 URL은 DB나 공유 조회 캐시에 저장하지 않습니다. 브라우저 표시 직전에 5분 읽기 권한만 만듭니다. */
@Service
public class ImageUrlServiceImpl implements ImageUrlService {
    private final QueryImageProperties properties;
    private final S3Presigner presigner;

    public ImageUrlServiceImpl(QueryImageProperties properties, S3Presigner presigner) {
        this.properties = properties;
        this.presigner = presigner;
    }

    public String resolve(String key) {
        if (!properties.enabled()) {
            return null;
        }
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            throw new IllegalStateException("이미지 버킷 설정이 필요합니다.");
        }
        var get = GetObjectRequest.builder().bucket(properties.bucket()).key(key).build();
        return presigner
                .presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(5))
                                .getObjectRequest(get)
                                .build())
                .url()
                .toExternalForm();
    }
}
