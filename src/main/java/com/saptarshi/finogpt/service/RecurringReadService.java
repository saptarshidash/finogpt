package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.DashboardRecurringSummaryResponse;
import com.saptarshi.finogpt.dto.PagedResponse;
import com.saptarshi.finogpt.dto.RecurringDetailResponse;
import com.saptarshi.finogpt.dto.RecurringListItemResponse;
import com.saptarshi.finogpt.dto.TransactionListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecurringReadService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PagedResponse<RecurringListItemResponse> listRecurring(Long userId,
                                                                  Long entityId,
                                                                  Long categoryId,
                                                                  int page,
                                                                  int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", safeSize)
                .addValue("offset", safePage * safeSize);

        String where = buildWhere(parameters, entityId, categoryId);

        List<RecurringListItemResponse> items = jdbcTemplate.query(
                "SELECT r.id, r.entity_id, e.name AS entity_name, r.category_id, c.name AS category_name, " +
                        "r.frequency, r.avg_amount, r.last_seen, r.created_at " +
                        "FROM recurring_transactions r " +
                        "LEFT JOIN entities e ON e.id = r.entity_id " +
                        "LEFT JOIN categories c ON c.id = r.category_id " +
                        "WHERE " + where + " " +
                        "ORDER BY r.last_seen DESC, r.created_at DESC " +
                        "LIMIT :limit OFFSET :offset",
                parameters,
                (rs, rowNum) -> {
                    LocalDate lastSeen = rs.getObject("last_seen", LocalDate.class);
                    Integer frequency = (Integer) rs.getObject("frequency");
                    return RecurringListItemResponse.builder()
                            .id(rs.getLong("id"))
                            .entityId((Long) rs.getObject("entity_id"))
                            .entityName(rs.getString("entity_name"))
                            .categoryId((Long) rs.getObject("category_id"))
                            .categoryName(rs.getString("category_name"))
                            .frequencyDays(frequency)
                            .avgAmount(rs.getBigDecimal("avg_amount"))
                            .lastSeen(lastSeen)
                            .nextExpectedDate(lastSeen != null && frequency != null ? lastSeen.plusDays(frequency) : null)
                            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                            .build();
                }
        );

        Long totalItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recurring_transactions r WHERE " + where,
                parameters,
                Long.class
        );

        long total = totalItems == null ? 0L : totalItems;
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) safeSize);

        return PagedResponse.<RecurringListItemResponse>builder()
                .items(items)
                .page(safePage)
                .size(safeSize)
                .totalItems(total)
                .totalPages(totalPages)
                .build();
    }

    public RecurringDetailResponse getRecurring(Long userId, Long recurringId) {
        List<RecurringDetailResponse> items = jdbcTemplate.query(
                "SELECT r.id, r.entity_id, e.name AS entity_name, r.category_id, c.name AS category_name, " +
                        "r.frequency, r.avg_amount, r.last_seen, r.created_at " +
                        "FROM recurring_transactions r " +
                        "LEFT JOIN entities e ON e.id = r.entity_id " +
                        "LEFT JOIN categories c ON c.id = r.category_id " +
                        "WHERE r.user_id = :userId AND r.id = :recurringId",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("recurringId", recurringId),
                (rs, rowNum) -> {
                    Long entityId = (Long) rs.getObject("entity_id");
                    LocalDate lastSeen = rs.getObject("last_seen", LocalDate.class);
                    Integer frequency = (Integer) rs.getObject("frequency");
                    return RecurringDetailResponse.builder()
                            .id(rs.getLong("id"))
                            .entityId(entityId)
                            .entityName(rs.getString("entity_name"))
                            .categoryId((Long) rs.getObject("category_id"))
                            .categoryName(rs.getString("category_name"))
                            .frequencyDays(frequency)
                            .avgAmount(rs.getBigDecimal("avg_amount"))
                            .lastSeen(lastSeen)
                            .nextExpectedDate(lastSeen != null && frequency != null ? lastSeen.plusDays(frequency) : null)
                            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                            .recentTransactions(loadRecentTransactions(userId, entityId))
                            .build();
                }
        );

        if (items.isEmpty()) {
            throw new IllegalArgumentException("Recurring item not found");
        }

        return items.get(0);
    }

    public DashboardRecurringSummaryResponse getDashboardSummary(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", safeLimit)
                .addValue("today", LocalDate.now())
                .addValue("dueSoonDate", LocalDate.now().plusDays(7));

        SummaryCounts counts = jdbcTemplate.query(
                "SELECT COUNT(*) AS total_count, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN r.last_seen IS NOT NULL AND r.frequency IS NOT NULL " +
                        "AND (r.last_seen + r.frequency * INTERVAL '1 day') BETWEEN :today AND :dueSoonDate " +
                        "THEN 1 ELSE 0 END), 0) AS due_soon_count " +
                        "FROM recurring_transactions r WHERE r.user_id = :userId",
                parameters,
                rs -> rs.next()
                        ? new SummaryCounts(rs.getLong("total_count"), rs.getLong("due_soon_count"))
                        : new SummaryCounts(0L, 0L)
        );

        List<RecurringListItemResponse> items = jdbcTemplate.query(
                "SELECT r.id, r.entity_id, e.name AS entity_name, r.category_id, c.name AS category_name, " +
                        "r.frequency, r.avg_amount, r.last_seen, r.created_at " +
                        "FROM recurring_transactions r " +
                        "LEFT JOIN entities e ON e.id = r.entity_id " +
                        "LEFT JOIN categories c ON c.id = r.category_id " +
                        "WHERE r.user_id = :userId " +
                        "ORDER BY r.last_seen DESC, r.created_at DESC " +
                        "LIMIT :limit",
                parameters,
                (rs, rowNum) -> {
                    LocalDate lastSeen = rs.getObject("last_seen", LocalDate.class);
                    Integer frequency = (Integer) rs.getObject("frequency");
                    return RecurringListItemResponse.builder()
                            .id(rs.getLong("id"))
                            .entityId((Long) rs.getObject("entity_id"))
                            .entityName(rs.getString("entity_name"))
                            .categoryId((Long) rs.getObject("category_id"))
                            .categoryName(rs.getString("category_name"))
                            .frequencyDays(frequency)
                            .avgAmount(rs.getBigDecimal("avg_amount"))
                            .lastSeen(lastSeen)
                            .nextExpectedDate(lastSeen != null && frequency != null ? lastSeen.plusDays(frequency) : null)
                            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                            .build();
                }
        );

        return DashboardRecurringSummaryResponse.builder()
                .totalRecurring(counts.totalCount())
                .dueSoonCount(counts.dueSoonCount())
                .recurringItems(items)
                .build();
    }

    public List<RecurringListItemResponse> listDueSoon(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("today", LocalDate.now())
                .addValue("dueSoonDate", LocalDate.now().plusDays(7))
                .addValue("limit", safeLimit);

        return jdbcTemplate.query(
                "SELECT r.id, r.entity_id, e.name AS entity_name, r.category_id, c.name AS category_name, " +
                        "r.frequency, r.avg_amount, r.last_seen, r.created_at, " +
                        "(r.last_seen + r.frequency * INTERVAL '1 day')::date AS next_expected_date " +
                        "FROM recurring_transactions r " +
                        "LEFT JOIN entities e ON e.id = r.entity_id " +
                        "LEFT JOIN categories c ON c.id = r.category_id " +
                        "WHERE r.user_id = :userId " +
                        "AND r.last_seen IS NOT NULL " +
                        "AND r.frequency IS NOT NULL " +
                        "AND (r.last_seen + r.frequency * INTERVAL '1 day') BETWEEN :today AND :dueSoonDate " +
                        "ORDER BY next_expected_date, r.avg_amount DESC, r.created_at DESC " +
                        "LIMIT :limit",
                parameters,
                (rs, rowNum) -> RecurringListItemResponse.builder()
                        .id(rs.getLong("id"))
                        .entityId((Long) rs.getObject("entity_id"))
                        .entityName(rs.getString("entity_name"))
                        .categoryId((Long) rs.getObject("category_id"))
                        .categoryName(rs.getString("category_name"))
                        .frequencyDays((Integer) rs.getObject("frequency"))
                        .avgAmount(rs.getBigDecimal("avg_amount"))
                        .lastSeen(rs.getObject("last_seen", LocalDate.class))
                        .nextExpectedDate(rs.getObject("next_expected_date", LocalDate.class))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build()
        );
    }

    private List<TransactionListItemResponse> loadRecentTransactions(Long userId, Long entityId) {
        if (entityId == null) {
            return List.of();
        }

        return jdbcTemplate.query(
                "SELECT t.id, t.txn_date, t.txn_time, t.entity_id, e.name AS entity_name, " +
                        "t.category_id, c.name AS category_name, t.amount, t.txn_type, " +
                        "t.raw_details, t.source, t.external_txn_id, t.created_at " +
                        "FROM transactions t " +
                        "LEFT JOIN entities e ON e.id = t.entity_id " +
                        "LEFT JOIN categories c ON c.id = t.category_id " +
                        "WHERE t.user_id = :userId AND t.entity_id = :entityId " +
                        "ORDER BY t.txn_date DESC, t.created_at DESC " +
                        "LIMIT 5",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("entityId", entityId),
                (rs, rowNum) -> TransactionListItemResponse.builder()
                        .id(rs.getLong("id"))
                        .txnDate(rs.getObject("txn_date", LocalDate.class))
                        .txnTime(rs.getObject("txn_time", java.time.LocalTime.class))
                        .entityId((Long) rs.getObject("entity_id"))
                        .entityName(rs.getString("entity_name"))
                        .categoryId((Long) rs.getObject("category_id"))
                        .categoryName(rs.getString("category_name"))
                        .amount(rs.getBigDecimal("amount"))
                        .txnType(rs.getString("txn_type"))
                        .rawDetails(rs.getString("raw_details"))
                        .source(rs.getString("source"))
                        .externalTxnId(rs.getString("external_txn_id"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build()
        );
    }

    private String buildWhere(MapSqlParameterSource parameters, Long entityId, Long categoryId) {
        StringBuilder where = new StringBuilder("r.user_id = :userId");

        if (entityId != null) {
            where.append(" AND r.entity_id = :entityId");
            parameters.addValue("entityId", entityId);
        }

        if (categoryId != null) {
            where.append(" AND r.category_id = :categoryId");
            parameters.addValue("categoryId", categoryId);
        }

        return where.toString();
    }

    private record SummaryCounts(Long totalCount, Long dueSoonCount) {
    }
}
