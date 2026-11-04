package com.msa.productquery.domain.projection.repository;

import com.msa.productquery.domain.projection.entity.ProcessedEvent;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.*;

import java.util.UUID;

/** 이벤트 중복 이력과 조회 모델 반영의 직렬화 잠금을 담당합니다. 원본 DB에는 접근하지 않습니다. */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    /** 최초 INSERT 전에도 같은 집계의 반영을 직렬화하며 잠금은 조회 모델·이력 트랜잭션 종료까지 유지합니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
            value =
                    "select true from pg_advisory_xact_lock(hashtextextended(cast(:key as"
                        + " text),617))",
            nativeQuery = true)
    boolean lockAggregate(@Param("key") String key);
}
