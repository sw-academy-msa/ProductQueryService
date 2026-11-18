package com.msa.productquery.domain.projection.exception;

import com.msa.productquery.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 조회 모델 반영의 영구적인 이벤트 계약 오류입니다. DB 장애와 시스템 오류의 재시도 분류와 구분합니다. */
public enum ProjectionErrorCode implements ErrorCode {
    INVALID_EVENT(HttpStatus.BAD_REQUEST, "PROJECTION_INVALID_EVENT", "조회 이벤트 계약이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ProjectionErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
