package com.msa.productquery.domain.projection.repository;

import com.msa.productquery.domain.projection.entity.ProcessedEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** eventId는 재발행·재소비에도 같은 식별자로 유지되어야 합니다. */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}
