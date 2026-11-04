package com.msa.productquery.domain.projection.event;

import com.msa.productquery.domain.projection.exception.ProjectionErrorCode;
import com.msa.productquery.domain.projection.exception.ProjectionException;

/**
 * 영구적인 계약 오류입니다. 예외에 원문 JSON·문의 내용·연락처를 포함하지 않습니다. 기존 타입과 패키지는 Kafka 재시도 분류·DLT 예외 헤더 호환성을 위해
 * 유지합니다.
 */
public class InvalidProjectionEventException extends ProjectionException {
    public InvalidProjectionEventException() {
        super(ProjectionErrorCode.INVALID_EVENT);
    }
}
