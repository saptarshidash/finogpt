package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.CategorySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategorySummaryRepository extends JpaRepository<CategorySummary, Long> {

    Optional<CategorySummary> findByUserIdAndYearAndMonthAndCategoryId(
            Long userId, Integer year, Integer month, Long categoryId);

    void deleteByUserId(Long userId);
}
