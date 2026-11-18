package com.msa.productquery.domain.product.query.service;

/** Query는 이미지 읽기 권한만 발급합니다. 업로드·삭제·객체 연결은 Command 책임입니다. */
public interface ImageUrlService {
    String resolve(String objectKey);
}
