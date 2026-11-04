package com.msa.productquery.domain.projection.exception;

import com.msa.productquery.global.error.BusinessException;

/** 조회 모델 반영의 업무 예외 기반 타입입니다. 이 기반 타입 전체를 Kafka 재시도 제외 대상으로 등록하지 않습니다. */
public class ProjectionException extends BusinessException {
    public ProjectionException(ProjectionErrorCode errorCode) {
        super(errorCode);
    }
}
