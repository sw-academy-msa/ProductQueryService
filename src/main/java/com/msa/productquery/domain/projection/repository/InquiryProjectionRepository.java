package com.msa.productquery.domain.projection.repository;

import com.msa.productquery.domain.projection.entity.InquiryProjection;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 구매자 ID를 DB 조건에 포함해 다른 계정의 문의를 목록에 섞지 않습니다. */
public interface InquiryProjectionRepository extends JpaRepository<InquiryProjection, UUID> {
    Page<InquiryProjection> findByProductIdAndBuyerId(UUID productId, UUID buyerId, Pageable page);
}
