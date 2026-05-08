package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.FilterOptionResponse;
import com.saptarshi.finogpt.dto.PagedResponse;
import com.saptarshi.finogpt.dto.TransactionDetailResponse;
import com.saptarshi.finogpt.dto.TransactionFiltersResponse;
import com.saptarshi.finogpt.dto.TransactionListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionReadService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PagedResponse<TransactionListItemResponse> listTransactions(Long userId,
                                                                       LocalDate from,
                                                                       LocalDate to,
                                                                       String type,
                                                                       Long entityId,
                                                                       Long categoryId,
                                                                       int page,
                                                                       int size,
                                                                       String sortBy,
                                                                       String sortDir) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from date must be on or before to date");
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", safeSize)
                .addValue("offset", safePage * safeSize);

        String where = buildTransactionWhere(parameters, from, to, type, entityId, categoryId);
        String orderBy = buildOrderBy(sortBy, sortDir);

        List<TransactionListItemResponse> items = jdbcTemplate.query(
                "SELECT t.id, t.txn_date, t.txn_time, t.entity_id, e.name AS entity_name, " +
                        "COALESCE(t.user_category_id, t.category_id) AS category_id, c.name AS category_name, t.amount, t.txn_type, " +
                        "t.raw_details, t.source, t.external_txn_id, t.created_at " +
                        "FROM transactions t " +
                        "LEFT JOIN entities e ON e.id = t.entity_id " +
                        "LEFT JOIN categories c ON c.id = COALESCE(t.user_category_id, t.category_id) " +
                        "WHERE " + where + " " +
                        "ORDER BY " + orderBy + " " +
                        "LIMIT :limit OFFSET :offset",
                parameters,
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

        Long totalItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions t WHERE " + where,
                parameters,
                Long.class
        );

        long total = totalItems == null ? 0L : totalItems;
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) safeSize);

        return PagedResponse.<TransactionListItemResponse>builder()
                .items(items)
                .page(safePage)
                .size(safeSize)
                .totalItems(total)
                .totalPages(totalPages)
                .build();
    }

    public TransactionDetailResponse getTransaction(Long userId, Long txnId) {
        List<TransactionDetailResponse> results = jdbcTemplate.query(
                "SELECT t.id, t.txn_date, t.txn_time, t.entity_id, e.name AS entity_name, " +
                        "COALESCE(t.user_category_id, t.category_id) AS category_id, c.name AS category_name, t.user_category_id, t.amount, t.txn_type, " +
                        "t.raw_details, t.source, t.external_txn_id, t.ingestion_job_id, t.created_at " +
                        "FROM transactions t " +
                        "LEFT JOIN entities e ON e.id = t.entity_id " +
                        "LEFT JOIN categories c ON c.id = COALESCE(t.user_category_id, t.category_id) " +
                        "WHERE t.user_id = :userId AND t.id = :txnId",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("txnId", txnId),
                (rs, rowNum) -> TransactionDetailResponse.builder()
                        .id(rs.getLong("id"))
                        .txnDate(rs.getObject("txn_date", LocalDate.class))
                        .txnTime(rs.getObject("txn_time", java.time.LocalTime.class))
                        .entityId((Long) rs.getObject("entity_id"))
                        .entityName(rs.getString("entity_name"))
                        .categoryId((Long) rs.getObject("category_id"))
                        .categoryName(rs.getString("category_name"))
                        .userCategoryId((Long) rs.getObject("user_category_id"))
                        .amount(rs.getBigDecimal("amount"))
                        .txnType(rs.getString("txn_type"))
                        .rawDetails(rs.getString("raw_details"))
                        .source(rs.getString("source"))
                        .externalTxnId(rs.getString("external_txn_id"))
                        .ingestionJobId((java.util.UUID) rs.getObject("ingestion_job_id"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build()
        );

        if (results.isEmpty()) {
            throw new IllegalArgumentException("Transaction not found");
        }

        return results.get(0);
    }

    public TransactionFiltersResponse getFilters(Long userId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId);

        LocalDate minDate = jdbcTemplate.queryForObject(
                "SELECT MIN(t.txn_date) FROM transactions t WHERE t.user_id = :userId",
                parameters,
                (rs, rowNum) -> rs.getObject(1, LocalDate.class)
        );

        LocalDate maxDate = jdbcTemplate.queryForObject(
                "SELECT MAX(t.txn_date) FROM transactions t WHERE t.user_id = :userId",
                parameters,
                (rs, rowNum) -> rs.getObject(1, LocalDate.class)
        );

        List<FilterOptionResponse> entities = jdbcTemplate.query(
                "SELECT t.entity_id AS id, e.name " +
                        "FROM transactions t " +
                        "JOIN entities e ON e.id = t.entity_id " +
                        "WHERE t.user_id = :userId AND t.entity_id IS NOT NULL " +
                        "GROUP BY t.entity_id, e.name " +
                        "ORDER BY e.name",
                parameters,
                (rs, rowNum) -> FilterOptionResponse.builder()
                        .id(rs.getLong("id"))
                        .name(rs.getString("name"))
                        .build()
        );

        List<FilterOptionResponse> categories = jdbcTemplate.query(
                "SELECT COALESCE(t.user_category_id, t.category_id) AS id, c.name " +
                        "FROM transactions t " +
                        "JOIN categories c ON c.id = COALESCE(t.user_category_id, t.category_id) " +
                        "WHERE t.user_id = :userId AND COALESCE(t.user_category_id, t.category_id) IS NOT NULL " +
                        "GROUP BY COALESCE(t.user_category_id, t.category_id), c.name " +
                        "ORDER BY c.name",
                parameters,
                (rs, rowNum) -> FilterOptionResponse.builder()
                        .id(rs.getLong("id"))
                        .name(rs.getString("name"))
                        .build()
        );

        return TransactionFiltersResponse.builder()
                .minDate(minDate)
                .maxDate(maxDate)
                .entities(entities)
                .categories(categories)
                .types(List.of("DEBIT", "CREDIT"))
                .build();
    }

    private String buildTransactionWhere(MapSqlParameterSource parameters,
                                         LocalDate from,
                                         LocalDate to,
                                         String type,
                                         Long entityId,
                                         Long categoryId) {
        StringBuilder where = new StringBuilder("t.user_id = :userId");

        if (from != null) {
            where.append(" AND t.txn_date >= :fromDate");
            parameters.addValue("fromDate", from);
        }

        if (to != null) {
            where.append(" AND t.txn_date <= :toDate");
            parameters.addValue("toDate", to);
        }

        if (type != null && !type.isBlank()) {
            String normalizedType = type.trim().toUpperCase();
            if (!List.of("DEBIT", "CREDIT").contains(normalizedType)) {
                throw new IllegalArgumentException("Unsupported transaction type filter");
            }
            where.append(" AND t.txn_type = :txnType");
            parameters.addValue("txnType", normalizedType);
        }

        if (entityId != null) {
            where.append(" AND t.entity_id = :entityId");
            parameters.addValue("entityId", entityId);
        }

        if (categoryId != null) {
            where.append(" AND COALESCE(t.user_category_id, t.category_id) = :categoryId");
            parameters.addValue("categoryId", categoryId);
        }

        return where.toString();
    }

    private String buildOrderBy(String sortBy, String sortDir) {
        String normalizedSortBy = sortBy == null ? "txnDate" : sortBy;
        String column;
        if ("amount".equals(normalizedSortBy)) {
            column = "t.amount";
        } else if ("createdAt".equals(normalizedSortBy)) {
            column = "t.created_at";
        } else {
            column = "t.txn_date";
        }

        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        if ("t.txn_date".equals(column)) {
            return column + " " + direction + ", t.created_at " + direction;
        }
        return column + " " + direction + ", t.txn_date DESC";
    }
}
