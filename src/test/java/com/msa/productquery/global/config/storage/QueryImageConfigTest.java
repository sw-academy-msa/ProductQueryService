package com.msa.productquery.global.config.storage;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 이미지 기능의 활성 여부에 맞춰 시작 시 필수 설정만 검증합니다. */
class QueryImageConfigTest {
    @Test
    void enabledImagesRequireBucketAndLocalEndpoint() {
        var config = new QueryImageConfig();
        for (String bucket : new String[] {null, "", " "}) {
            assertThatThrownBy(
                            () ->
                                    config.queryImagePresigner(
                                            new QueryImageProperties(
                                                    true,
                                                    true,
                                                    bucket,
                                                    null,
                                                    "http://localhost:9092")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("이미지 버킷 설정이 필요합니다.");
        }
        for (String endpoint : new String[] {null, "", " "}) {
            assertThatThrownBy(
                            () ->
                                    config.queryImagePresigner(
                                            new QueryImageProperties(
                                                    true, true, "test-bucket", null, endpoint)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("로컬 이미지 주소가 필요합니다.");
        }
    }

    @Test
    void disabledLocalImagesDoNotRequireBucketOrEndpoint() {
        try (var presigner =
                new QueryImageConfig()
                        .queryImagePresigner(
                                new QueryImageProperties(false, true, null, null, null))) {
            assertThat(presigner).isNotNull();
        }
    }
}
