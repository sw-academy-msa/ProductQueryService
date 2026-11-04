package com.msa.productquery.domain.product.exception;

import com.msa.productquery.global.error.BusinessException;

/** 조회 업무 실패를 공통 HTTP 오류 응답으로 전달하며 상품명·검색어·문의 원문을 포함하지 않습니다. */
public class ProductQueryException extends BusinessException {
    public ProductQueryException(ProductQueryErrorCode errorCode) {
        super(errorCode);
    }
}
