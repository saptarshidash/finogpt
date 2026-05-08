package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.BreakdownItemResponse;
import com.saptarshi.finogpt.dto.DailyCashflowPointResponse;
import com.saptarshi.finogpt.dto.DashboardOverviewResponse;
import com.saptarshi.finogpt.dto.MonthlyTrendPointResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DashboardOverviewResponse getOverview(Long userId, String period) {
        OverviewWindow window = resolveOverviewWindow(period);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("startYm", window.startYearMonth())
                .addValue("endYm", window.endYearMonth());

        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(ms.total_spend), 0) AS total_spend, " +
                        "COALESCE(SUM(ms.total_credit), 0) AS total_credit, " +
                        "COALESCE(SUM(ms.txn_count), 0) AS txn_count " +
                        "FROM monthly_summary ms " +
                        "WHERE ms.user_id = :userId " +
                "AND (ms.year * 100 + ms.month) BETWEEN :startYm AND :endYm",
                parameters,
                (rs, rowNum) -> DashboardOverviewResponse.builder()
                        .period(window.getLabel())
                        .startDate(window.getStartDate())
                        .endDate(window.getEndDate())
                        .totalSpend(rs.getBigDecimal("total_spend"))
                        .totalCredit(rs.getBigDecimal("total_credit"))
                        .txnCount(rs.getLong("txn_count"))
                        .build()
        );
    }

    public List<MonthlyTrendPointResponse> getSpendTrend(Long userId, int months) {
        int safeMonths = Math.max(1, Math.min(months, 24));
        LocalDate endMonth = resolveLatestSummaryMonth(userId);
        LocalDate startMonth = endMonth.minusMonths(safeMonths - 1L);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("startYm", startMonth.getYear() * 100 + startMonth.getMonthValue())
                .addValue("endYm", endMonth.getYear() * 100 + endMonth.getMonthValue());

        return jdbcTemplate.query(
                "SELECT ms.year, ms.month, ms.total_spend, ms.total_credit, ms.txn_count " +
                        "FROM monthly_summary ms " +
                        "WHERE ms.user_id = :userId " +
                        "AND (ms.year * 100 + ms.month) BETWEEN :startYm AND :endYm " +
                        "ORDER BY ms.year, ms.month",
                parameters,
                (rs, rowNum) -> MonthlyTrendPointResponse.builder()
                        .year(rs.getInt("year"))
                        .month(rs.getInt("month"))
                        .totalSpend(rs.getBigDecimal("total_spend"))
                        .totalCredit(rs.getBigDecimal("total_credit"))
                        .txnCount(rs.getLong("txn_count"))
                        .build()
        );
    }

    private LocalDate resolveLatestSummaryMonth(Long userId) {
        Integer latestYearMonth = jdbcTemplate.queryForObject(
                "SELECT MAX(ms.year * 100 + ms.month) " +
                        "FROM monthly_summary ms " +
                        "WHERE ms.user_id = :userId",
                new MapSqlParameterSource().addValue("userId", userId),
                Integer.class
        );

        if (latestYearMonth == null) {
            return LocalDate.now().withDayOfMonth(1);
        }

        int year = latestYearMonth / 100;
        int month = latestYearMonth % 100;
        return LocalDate.of(year, month, 1);
    }

    public List<DailyCashflowPointResponse> getCashflowDaily(Long userId, int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(safeDays - 1L);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        return jdbcTemplate.query(
                "SELECT ds.date, ds.total_debit, ds.total_credit, ds.txn_count " +
                        "FROM daily_summary ds " +
                        "WHERE ds.user_id = :userId " +
                        "AND ds.date BETWEEN :startDate AND :endDate " +
                        "ORDER BY ds.date",
                parameters,
                (rs, rowNum) -> DailyCashflowPointResponse.builder()
                        .date(rs.getObject("date", LocalDate.class))
                        .totalDebit(rs.getBigDecimal("total_debit"))
                        .totalCredit(rs.getBigDecimal("total_credit"))
                        .txnCount(rs.getLong("txn_count"))
                        .build()
        );
    }

    public List<BreakdownItemResponse> getTopMerchants(Long userId, Integer year, Integer month, int limit) {
        YearMonthSelection selection = resolveYearMonth(userId, year, month);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", safeLimit(limit));

        StringBuilder sql = new StringBuilder(
                "SELECT es.entity_id AS id, e.name, COALESCE(SUM(es.total_amount), 0) AS total_amount, " +
                        "COALESCE(SUM(es.txn_count), 0) AS txn_count " +
                        "FROM entity_summary es " +
                        "JOIN entities e ON e.id = es.entity_id " +
                        "WHERE es.user_id = :userId "
        );

        if (selection.getYear() != null) {
            sql.append("AND es.year = :year ");
            parameters.addValue("year", selection.getYear());
        }

        if (selection.getMonth() != null) {
            sql.append("AND es.month = :month ");
            parameters.addValue("month", selection.getMonth());
        }

        sql.append(
                "GROUP BY es.entity_id, e.name " +
                        "ORDER BY total_amount DESC, txn_count DESC " +
                        "LIMIT :limit"
        );

        return jdbcTemplate.query(
                sql.toString(),
                parameters,
                (rs, rowNum) -> BreakdownItemResponse.builder()
                        .id(rs.getLong("id"))
                        .name(rs.getString("name"))
                        .totalAmount(rs.getBigDecimal("total_amount"))
                        .txnCount(rs.getLong("txn_count"))
                        .build()
        );
    }

    public List<BreakdownItemResponse> getTopCategories(Long userId, Integer year, Integer month, int limit) {
        YearMonthSelection selection = resolveYearMonth(userId, year, month);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", safeLimit(limit));

        StringBuilder sql = new StringBuilder(
                "SELECT cs.category_id AS id, c.name, COALESCE(SUM(cs.total_amount), 0) AS total_amount, " +
                        "COALESCE(SUM(cs.txn_count), 0) AS txn_count " +
                        "FROM category_summary cs " +
                        "JOIN categories c ON c.id = cs.category_id " +
                        "WHERE cs.user_id = :userId "
        );

        if (selection.getYear() != null) {
            sql.append("AND cs.year = :year ");
            parameters.addValue("year", selection.getYear());
        }

        if (selection.getMonth() != null) {
            sql.append("AND cs.month = :month ");
            parameters.addValue("month", selection.getMonth());
        }

        sql.append(
                "GROUP BY cs.category_id, c.name " +
                        "ORDER BY total_amount DESC, txn_count DESC " +
                        "LIMIT :limit"
        );

        return jdbcTemplate.query(
                sql.toString(),
                parameters,
                (rs, rowNum) -> BreakdownItemResponse.builder()
                        .id(rs.getLong("id"))
                        .name(rs.getString("name"))
                        .totalAmount(rs.getBigDecimal("total_amount"))
                        .txnCount(rs.getLong("txn_count"))
                        .build()
        );
    }

    private OverviewWindow resolveOverviewWindow(String period) {
        LocalDate now = LocalDate.now();
        String normalized = period == null ? "this_month" : period.trim().toLowerCase();

        if ("last_month".equals(normalized)) {
            LocalDate month = now.minusMonths(1).withDayOfMonth(1);
            return new OverviewWindow("last_month", month, month.withDayOfMonth(month.lengthOfMonth()));
        }

        if ("year_to_date".equals(normalized)) {
            return new OverviewWindow(
                    "year_to_date",
                    LocalDate.of(now.getYear(), 1, 1),
                    now
            );
        }

        return new OverviewWindow(
                "this_month",
                now.withDayOfMonth(1),
                now
        );
    }

    private YearMonthSelection resolveYearMonth(Long userId, Integer year, Integer month) {
        LocalDate now = LocalDate.now();

        if (year != null) {
            return new YearMonthSelection(year, month);
        }

        if (month != null) {
            return new YearMonthSelection(now.getYear(), month);
        }

        return new YearMonthSelection(null, null);
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            return 5;
        }
        return Math.min(limit, 20);
    }

    private static final class OverviewWindow {
        private final String label;
        private final LocalDate startDate;
        private final LocalDate endDate;

        private OverviewWindow(String label, LocalDate startDate, LocalDate endDate) {
            this.label = label;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        private String getLabel() {
            return label;
        }

        private LocalDate getStartDate() {
            return startDate;
        }

        private LocalDate getEndDate() {
            return endDate;
        }

        private int startYearMonth() {
            return startDate.getYear() * 100 + startDate.getMonthValue();
        }

        private int endYearMonth() {
            return endDate.getYear() * 100 + endDate.getMonthValue();
        }
    }

    private static final class YearMonthSelection {
        private final Integer year;
        private final Integer month;

        private YearMonthSelection(Integer year, Integer month) {
            this.year = year;
            this.month = month;
        }

        private Integer getYear() {
            return year;
        }

        private Integer getMonth() {
            return month;
        }
    }
}
