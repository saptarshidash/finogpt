package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.InsightCache;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InsightCacheRepository extends JpaRepository<InsightCache, Long> {

    Optional<InsightCache> findByUserIdAndYearAndMonth(Long userId, Integer year, Integer month);

    Page<InsightCache> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<InsightCache> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
