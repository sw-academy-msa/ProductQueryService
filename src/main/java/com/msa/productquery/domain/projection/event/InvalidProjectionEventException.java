package com.msa.productquery.domain.projection.event;

/** 영구적인 계약 오류입니다. 예외에 원문 JSON·문의 내용·연락처를 포함하지 않습니다. */
public class InvalidProjectionEventException extends RuntimeException {
    public InvalidProjectionEventException() {
        super("조회 이벤트 계약이 올바르지 않습니다.");
    }
}
