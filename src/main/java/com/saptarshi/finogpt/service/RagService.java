package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.QueryContext;
import com.saptarshi.finogpt.dto.QueryExecutionResult;
import com.saptarshi.finogpt.enums.QueryExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private static final int DEFAULT_RESULT_LIMIT = 20;

    private final ResolverService resolverService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final QueryDecisionService queryDecisionService;

    public String search(Long userId, String query) {
        return executeSearch(userId, query).getAnswer();
    }

    public QueryExecutionResult executeSearch(Long userId, String query) {
        QueryContext ctx = resolverService.resolve(userId, query);
        return executeResolvedSearch(query, ctx);
    }

    public QueryExecutionResult executeResolvedSearch(String query, QueryContext ctx) {
        if (queryDecisionService.requiresClarification(ctx)) {
            return new QueryExecutionResult(
                    queryDecisionService.buildClarificationQuestion(ctx),
                    List.of(),
                    ctx,
                    QueryExecutionStatus.CLARIFICATION_REQUIRED
            );
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        List<String> whereClauses = new ArrayList<>();

        parameters.put("userId",  ctx.getUserId());
        parameters.put("limit", safeLimit(ctx.getLimit()));
        whereClauses.add("t.user_id = :userId");
        whereClauses.addAll(buildTimeClauses(ctx, parameters));
        whereClauses.addAll(buildFilterClauses(ctx, parameters));
        whereClauses.addAll(buildPhraseClauses(ctx, parameters));

        String sql = "SELECT t.id, t.txn_date, t.txn_time, t.amount, t.txn_type, " +
                "e.name AS entity_name, " +
                "COALESCE(uc.name, c.name) AS category_name, " +
                "t.raw_details, t.source " +
                "FROM transactions t " +
                "LEFT JOIN entities e ON e.id = t.entity_id " +
                "LEFT JOIN categories c ON c.id = t.category_id " +
                "LEFT JOIN categories uc ON uc.id = t.user_category_id " +
                "WHERE " + String.join(" AND ", whereClauses) + " " +
                "ORDER BY t.txn_date DESC, t.txn_time DESC NULLS LAST, t.id DESC " +
                "LIMIT :limit";

        log.info("Executing transaction search SQL: {} with params {}", sql, parameters);

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
            return new QueryExecutionResult(buildAnswer(ctx, rows), rows, ctx, QueryExecutionStatus.EXECUTED);
        } catch (DataAccessException ex) {
            log.error("Transaction search failed for context {}", ctx, ex);
            return new QueryExecutionResult("I could not execute that transaction search.", List.of(), ctx, QueryExecutionStatus.FAILED);
        }
    }

    private List<String> buildTimeClauses(QueryContext ctx, Map<String, Object> parameters) {
        List<String> clauses = new ArrayList<>();

        if (ctx.isToday()) {
            parameters.put("targetDate", LocalDate.now());
            clauses.add("t.txn_date = :targetDate");
            return clauses;
        }

        if (ctx.isYesterday()) {
            parameters.put("targetDate", LocalDate.now().minusDays(1));
            clauses.add("t.txn_date = :targetDate");
            return clauses;
        }

        if (ctx.getDay() != null && ctx.getMonth() != null && ctx.getYear() != null) {
            parameters.put("targetDate", LocalDate.of(ctx.getYear(), ctx.getMonth(), ctx.getDay()));
            clauses.add("t.txn_date = :targetDate");
            return clauses;
        }

        if (ctx.getLastNMonths() != null) {
            int lastNMonths = Math.max(ctx.getLastNMonths(), 1);
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.withDayOfMonth(1).minusMonths(lastNMonths - 1L);
            parameters.put("startDate", startDate);
            parameters.put("endDate", endDate);
            clauses.add("t.txn_date BETWEEN :startDate AND :endDate");
            return clauses;
        }

        if (ctx.getMonth() != null && ctx.getYear() != null) {
            LocalDate startDate = LocalDate.of(ctx.getYear(), ctx.getMonth(), 1);
            LocalDate endDate = startDate.plusMonths(1).minusDays(1);
            parameters.put("startDate", startDate);
            parameters.put("endDate", endDate);
            clauses.add("t.txn_date BETWEEN :startDate AND :endDate");
        }

        return clauses;
    }

    private List<String> buildFilterClauses(QueryContext ctx, Map<String, Object> parameters) {
        List<String> clauses = new ArrayList<>();

        if (ctx.getEntityIds() != null && !ctx.getEntityIds().isEmpty()) {
            parameters.put("entityIds", ctx.getEntityIds());
            clauses.add("t.entity_id IN (:entityIds)");
        }

        if (ctx.getCategoryIds() != null && !ctx.getCategoryIds().isEmpty()) {
            parameters.put("categoryIds", ctx.getCategoryIds());
            clauses.add("COALESCE(t.user_category_id, t.category_id) IN (:categoryIds)");
        }

        return clauses;
    }

    private List<String> buildPhraseClauses(QueryContext ctx, Map<String, Object> parameters) {
        List<String> clauses = new ArrayList<>();
        if (!hasStrongFilter(ctx) && ctx.getSearchPhrase() != null && !ctx.getSearchPhrase().isBlank()) {
            parameters.put("searchPhrase", "%" + ctx.getSearchPhrase().toLowerCase() + "%");
            clauses.add("(" +
                    "LOWER(COALESCE(e.name, '')) LIKE :searchPhrase " +
                    "OR LOWER(COALESCE(c.name, '')) LIKE :searchPhrase " +
                    "OR LOWER(COALESCE(uc.name, '')) LIKE :searchPhrase " +
                    "OR LOWER(COALESCE(t.raw_details, '')) LIKE :searchPhrase" +
                    ")");
        }
        return clauses;
    }

    private boolean hasStrongFilter(QueryContext ctx) {
        return (ctx.getEntityIds() != null && !ctx.getEntityIds().isEmpty())
                || (ctx.getCategoryIds() != null && !ctx.getCategoryIds().isEmpty());
    }

    private int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_RESULT_LIMIT;
        }
        return Math.min(limit, DEFAULT_RESULT_LIMIT);
    }

    private String buildAnswer(QueryContext ctx, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "No matching transactions found.";
        }

        if (ctx.getEntityNames() != null && !ctx.getEntityNames().isEmpty()) {
            return "Found " + rows.size() + " matching transactions for " + String.join(", ", ctx.getEntityNames()) + ".";
        }

        if (ctx.getCategoryNames() != null && !ctx.getCategoryNames().isEmpty()) {
            return "Found " + rows.size() + " matching transactions in " + String.join(", ", ctx.getCategoryNames()) + ".";
        }

        if (ctx.getSearchPhrase() != null && !ctx.getSearchPhrase().isBlank()) {
            return "Found " + rows.size() + " matching transactions for \"" + ctx.getSearchPhrase() + "\".";
        }

        return "Showing " + rows.size() + " recent matching transactions.";
    }
}
