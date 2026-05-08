package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.BreakdownItemResponse;
import com.saptarshi.finogpt.dto.DimensionSummaryDetailResponse;
import com.saptarshi.finogpt.dto.DimensionTrendPointResponse;
import com.saptarshi.finogpt.entity.Category;
import com.saptarshi.finogpt.entity.EntityTxn;
import com.saptarshi.finogpt.repository.CategoryRepository;
import com.saptarshi.finogpt.repository.EntityTxnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DimensionSummaryService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EntityTxnRepository entityTxnRepository;
    private final CategoryRepository categoryRepository;

    public List<BreakdownItemResponse> listEntities(Long userId, Integer year, Integer month, Integer months, int limit) {
        PeriodWindow periodWindow = resolvePeriod(year, month, months);
        MapSqlParameterSource parameters = periodParameters(userId, periodWindow)
                .addValue("limit", safeLimit(limit));

        return jdbcTemplate.query(
                "SELECT es.entity_id AS id, e.name, COALESCE(SUM(es.total_amount), 0) AS total_amount, " +
                        "COALESCE(SUM(es.txn_count), 0) AS txn_count " +
                        "FROM entity_summary es " +
                        "JOIN entities e ON e.id = es.entity_id " +
                        "WHERE es.user_id = :userId AND (es.year * 100 + es.month) BETWEEN :startYm AND :endYm " +
                        "GROUP BY es.entity_id, e.name " +
                        "ORDER BY total_amount DESC, txn_count DESC " +
                        "LIMIT :limit",
                parameters,
                (rs, rowNum) -> BreakdownItemResponse.builder()
                        .id(rs.getLong("id"))
                        .name(rs.getString("name"))
                        .totalAmount(rs.getBigDecimal("total_amount"))
                        .txnCount(rs.getLong("txn_count"))
                        .build()
        );
    }

    public List<BreakdownItemResponse> listCategories(Long userId, Integer year, Integer month, Integer months, int limit) {
        PeriodWindow periodWindow = resolvePeriod(year, month, months);
        MapSqlParameterSource parameters = periodParameters(userId, periodWindow)
                .addValue("limit", safeLimit(limit));

        return jdbcTemplate.query(
                "SELECT cs.category_id AS id, c.name, COALESCE(SUM(cs.total_amount), 0) AS total_amount, " +
                        "COALESCE(SUM(cs.txn_count), 0) AS txn_count " +
                        "FROM category_summary cs " +
                        "JOIN categories c ON c.id = cs.category_id " +
                        "WHERE cs.user_id = :userId AND (cs.year * 100 + cs.month) BETWEEN :startYm AND :endYm " +
                        "GROUP BY cs.category_id, c.name " +
                        "ORDER BY total_amount DESC, txn_count DESC " +
                        "LIMIT :limit",
                parameters,
                (rs, rowNum) -> BreakdownItemResponse.builder()
                        .id(rs.getLong("id"))
                        .name(rs.getString("name"))
                        .totalAmount(rs.getBigDecimal("total_amount"))
                        .txnCount(rs.getLong("txn_count"))
                        .build()
        );
    }

    public DimensionSummaryDetailResponse getEntityDetail(Long userId,
                                                          Long entityId,
                                                          Integer year,
                                                          Integer month,
                                                          Integer months) {
        EntityTxn entity = entityTxnRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("Entity not found"));

        PeriodWindow periodWindow = resolvePeriod(year, month, months);
        MapSqlParameterSource parameters = periodParameters(userId, periodWindow)
                .addValue("entityId", entityId);

        SummaryAggregate aggregate = jdbcTemplate.query(
                "SELECT COALESCE(SUM(es.total_amount), 0) AS total_amount, " +
                        "COALESCE(SUM(es.txn_count), 0) AS txn_count " +
                        "FROM entity_summary es " +
                        "WHERE es.user_id = :userId AND es.entity_id = :entityId " +
                        "AND (es.year * 100 + es.month) BETWEEN :startYm AND :endYm",
                parameters,
                rs -> rs.next()
                        ? new SummaryAggregate(rs.getBigDecimal("total_amount"), rs.getLong("txn_count"))
                        : new SummaryAggregate(java.math.BigDecimal.ZERO, 0L)
        );

        List<DimensionTrendPointResponse> trend = jdbcTemplate.query(
                "SELECT es.year, es.month, es.total_amount, es.txn_count " +
                        "FROM entity_summary es " +
                        "WHERE es.user_id = :userId AND es.entity_id = :entityId " +
                        "AND (es.year * 100 + es.month) BETWEEN :startYm AND :endYm " +
                        "ORDER BY es.year, es.month",
                parameters,
                (rs, rowNum) -> DimensionTrendPointResponse.builder()
                        .year(rs.getInt("year"))
                        .month(rs.getInt("month"))
                        .totalAmount(rs.getBigDecimal("total_amount"))
                        .txnCount(rs.getLong("txn_count"))
                        .build()
        );

        return DimensionSummaryDetailResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .dimension("ENTITY")
                .period(periodWindow.label())
                .totalAmount(aggregate.totalAmount())
                .txnCount(aggregate.txnCount())
                .trend(trend)
                .build();
    }

    public DimensionSummaryDetailResponse getCategoryDetail(Long userId,
                                                            Long categoryId,
                                                            Integer year,
                                                            Integer month,
                                                            Integer months) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        PeriodWindow periodWindow = resolvePeriod(year, month, months);
        MapSqlParameterSource parameters = periodParameters(userId, periodWindow)
                .addValue("categoryId", categoryId);

        SummaryAggregate aggregate = jdbcTemplate.query(
                "SELECT COALESCE(SUM(cs.total_amount), 0) AS total_amount, " +
                        "COALESCE(SUM(cs.txn_count), 0) AS txn_count " +
                        "FROM category_summary cs " +
                        "WHERE cs.user_id = :userId AND cs.category_id = :categoryId " +
                        "AND (cs.year * 100 + cs.month) BETWEEN :startYm AND :endYm",
                parameters,
                rs -> rs.next()
                        ? new SummaryAggregate(rs.getBigDecimal("total_amount"), rs.getLong("txn_count"))
                        : new SummaryAggregate(java.math.BigDecimal.ZERO, 0L)
        );

        List<DimensionTrendPointResponse> trend = jdbcTemplate.query(
                "SELECT cs.year, cs.month, cs.total_amount, cs.txn_count " +
                        "FROM category_summary cs " +
                        "WHERE cs.user_id = :userId AND cs.category_id = :categoryId " +
                        "AND (cs.year * 100 + cs.month) BETWEEN :startYm AND :endYm " +
                        "ORDER BY cs.year, cs.month",
                parameters,
                (rs, rowNum) -> DimensionTrendPointResponse.builder()
                        .year(rs.getInt("year"))
                        .month(rs.getInt("month"))
                        .totalAmount(rs.getBigDecimal("total_amount"))
                        .txnCount(rs.getLong("txn_count"))
                        .build()
        );

        return DimensionSummaryDetailResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .dimension("CATEGORY")
                .period(periodWindow.label())
                .totalAmount(aggregate.totalAmount())
                .txnCount(aggregate.txnCount())
                .trend(trend)
                .build();
    }

    private MapSqlParameterSource periodParameters(Long userId, PeriodWindow periodWindow) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("startYm", periodWindow.startYm())
                .addValue("endYm", periodWindow.endYm());
    }

    private PeriodWindow resolvePeriod(Integer year, Integer month, Integer months) {
        java.time.LocalDate now = java.time.LocalDate.now().withDayOfMonth(1);

        if (months != null) {
            int safeMonths = Math.max(1, Math.min(months, 24));
            java.time.LocalDate start = now.minusMonths(safeMonths - 1L);
            return new PeriodWindow(
                    "last_" + safeMonths + "_months",
                    start.getYear() * 100 + start.getMonthValue(),
                    now.getYear() * 100 + now.getMonthValue()
            );
        }

        if (year != null && month != null) {
            validateMonth(month);
            int yearMonth = year * 100 + month;
            return new PeriodWindow(year + "-" + String.format("%02d", month), yearMonth, yearMonth);
        }

        if (year != null) {
            return new PeriodWindow(
                    String.valueOf(year),
                    year * 100 + 1,
                    year * 100 + 12
            );
        }

        int currentYm = now.getYear() * 100 + now.getMonthValue();
        return new PeriodWindow("current_month", currentYm, currentYm);
    }

    private void validateMonth(Integer month) {
        if (month == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    private record PeriodWindow(String label, int startYm, int endYm) {
    }

    private record SummaryAggregate(java.math.BigDecimal totalAmount, Long txnCount) {
    }
}
