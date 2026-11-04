package com.msa.productquery.domain.product.exception;

import com.msa.productquery.global.error.CommonErrorCode;
import com.msa.productquery.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 상품 조회 업무 오류입니다. 기존 COMMON_* 코드·상태·메시지를 재사용해 공개 계약을 유지합니다. */
public enum ProductQueryErrorCode implements ErrorCode {
    INVALID_REQUEST(CommonErrorCode.INVALID_REQUEST),
    NOT_FOUND(CommonErrorCode.RESOURCE_NOT_FOUND);

    private final CommonErrorCode responseCode;

    ProductQueryErrorCode(CommonErrorCode responseCode) {
        this.responseCode = responseCode;
    }

    @Override
    public HttpStatus status() {
        return responseCode.status();
    }

    @Override
    public String code() {
        return responseCode.code();
    }

    @Override
    public String message() {
        return responseCode.message();
    }
}
