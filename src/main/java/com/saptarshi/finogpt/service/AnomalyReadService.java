package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.AnomalyDetailResponse;
import com.saptarshi.finogpt.dto.AnomalyFilterOptionResponse;
import com.saptarshi.finogpt.dto.AnomalyFiltersResponse;
import com.saptarshi.finogpt.dto.AnomalyListItemResponse;
import com.saptarshi.finogpt.dto.DashboardAnomalySummaryResponse;
import com.saptarshi.finogpt.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnomalyReadService {

    private static final List<String> SUPPORTED_SEVERITIES = List.of("HIGH", "MEDIUM", "LOW");
    private static final List<String> SUPPORTED_ANOMALY_TYPES = List.of("HIGH_SPEND", "ENTITY_SPIKE", "FIRST_HIGH_TXN");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PagedResponse<AnomalyListItemResponse> listAnomalies(Long userId,
                                                                String severity,
                                                                String anomalyType,
                                                                int page,
                                                                int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", safeSize)
                .addValue("offset", safePage * safeSize);

        String where = buildWhere(parameters, severity, anomalyType);

        List<AnomalyListItemResponse> items = jdbcTemplate.query(
                "SELECT a.id, a.anomaly_type, a.description, a.severity, a.txn_id, a.created_at, " +
                        "t.txn_date, t.amount, t.entity_id, e.name AS entity_name " +
                        "FROM anomalies a " +
                        "LEFT JOIN transactions t ON t.id = a.txn_id " +
                        "LEFT JOIN entities e ON e.id = t.entity_id " +
                        "WHERE " + where + " " +
                        "ORDER BY a.created_at DESC " +
                        "LIMIT :limit OFFSET :offset",
                parameters,
                (rs, rowNum) -> AnomalyListItemResponse.builder()
                        .id(rs.getLong("id"))
                        .anomalyType(rs.getString("anomaly_type"))
                        .description(rs.getString("description"))
                        .severity(rs.getString("severity"))
                        .txnId((Long) rs.getObject("txn_id"))
                        .txnDate(rs.getObject("txn_date", LocalDate.class))
                        .amount(rs.getBigDecimal("amount"))
                        .entityId((Long) rs.getObject("entity_id"))
                        .entityName(rs.getString("entity_name"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build()
        );

        Long totalItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM anomalies a WHERE " + where,
                parameters,
                Long.class
        );

        long total = totalItems == null ? 0L : totalItems;
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) safeSize);

        return PagedResponse.<AnomalyListItemResponse>builder()
                .items(items)
                .page(safePage)
                .size(safeSize)
                .totalItems(total)
                .totalPages(totalPages)
                .build();
    }

    public AnomalyDetailResponse getAnomaly(Long userId, Long anomalyId) {
        List<AnomalyDetailResponse> items = jdbcTemplate.query(
                "SELECT a.id, a.anomaly_type, a.description, a.severity, a.txn_id, a.created_at, " +
                        "t.txn_date, t.amount, t.txn_type, t.entity_id, e.name AS entity_name, " +
                        "t.category_id, c.name AS category_name, t.raw_details, t.source " +
                        "FROM anomalies a " +
                        "LEFT JOIN transactions t ON t.id = a.txn_id " +
                        "LEFT JOIN entities e ON e.id = t.entity_id " +
                        "LEFT JOIN categories c ON c.id = t.category_id " +
                        "WHERE a.user_id = :userId AND a.id = :anomalyId",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("anomalyId", anomalyId),
                (rs, rowNum) -> AnomalyDetailResponse.builder()
                        .id(rs.getLong("id"))
                        .anomalyType(rs.getString("anomaly_type"))
                        .description(rs.getString("description"))
                        .severity(rs.getString("severity"))
                        .txnId((Long) rs.getObject("txn_id"))
                        .txnDate(rs.getObject("txn_date", LocalDate.class))
                        .amount(rs.getBigDecimal("amount"))
                        .txnType(rs.getString("txn_type"))
                        .entityId((Long) rs.getObject("entity_id"))
                        .entityName(rs.getString("entity_name"))
                        .categoryId((Long) rs.getObject("category_id"))
                        .categoryName(rs.getString("category_name"))
                        .rawDetails(rs.getString("raw_details"))
                        .source(rs.getString("source"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build()
        );

        if (items.isEmpty()) {
            throw new IllegalArgumentException("Anomaly not found");
        }

        return items.get(0);
    }

    public AnomalyFiltersResponse getFilters(Long userId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId);

        Map<String, Long> severityCounts = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT a.severity AS value, COUNT(*) AS count " +
                        "FROM anomalies a " +
                        "WHERE a.user_id = :userId " +
                        "GROUP BY a.severity",
                parameters,
                rs -> {
                    while (rs.next()) {
                        severityCounts.put(rs.getString("value"), rs.getLong("count"));
                    }
                    return null;
                }
        );

        Map<String, Long> anomalyTypeCounts = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT a.anomaly_type AS value, COUNT(*) AS count " +
                        "FROM anomalies a " +
                        "WHERE a.user_id = :userId " +
                        "GROUP BY a.anomaly_type",
                parameters,
                rs -> {
                    while (rs.next()) {
                        anomalyTypeCounts.put(rs.getString("value"), rs.getLong("count"));
                    }
                    return null;
                }
        );

        return AnomalyFiltersResponse.builder()
                .severities(buildFilterOptions(SUPPORTED_SEVERITIES, severityCounts))
                .anomalyTypes(buildFilterOptions(SUPPORTED_ANOMALY_TYPES, anomalyTypeCounts))
                .build();
    }

    public DashboardAnomalySummaryResponse getDashboardSummary(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", safeLimit);

        SeverityCounts counts = jdbcTemplate.query(
                "SELECT COUNT(*) AS total_count, " +
                        "COALESCE(SUM(CASE WHEN severity = 'HIGH' THEN 1 ELSE 0 END), 0) AS high_count, " +
                        "COALESCE(SUM(CASE WHEN severity = 'MEDIUM' THEN 1 ELSE 0 END), 0) AS medium_count, " +
                        "COALESCE(SUM(CASE WHEN severity = 'LOW' THEN 1 ELSE 0 END), 0) AS low_count " +
                        "FROM anomalies WHERE user_id = :userId",
                parameters,
                rs -> rs.next()
                        ? new SeverityCounts(
                        rs.getLong("total_count"),
                        rs.getLong("high_count"),
                        rs.getLong("medium_count"),
                        rs.getLong("low_count")
                )
                        : new SeverityCounts(0L, 0L, 0L, 0L)
        );

        List<AnomalyListItemResponse> recent = jdbcTemplate.query(
                "SELECT a.id, a.anomaly_type, a.description, a.severity, a.txn_id, a.created_at, " +
                        "t.txn_date, t.amount, t.entity_id, e.name AS entity_name " +
                        "FROM anomalies a " +
                        "LEFT JOIN transactions t ON t.id = a.txn_id " +
                        "LEFT JOIN entities e ON e.id = t.entity_id " +
                        "WHERE a.user_id = :userId " +
                        "ORDER BY a.created_at DESC " +
                        "LIMIT :limit",
                parameters,
                (rs, rowNum) -> AnomalyListItemResponse.builder()
                        .id(rs.getLong("id"))
                        .anomalyType(rs.getString("anomaly_type"))
                        .description(rs.getString("description"))
                        .severity(rs.getString("severity"))
                        .txnId((Long) rs.getObject("txn_id"))
                        .txnDate(rs.getObject("txn_date", LocalDate.class))
                        .amount(rs.getBigDecimal("amount"))
                        .entityId((Long) rs.getObject("entity_id"))
                        .entityName(rs.getString("entity_name"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build()
        );

        return DashboardAnomalySummaryResponse.builder()
                .totalAnomalies(counts.totalCount())
                .highSeverityCount(counts.highCount())
                .mediumSeverityCount(counts.mediumCount())
                .lowSeverityCount(counts.lowCount())
                .recentAnomalies(recent)
                .build();
    }

    private String buildWhere(MapSqlParameterSource parameters, String severity, String anomalyType) {
        StringBuilder where = new StringBuilder("a.user_id = :userId");

        if (severity != null && !severity.isBlank()) {
            String normalizedSeverity = severity.trim().toUpperCase();
            if (!SUPPORTED_SEVERITIES.contains(normalizedSeverity)) {
                throw new IllegalArgumentException("Unsupported anomaly severity filter");
            }
            where.append(" AND a.severity = :severity");
            parameters.addValue("severity", normalizedSeverity);
        }

        if (anomalyType != null && !anomalyType.isBlank()) {
            String normalizedAnomalyType = anomalyType.trim().toUpperCase();
            if (!SUPPORTED_ANOMALY_TYPES.contains(normalizedAnomalyType)) {
                throw new IllegalArgumentException("Unsupported anomaly type filter");
            }
            where.append(" AND a.anomaly_type = :anomalyType");
            parameters.addValue("anomalyType", normalizedAnomalyType);
        }

        return where.toString();
    }

    private List<AnomalyFilterOptionResponse> buildFilterOptions(List<String> values, Map<String, Long> counts) {
        return values.stream()
                .map(value -> AnomalyFilterOptionResponse.builder()
                        .value(value)
                        .count(counts.getOrDefault(value, 0L))
                        .build())
                .toList();
    }

    private record SeverityCounts(Long totalCount, Long highCount, Long mediumCount, Long lowCount) {
    }
}
