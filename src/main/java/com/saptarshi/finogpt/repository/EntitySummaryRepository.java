package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.EntitySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EntitySummaryRepository extends JpaRepository<EntitySummary, Long> {

    Optional<EntitySummary> findByUserIdAndYearAndMonthAndEntityId(
            Long userId, Integer year, Integer month, Long entityId);

    void deleteByUserId(Long userId);
}
