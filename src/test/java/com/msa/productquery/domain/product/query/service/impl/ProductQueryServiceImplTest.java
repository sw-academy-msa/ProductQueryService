package com.msa.productquery.domain.product.query.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.msa.productquery.domain.product.exception.ProductQueryErrorCode;
import com.msa.productquery.domain.product.exception.ProductQueryException;
import com.msa.productquery.domain.product.query.dto.request.ProductSearchRequest;
import com.msa.productquery.domain.product.query.service.ImageUrlService;
import com.msa.productquery.domain.projection.entity.ProductProjection;
import com.msa.productquery.domain.projection.repository.InquiryProjectionRepository;
import com.msa.productquery.domain.projection.repository.ProductProjectionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/** 조회 서비스가 요청 검증·공개 여부 실패를 도메인 예외로 구분하고 DB 접근 순서를 유지하는지 검증합니다. */
class ProductQueryServiceImplTest {
    private final ProductProjectionRepository products = mock(ProductProjectionRepository.class);
    private final InquiryProjectionRepository inquiries = mock(InquiryProjectionRepository.class);
    private final Instant now = Instant.parse("2030-01-01T00:00:00Z");
    private final ProductQueryServiceImpl service =
            new ProductQueryServiceImpl(
                    products,
                    inquiries,
                    mock(ImageUrlService.class),
                    mock(ObjectMapper.class),
                    Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void invalidPagingIsRejectedBeforeDatabaseAccess() {
        var request = new ProductSearchRequest(null, null, null, null, -1, 20, null, null);
        assertThatThrownBy(() -> service.list(request))
                .isInstanceOfSatisfying(
                        ProductQueryException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ProductQueryErrorCode.INVALID_REQUEST));
        verifyNoInteractions(products, inquiries);
    }

    /** 곱셈 오버플로 없이 JPA 최대 오프셋 초과를 DB 접근 전에 거절합니다. */
    @Test
    void oversizedOffsetIsRejectedBeforeDatabaseAccess() {
        var request =
                new ProductSearchRequest(
                        null, null, null, null, Integer.MAX_VALUE, 100, null, null);
        assertThatThrownBy(() -> service.list(request))
                .isInstanceOfSatisfying(
                        ProductQueryException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ProductQueryErrorCode.INVALID_REQUEST));
        verifyNoInteractions(products, inquiries);
    }

    @Test
    void invalidDateRangeIsRejectedBeforeDatabaseAccess() {
        var request = new ProductSearchRequest(null, null, null, null, 0, 20, now, now);
        assertThatThrownBy(() -> service.list(request))
                .isInstanceOfSatisfying(
                        ProductQueryException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ProductQueryErrorCode.INVALID_REQUEST));
        verifyNoInteractions(products, inquiries);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingAndUnpublishedProductsUseSameNotFoundContract(boolean exists) {
        var id = UUID.randomUUID();
        var hidden = ProductProjection.builder().id(id).build();
        when(products.findById(id)).thenReturn(exists ? Optional.of(hidden) : Optional.empty());
        assertThatThrownBy(() -> service.detail(id))
                .isInstanceOfSatisfying(
                        ProductQueryException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ProductQueryErrorCode.NOT_FOUND));
    }
}
