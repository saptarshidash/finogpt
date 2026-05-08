package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.AnalyticsBreakdownResponse;
import com.saptarshi.finogpt.dto.AnalyticsPointResponse;
import com.saptarshi.finogpt.dto.AnalyticsSeriesResponse;
import com.saptarshi.finogpt.dto.BreakdownItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsExplorerService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AnalyticsSeriesResponse getSummarySeries(Long userId,
                                                    String grain,
                                                    String metric,
                                                    LocalDate from,
                                                    LocalDate to) {
        String normalizedGrain = normalizeGrain(grain);
        String normalizedMetric = normalizeMetric(metric);

        DateWindow window = resolveSeriesWindow(normalizedGrain, from, to);

        List<AnalyticsPointResponse> points;
        if ("DAILY".equals(normalizedGrain)) {
            points = queryDailySeries(userId, normalizedMetric, window);
        } else if ("MONTHLY".equals(normalizedGrain)) {
            points = queryMonthlySeries(userId, normalizedMetric, window);
        } else {
            throw new IllegalArgumentException("Unsupported analytics grain");
        }

        return AnalyticsSeriesResponse.builder()
                .grain(normalizedGrain.toLowerCase())
                .metric(normalizedMetric.toLowerCase())
                .from(window.from())
                .to(window.to())
                .points(points)
                .build();
    }

    public AnalyticsBreakdownResponse getEntityBreakdown(Long userId,
                                                         LocalDate from,
                                                         LocalDate to,
                                                         int limit,
                                                         String sort) {
        DateWindow window = resolveBreakdownWindow(from, to);
        String normalizedSort = normalizeBreakdownSort(sort);

        return AnalyticsBreakdownResponse.builder()
                .dimension("entity")
                .from(window.from())
                .to(window.to())
                .sort(normalizedSort.toLowerCase())
                .items(queryDimensionBreakdown(userId, "ENTITY", window, safeLimit(limit), normalizedSort))
                .build();
    }

    public AnalyticsBreakdownResponse getCategoryBreakdown(Long userId,
                                                           LocalDate from,
                                                           LocalDate to,
                                                           int limit,
                                                           String sort) {
        DateWindow window = resolveBreakdownWindow(from, to);
        String normalizedSort = normalizeBreakdownSort(sort);

        return AnalyticsBreakdownResponse.builder()
                .dimension("category")
                .from(window.from())
                .to(window.to())
                .sort(normalizedSort.toLowerCase())
                .items(queryDimensionBreakdown(userId, "CATEGORY", window, safeLimit(limit), normalizedSort))
                .build();
    }

    public AnalyticsBreakdownResponse getBreakdown(Long userId,
                                                   String dimension,
                                                   LocalDate from,
                                                   LocalDate to,
                                                   int limit,
                                                   String sort) {
        String normalizedDimension = normalizeDimension(dimension);
        return "ENTITY".equals(normalizedDimension)
                ? getEntityBreakdown(userId, from, to, limit, sort)
                : getCategoryBreakdown(userId, from, to, limit, sort);
    }

    private List<AnalyticsPointResponse> queryDailySeries(Long userId,
                                                          String metric,
                                                          DateWindow window) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromDate", window.from())
                .addValue("toDate", window.to());

        String valueColumn;
        if ("SPEND".equals(metric)) {
            valueColumn = "ds.total_debit";
        } else if ("CREDIT".equals(metric)) {
            valueColumn = "ds.total_credit";
        } else if ("TXN_COUNT".equals(metric)) {
            valueColumn = "CAST(ds.txn_count AS NUMERIC)";
        } else {
            throw new IllegalArgumentException("Unsupported analytics metric");
        }

        return jdbcTemplate.query(
                "SELECT ds.date AS period_start, " + valueColumn + " AS value " +
                        "FROM daily_summary ds " +
                        "WHERE ds.user_id = :userId " +
                        "AND ds.date BETWEEN :fromDate AND :toDate " +
                        "ORDER BY ds.date",
                parameters,
                (rs, rowNum) -> AnalyticsPointResponse.builder()
                        .periodStart(rs.getObject("period_start", LocalDate.class))
                        .year(null)
                        .month(null)
                        .value(rs.getBigDecimal("value"))
                        .build()
        );
    }

    private List<AnalyticsPointResponse> queryMonthlySeries(Long userId,
                                                            String metric,
                                                            DateWindow window) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromDate", window.from())
                .addValue("toDate", window.to());

        String valueColumn;
        if ("SPEND".equals(metric)) {
            valueColumn = "ds.total_debit";
        } else if ("CREDIT".equals(metric)) {
            valueColumn = "ds.total_credit";
        } else if ("TXN_COUNT".equals(metric)) {
            valueColumn = "CAST(ds.txn_count AS NUMERIC)";
        } else {
            throw new IllegalArgumentException("Unsupported analytics metric");
        }

        return jdbcTemplate.query(
                "SELECT EXTRACT(YEAR FROM ds.date)::INT AS year, " +
                        "EXTRACT(MONTH FROM ds.date)::INT AS month, " +
                        "DATE_TRUNC('month', ds.date)::DATE AS period_start, " +
                        "COALESCE(SUM(" + valueColumn + "), 0) AS value " +
                        "FROM daily_summary ds " +
                        "WHERE ds.user_id = :userId " +
                        "AND ds.date BETWEEN :fromDate AND :toDate " +
                        "GROUP BY EXTRACT(YEAR FROM ds.date), EXTRACT(MONTH FROM ds.date), DATE_TRUNC('month', ds.date) " +
                        "ORDER BY year, month",
                parameters,
                (rs, rowNum) -> AnalyticsPointResponse.builder()
                        .periodStart(rs.getObject("period_start", LocalDate.class))
                        .year(rs.getInt("year"))
                        .month(rs.getInt("month"))
                        .value(rs.getBigDecimal("value"))
                        .build()
        );
    }

    private List<BreakdownItemResponse> queryDimensionBreakdown(Long userId,
                                                                String dimension,
                                                                DateWindow window,
                                                                int limit,
                                                                String sort) {
        boolean entity = "ENTITY".equals(dimension);
        String idExpression = entity ? "t.entity_id" : "COALESCE(t.user_category_id, t.category_id)";
        String joinTable = entity ? "entities" : "categories";
        String joinAlias = entity ? "e" : "c";
        String nonNullPredicate = entity
                ? "t.entity_id IS NOT NULL"
                : "COALESCE(t.user_category_id, t.category_id) IS NOT NULL";
        String orderColumn = "COUNT".equals(sort) ? "txn_count" : "total_amount";

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromDate", window.from())
                .addValue("toDate", window.to())
                .addValue("limit", limit);

        return jdbcTemplate.query(
                "SELECT " + idExpression + " AS id, " + joinAlias + ".name, " +
                        "COALESCE(SUM(t.amount), 0) AS total_amount, " +
                        "COUNT(*) AS txn_count " +
                        "FROM transactions t " +
                        "JOIN " + joinTable + " " + joinAlias + " ON " + joinAlias + ".id = " + idExpression + " " +
                        "WHERE t.user_id = :userId " +
                        "AND t.txn_type = 'DEBIT' " +
                        "AND " + nonNullPredicate + " " +
                        "AND t.txn_date BETWEEN :fromDate AND :toDate " +
                        "GROUP BY " + idExpression + ", " + joinAlias + ".name " +
                        "ORDER BY " + orderColumn + " DESC, total_amount DESC, txn_count DESC " +
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

    private DateWindow resolveSeriesWindow(String grain, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from date must be on or before to date");
        }

        if ("DAILY".equals(grain)) {
            LocalDate end = to != null ? to : LocalDate.now();
            LocalDate start = from != null ? from : end.minusDays(29);
            return new DateWindow(start, end);
        }

        LocalDate anchorMonthStart = (to != null ? to : LocalDate.now()).withDayOfMonth(1);
        LocalDate end = to != null ? to : anchorMonthStart;
        LocalDate start = from != null ? from : anchorMonthStart.minusMonths(5);
        return new DateWindow(start, end);
    }

    private DateWindow resolveBreakdownWindow(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from date must be on or before to date");
        }

        LocalDate anchorMonthStart = (to != null ? to : LocalDate.now()).withDayOfMonth(1);
        LocalDate end = to != null ? to : anchorMonthStart;
        LocalDate start = from != null ? from : anchorMonthStart.minusMonths(5);
        return new DateWindow(start, end);
    }

    private String normalizeGrain(String grain) {
        String normalized = grain == null ? "MONTHLY" : grain.trim().toUpperCase();
        if (!List.of("DAILY", "MONTHLY").contains(normalized)) {
            throw new IllegalArgumentException("grain must be daily or monthly");
        }
        return normalized;
    }

    private String normalizeMetric(String metric) {
        String normalized = metric == null ? "SPEND" : metric.trim().toUpperCase();
        if (!List.of("SPEND", "CREDIT", "TXN_COUNT").contains(normalized)) {
            throw new IllegalArgumentException("metric must be spend, credit, or txn_count");
        }
        return normalized;
    }

    private String normalizeDimension(String dimension) {
        String normalized = dimension == null ? "" : dimension.trim().toUpperCase();
        if (!List.of("ENTITY", "CATEGORY").contains(normalized)) {
            throw new IllegalArgumentException("dimension must be entity or category");
        }
        return normalized;
    }

    private String normalizeBreakdownSort(String sort) {
        String normalized = sort == null ? "AMOUNT" : sort.trim().toUpperCase();
        if (!List.of("AMOUNT", "COUNT").contains(normalized)) {
            throw new IllegalArgumentException("sort must be amount or count");
        }
        return normalized;
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    private record DateWindow(LocalDate from, LocalDate to) {
    }
}
