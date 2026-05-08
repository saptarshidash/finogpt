package com.saptarshi.finogpt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IngestionReconciliationService {

    private final JdbcTemplate jdbcTemplate;

    public void reconcileJob(UUID jobId, Long userId) {
        List<Map<String, Object>> affectedMonths = jdbcTemplate.queryForList(
                "SELECT DISTINCT EXTRACT(YEAR FROM txn_date) AS year, " +
                        "EXTRACT(MONTH FROM txn_date) AS month " +
                        "FROM transactions " +
                        "WHERE ingestion_job_id = ?",
                jobId
        );

        for (Map<String, Object> monthRow : affectedMonths) {
            int year = ((Number) monthRow.get("year")).intValue();
            int month = ((Number) monthRow.get("month")).intValue();

            Map<String, Object> raw = jdbcTemplate.queryForMap(
                    "SELECT COALESCE(SUM(CASE WHEN txn_type = 'DEBIT' THEN amount ELSE 0 END), 0) AS total_spend, " +
                            "COALESCE(SUM(CASE WHEN txn_type = 'CREDIT' THEN amount ELSE 0 END), 0) AS total_credit, " +
                            "COUNT(*) AS txn_count " +
                            "FROM transactions " +
                            "WHERE user_id = ? " +
                            "AND EXTRACT(YEAR FROM txn_date) = ? " +
                            "AND EXTRACT(MONTH FROM txn_date) = ?",
                    userId,
                    year,
                    month
            );

            List<Map<String, Object>> summaryRows = jdbcTemplate.queryForList(
                    "SELECT total_spend, total_credit, txn_count " +
                            "FROM monthly_summary " +
                            "WHERE user_id = ? " +
                            "AND year = ? " +
                            "AND month = ?",
                    userId,
                    year,
                    month
            );

            if (summaryRows.isEmpty()) {
                throw new IllegalStateException(
                        "Monthly summary row missing for user %d, %d-%02d".formatted(userId, year, month)
                );
            }

            Map<String, Object> summary = summaryRows.get(0);
            BigDecimal rawSpend = toBigDecimal(raw.get("total_spend"));
            BigDecimal rawCredit = toBigDecimal(raw.get("total_credit"));
            int rawCount = ((Number) raw.get("txn_count")).intValue();

            BigDecimal summarySpend = toBigDecimal(summary.get("total_spend"));
            BigDecimal summaryCredit = toBigDecimal(summary.get("total_credit"));
            int summaryCount = ((Number) summary.get("txn_count")).intValue();

            if (rawSpend.compareTo(summarySpend) != 0
                    || rawCredit.compareTo(summaryCredit) != 0
                    || rawCount != summaryCount) {
                throw new IllegalStateException(
                        "Summary mismatch for user %d, %d-%02d".formatted(userId, year, month)
                );
            }
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }

        return new BigDecimal(value.toString());
    }
}
