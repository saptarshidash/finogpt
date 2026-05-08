package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.DailySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailySummaryRepository extends JpaRepository<DailySummary, Long> {

    Optional<DailySummary> findByUserIdAndDate(Long userId, LocalDate date);

    void deleteByUserId(Long userId);
}
