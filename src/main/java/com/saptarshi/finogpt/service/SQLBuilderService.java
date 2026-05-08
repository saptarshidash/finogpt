package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.QueryContext;
import com.saptarshi.finogpt.dto.SqlQueryPlan;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SQLBuilderService {

    public SqlQueryPlan buildPlan(QueryContext ctx) {
        TableSpec tableSpec = resolveTable(ctx);
        validateSupportedCombination(ctx, tableSpec);
        Map<String, Object> parameters = new LinkedHashMap<>();
        List<String> whereClauses = new ArrayList<>();

        parameters.put("userId", ctx.getUserId());
        whereClauses.add(tableSpec.alias + ".user_id = :userId");
        whereClauses.addAll(buildTimeClauses(ctx, tableSpec, parameters));
        whereClauses.addAll(buildFilterClauses(ctx, tableSpec, parameters));

        String sql;
        if (tableSpec.kind == TableKind.DAILY) {
            sql = buildDailySql(ctx, tableSpec, whereClauses);
        } else if (tableSpec.kind == TableKind.MONTHLY) {
            sql = buildMonthlySql(ctx, tableSpec, whereClauses);
        } else if (tableSpec.kind == TableKind.RAW_TXN) {
            sql = buildRawTransactionSql(ctx, tableSpec, whereClauses);
        } else if (tableSpec.kind == TableKind.ENTITY || tableSpec.kind == TableKind.RAW_ENTITY) {
            sql = buildEntitySql(ctx, tableSpec, whereClauses, parameters);
        } else {
            sql = buildCategorySql(ctx, tableSpec, whereClauses, parameters);
        }

        return new SqlQueryPlan(sql, parameters);
    }

    public String build(QueryContext ctx) {
        return buildPlan(ctx).getSql();
    }

    private String buildDailySql(QueryContext ctx, TableSpec tableSpec, List<String> whereClauses) {
        String intent = normalizeIntent(ctx.getIntent());
        String measure;
        if ("COUNT".equals(intent)) {
            measure = "COALESCE(SUM(" + tableSpec.alias + ".txn_count), 0) AS txn_count";
        } else if ("AVG".equals(intent)) {
            measure = "COALESCE(SUM(" + dailyAmountColumn(ctx, tableSpec.alias) + ") / NULLIF(SUM(" + tableSpec.alias + ".txn_count), 0), 0) AS avg_amount";
        } else {
            measure = "COALESCE(SUM(" + dailyAmountColumn(ctx, tableSpec.alias) + "), 0) AS total_amount";
        }

        return "SELECT " + measure + " FROM " + tableSpec.table + " " + tableSpec.alias +
                " WHERE " + joinWhere(whereClauses);
    }

    private String buildMonthlySql(QueryContext ctx, TableSpec tableSpec, List<String> whereClauses) {
        if (isTopIntent(ctx)) {
            throw new IllegalArgumentException("TOP queries require entity or category dimension");
        }

        String intent = normalizeIntent(ctx.getIntent());
        String measure;
        if ("COUNT".equals(intent)) {
            measure = "COALESCE(SUM(" + tableSpec.alias + ".txn_count), 0) AS txn_count";
        } else if ("AVG".equals(intent)) {
            measure = "COALESCE(SUM(" + monthlyAmountColumn(ctx, tableSpec.alias) + ") / NULLIF(SUM(" + tableSpec.alias + ".txn_count), 0), 0) AS avg_amount";
        } else {
            measure = "COALESCE(SUM(" + monthlyAmountColumn(ctx, tableSpec.alias) + "), 0) AS total_amount";
        }

        return "SELECT " + measure + " FROM " + tableSpec.table + " " + tableSpec.alias +
                " WHERE " + joinWhere(whereClauses);
    }

    private String buildEntitySql(QueryContext ctx,
                                  TableSpec tableSpec,
                                  List<String> whereClauses,
                                  Map<String, Object> parameters) {
        if (tableSpec.kind == TableKind.RAW_ENTITY) {
            return buildRawEntitySql(ctx, tableSpec, whereClauses, parameters);
        }

        if (isTopIntent(ctx)) {
            parameters.put("limit", safeLimit(ctx.getLimit()));
            return "SELECT " + tableSpec.alias + ".entity_id, e.name AS entity_name, " +
                    "COALESCE(SUM(" + tableSpec.alias + ".total_amount), 0) AS total_amount, " +
                    "COALESCE(SUM(" + tableSpec.alias + ".txn_count), 0) AS txn_count " +
                    "FROM " + tableSpec.table + " " + tableSpec.alias + " " +
                    "LEFT JOIN entities e ON e.id = " + tableSpec.alias + ".entity_id " +
                    "WHERE " + joinWhere(whereClauses) + " " +
                    "GROUP BY " + tableSpec.alias + ".entity_id, e.name " +
                    "ORDER BY total_amount DESC " +
                    "LIMIT :limit";
        }

        String intent = normalizeIntent(ctx.getIntent());
        String measure;
        if ("COUNT".equals(intent)) {
            measure = "COALESCE(SUM(" + tableSpec.alias + ".txn_count), 0) AS txn_count";
        } else if ("AVG".equals(intent)) {
            measure = "COALESCE(SUM(" + tableSpec.alias + ".total_amount) / NULLIF(SUM(" + tableSpec.alias + ".txn_count), 0), 0) AS avg_amount";
        } else {
            measure = "COALESCE(SUM(" + tableSpec.alias + ".total_amount), 0) AS total_amount";
        }

        return "SELECT " + measure + " FROM " + tableSpec.table + " " + tableSpec.alias +
                " WHERE " + joinWhere(whereClauses);
    }

    private String buildCategorySql(QueryContext ctx,
                                    TableSpec tableSpec,
                                    List<String> whereClauses,
                                    Map<String, Object> parameters) {
        if (tableSpec.kind == TableKind.RAW_CATEGORY) {
            return buildRawCategorySql(ctx, tableSpec, whereClauses, parameters);
        }

        if (isTopIntent(ctx)) {
            parameters.put("limit", safeLimit(ctx.getLimit()));
            return "SELECT " + tableSpec.alias + ".category_id, c.name AS category_name, " +
                    "COALESCE(SUM(" + tableSpec.alias + ".total_amount), 0) AS total_amount, " +
                    "COALESCE(SUM(" + tableSpec.alias + ".txn_count), 0) AS txn_count " +
                    "FROM " + tableSpec.table + " " + tableSpec.alias + " " +
                    "LEFT JOIN categories c ON c.id = " + tableSpec.alias + ".category_id " +
                    "WHERE " + joinWhere(whereClauses) + " " +
                    "GROUP BY " + tableSpec.alias + ".category_id, c.name " +
                    "ORDER BY total_amount DESC " +
                    "LIMIT :limit";
        }

        String intent = normalizeIntent(ctx.getIntent());
        String measure;
        if ("COUNT".equals(intent)) {
            measure = "COALESCE(SUM(" + tableSpec.alias + ".txn_count), 0) AS txn_count";
        } else if ("AVG".equals(intent)) {
            measure = "COALESCE(SUM(" + tableSpec.alias + ".total_amount) / NULLIF(SUM(" + tableSpec.alias + ".txn_count), 0), 0) AS avg_amount";
        } else {
            measure = "COALESCE(SUM(" + tableSpec.alias + ".total_amount), 0) AS total_amount";
        }

        return "SELECT " + measure + " FROM " + tableSpec.table + " " + tableSpec.alias +
                " WHERE " + joinWhere(whereClauses);
    }

    private String buildRawEntitySql(QueryContext ctx,
                                     TableSpec tableSpec,
                                     List<String> whereClauses,
                                     Map<String, Object> parameters) {
        if (isTopIntent(ctx)) {
            parameters.put("limit", safeLimit(ctx.getLimit()));
            return "SELECT t.entity_id, e.name AS entity_name, " +
                    "COALESCE(SUM(t.amount), 0) AS total_amount, " +
                    "COUNT(*) AS txn_count " +
                    "FROM transactions t " +
                    "LEFT JOIN entities e ON e.id = t.entity_id " +
                    "WHERE " + joinWhere(whereClauses) + " " +
                    "GROUP BY t.entity_id, e.name " +
                    "ORDER BY total_amount DESC " +
                    "LIMIT :limit";
        }

        String intent = normalizeIntent(ctx.getIntent());
        String measure;
        if ("COUNT".equals(intent)) {
            measure = "COUNT(*) AS txn_count";
        } else if ("AVG".equals(intent)) {
            measure = "COALESCE(AVG(t.amount), 0) AS avg_amount";
        } else {
            measure = "COALESCE(SUM(t.amount), 0) AS total_amount";
        }

        return "SELECT " + measure + " FROM transactions t WHERE " + joinWhere(whereClauses);
    }

    private String buildRawCategorySql(QueryContext ctx,
                                       TableSpec tableSpec,
                                       List<String> whereClauses,
                                       Map<String, Object> parameters) {
        if (isTopIntent(ctx)) {
            parameters.put("limit", safeLimit(ctx.getLimit()));
            return "SELECT COALESCE(t.user_category_id, t.category_id) AS category_id, c.name AS category_name, " +
                    "COALESCE(SUM(t.amount), 0) AS total_amount, " +
                    "COUNT(*) AS txn_count " +
                    "FROM transactions t " +
                    "LEFT JOIN categories c ON c.id = COALESCE(t.user_category_id, t.category_id) " +
                    "WHERE " + joinWhere(whereClauses) + " " +
                    "GROUP BY COALESCE(t.user_category_id, t.category_id), c.name " +
                    "ORDER BY total_amount DESC " +
                    "LIMIT :limit";
        }

        String intent = normalizeIntent(ctx.getIntent());
        String measure;
        if ("COUNT".equals(intent)) {
            measure = "COUNT(*) AS txn_count";
        } else if ("AVG".equals(intent)) {
            measure = "COALESCE(AVG(t.amount), 0) AS avg_amount";
        } else {
            measure = "COALESCE(SUM(t.amount), 0) AS total_amount";
        }

        return "SELECT " + measure + " FROM transactions t WHERE " + joinWhere(whereClauses);
    }

    private String buildRawTransactionSql(QueryContext ctx,
                                          TableSpec tableSpec,
                                          List<String> whereClauses) {
        String intent = normalizeIntent(ctx.getIntent());
        String requestedDimension = normalizeDimension(ctx.getRequestedDimension());
        boolean hasEntityFilter = ctx.getEntityIds() != null && !ctx.getEntityIds().isEmpty();
        boolean hasCategoryFilter = ctx.getCategoryIds() != null && !ctx.getCategoryIds().isEmpty();

        if ("LIST".equals(intent)) {
            return buildTransactionListSql(ctx, whereClauses);
        }

        if (isTopIntent(ctx)) {
            return buildTopTransactionsSql(ctx, whereClauses);
        }

        if (("MAX".equals(intent) || "MIN".equals(intent))
                && "ENTITY".equals(requestedDimension)
                && !hasEntityFilter
                && !hasCategoryFilter) {
            return buildRawEntityExtremaSql(intent, whereClauses);
        }

        if (("MAX".equals(intent) || "MIN".equals(intent))
                && "CATEGORY".equals(requestedDimension)
                && !hasEntityFilter
                && !hasCategoryFilter) {
            return buildRawCategoryExtremaSql(intent, whereClauses);
        }

        String measure;
        if ("COUNT".equals(intent)) {
            measure = "COUNT(*) AS txn_count";
        } else if ("AVG".equals(intent)) {
            measure = "COALESCE(AVG(" + tableSpec.alias + ".amount), 0) AS avg_amount";
        } else if ("MAX".equals(intent)) {
            measure = "COALESCE(MAX(" + tableSpec.alias + ".amount), 0) AS max_amount";
        } else if ("MIN".equals(intent)) {
            measure = "COALESCE(MIN(" + tableSpec.alias + ".amount), 0) AS min_amount";
        } else {
            measure = "COALESCE(SUM(" + tableSpec.alias + ".amount), 0) AS total_amount";
        }

        return "SELECT " + measure + " FROM " + tableSpec.table + " " + tableSpec.alias +
                " WHERE " + joinWhere(whereClauses);
    }

    private String buildTransactionListSql(QueryContext ctx, List<String> whereClauses) {
        int limit = safeListLimit(ctx.getLimit());
        return "SELECT t.id, t.txn_date, t.txn_time, t.entity_id, e.name AS entity_name, " +
                "COALESCE(t.user_category_id, t.category_id) AS category_id, c.name AS category_name, " +
                "t.amount, t.txn_type, t.raw_details, t.source, t.external_txn_id " +
                "FROM transactions t " +
                "LEFT JOIN entities e ON e.id = t.entity_id " +
                "LEFT JOIN categories c ON c.id = COALESCE(t.user_category_id, t.category_id) " +
                "WHERE " + joinWhere(whereClauses) + " " +
                "ORDER BY t.txn_date DESC, t.txn_time DESC NULLS LAST, t.id DESC " +
                "LIMIT " + limit;
    }

    private String buildTopTransactionsSql(QueryContext ctx, List<String> whereClauses) {
        int limit = safeLimit(ctx.getLimit());
        return "SELECT t.id, t.txn_date, t.txn_time, t.entity_id, e.name AS entity_name, " +
                "COALESCE(t.user_category_id, t.category_id) AS category_id, c.name AS category_name, " +
                "t.amount, t.txn_type, t.raw_details, t.source, t.external_txn_id " +
                "FROM transactions t " +
                "LEFT JOIN entities e ON e.id = t.entity_id " +
                "LEFT JOIN categories c ON c.id = COALESCE(t.user_category_id, t.category_id) " +
                "WHERE " + joinWhere(whereClauses) + " " +
                "ORDER BY t.amount DESC, t.txn_date DESC, t.id DESC " +
                "LIMIT " + limit;
    }

    private String buildRawEntityExtremaSql(String intent, List<String> whereClauses) {
        String orderDirection = "MAX".equals(intent) ? "DESC" : "ASC";
        return "SELECT t.entity_id, e.name AS entity_name, t.amount AS amount, t.txn_date " +
                "FROM transactions t " +
                "LEFT JOIN entities e ON e.id = t.entity_id " +
                "WHERE " + joinWhere(whereClauses) + " " +
                "ORDER BY t.amount " + orderDirection + ", t.txn_date DESC " +
                "LIMIT 1";
    }

    private String buildRawCategoryExtremaSql(String intent, List<String> whereClauses) {
        String orderDirection = "MAX".equals(intent) ? "DESC" : "ASC";
        return "SELECT COALESCE(t.user_category_id, t.category_id) AS category_id, c.name AS category_name, t.amount AS amount, t.txn_date " +
                "FROM transactions t " +
                "LEFT JOIN categories c ON c.id = COALESCE(t.user_category_id, t.category_id) " +
                "WHERE " + joinWhere(whereClauses) + " " +
                "ORDER BY t.amount " + orderDirection + ", t.txn_date DESC " +
                "LIMIT 1";
    }

    private TableSpec resolveTable(QueryContext ctx) {
        boolean dailyQuery = isDailyQuery(ctx);
        boolean hasEntityFilter = ctx.getEntityIds() != null && !ctx.getEntityIds().isEmpty();
        boolean hasCategoryFilter = ctx.getCategoryIds() != null && !ctx.getCategoryIds().isEmpty();
        boolean entityDimensionQuery = "ENTITY".equals(ctx.getResolutionMode())
                || (isTopIntent(ctx) && "ENTITY".equals(ctx.getRequestedDimension()));
        boolean categoryDimensionQuery = "CATEGORY".equals(ctx.getResolutionMode())
                || (isTopIntent(ctx) && "CATEGORY".equals(ctx.getRequestedDimension()));
        String txnDirection = normalizeDirection(ctx.getTxnDirection());
        String intent = normalizeIntent(ctx.getIntent());
        String requestedDimension = normalizeDimension(ctx.getRequestedDimension());
        boolean exactDateRange = hasExactDateRange(ctx);

        if ("LIST".equals(intent) || "MAX".equals(intent) || "MIN".equals(intent)) {
            return new TableSpec(TableKind.RAW_TXN, "transactions", "t");
        }

        if (isTopIntent(ctx) && "NONE".equals(requestedDimension)) {
            return new TableSpec(TableKind.RAW_TXN, "transactions", "t");
        }

        if (exactDateRange) {
            if (hasEntityFilter || entityDimensionQuery) {
                return new TableSpec(TableKind.RAW_ENTITY, "transactions", "t");
            }
            if (hasCategoryFilter || categoryDimensionQuery) {
                return new TableSpec(TableKind.RAW_CATEGORY, "transactions", "t");
            }
            return new TableSpec(TableKind.DAILY, "daily_summary", "ds");
        }

        if (dailyQuery) {
            if (hasEntityFilter || entityDimensionQuery) {
                return new TableSpec(TableKind.RAW_ENTITY, "transactions", "t");
            }
            if (hasCategoryFilter || categoryDimensionQuery) {
                return new TableSpec(TableKind.RAW_CATEGORY, "transactions", "t");
            }
            return new TableSpec(TableKind.DAILY, "daily_summary", "ds");
        }

        if (hasEntityFilter || entityDimensionQuery) {
            if ("CREDIT".equals(txnDirection)) {
                return new TableSpec(TableKind.RAW_ENTITY, "transactions", "t");
            }
            return new TableSpec(TableKind.ENTITY, "entity_summary", "es");
        }

        if (hasCategoryFilter || categoryDimensionQuery) {
            if ("CREDIT".equals(txnDirection)) {
                return new TableSpec(TableKind.RAW_CATEGORY, "transactions", "t");
            }
            return new TableSpec(TableKind.CATEGORY, "category_summary", "cs");
        }

        return new TableSpec(TableKind.MONTHLY, "monthly_summary", "ms");
    }

    private List<String> buildTimeClauses(QueryContext ctx,
                                          TableSpec tableSpec,
                                          Map<String, Object> parameters) {
        List<String> clauses = new ArrayList<>();

        if (tableSpec.kind == TableKind.RAW_ENTITY
                || tableSpec.kind == TableKind.RAW_CATEGORY
                || tableSpec.kind == TableKind.RAW_TXN) {
            return buildRawTimeClauses(ctx, tableSpec.alias, parameters);
        }

        if (tableSpec.kind == TableKind.DAILY) {
            return buildDailyTimeClauses(ctx, tableSpec.alias, parameters);
        }

        if (ctx.getLastNMonths() != null) {
            int lastNMonths = Math.max(ctx.getLastNMonths(), 1);
            LocalDate end = LocalDate.now().withDayOfMonth(1);
            LocalDate start = end.minusMonths(lastNMonths - 1L);
            int startYm = start.getYear() * 100 + start.getMonthValue();
            int endYm = end.getYear() * 100 + end.getMonthValue();
            parameters.put("startYm", startYm);
            parameters.put("endYm", endYm);
            clauses.add("(" + tableSpec.alias + ".year * 100 + " + tableSpec.alias + ".month) BETWEEN :startYm AND :endYm");
            return clauses;
        }

        if (ctx.getMonth() != null) {
            parameters.put("year", ctx.getYear());
            parameters.put("month", ctx.getMonth());
            clauses.add(tableSpec.alias + ".year = :year");
            clauses.add(tableSpec.alias + ".month = :month");
            return clauses;
        }

        if (ctx.isYearExplicit()) {
            parameters.put("year", ctx.getYear());
            clauses.add(tableSpec.alias + ".year = :year");
        }

        return clauses;
    }

    private List<String> buildDailyTimeClauses(QueryContext ctx,
                                               String alias,
                                               Map<String, Object> parameters) {
        List<String> clauses = new ArrayList<>();

        if (hasExactDateRange(ctx)) {
            parameters.put("fromDate", ctx.getFromDate());
            parameters.put("toDate", ctx.getToDate());
            clauses.add(alias + ".date BETWEEN :fromDate AND :toDate");
            return clauses;
        }

        LocalDate targetDate = resolveDailyDate(ctx);
        if (targetDate != null) {
            parameters.put("targetDate", targetDate);
            clauses.add(alias + ".date = :targetDate");
            return clauses;
        }

        LocalDate[] range = resolveRelativeWeekRange(ctx);
        if (range != null) {
            parameters.put("fromDate", range[0]);
            parameters.put("toDate", range[1]);
            clauses.add(alias + ".date BETWEEN :fromDate AND :toDate");
        }

        return clauses;
    }

    private List<String> buildRawTimeClauses(QueryContext ctx,
                                             String alias,
                                             Map<String, Object> parameters) {
        List<String> clauses = new ArrayList<>();

        if (hasExactDateRange(ctx)) {
            parameters.put("fromDate", ctx.getFromDate());
            parameters.put("toDate", ctx.getToDate());
            clauses.add(alias + ".txn_date BETWEEN :fromDate AND :toDate");
            return clauses;
        }

        LocalDate targetDate = resolveDailyDate(ctx);
        if (targetDate != null) {
            parameters.put("fromDate", targetDate);
            parameters.put("toDate", targetDate);
            clauses.add(alias + ".txn_date BETWEEN :fromDate AND :toDate");
            return clauses;
        }

        LocalDate[] range = resolveRelativeWeekRange(ctx);
        if (range != null) {
            parameters.put("fromDate", range[0]);
            parameters.put("toDate", range[1]);
            clauses.add(alias + ".txn_date BETWEEN :fromDate AND :toDate");
            return clauses;
        }

        if (ctx.getLastNMonths() != null) {
            int lastNMonths = Math.max(ctx.getLastNMonths(), 1);
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.withDayOfMonth(1).minusMonths(lastNMonths - 1L);
            parameters.put("fromDate", startDate);
            parameters.put("toDate", endDate);
            clauses.add(alias + ".txn_date BETWEEN :fromDate AND :toDate");
            return clauses;
        }

        if (ctx.getMonth() != null) {
            LocalDate startDate = LocalDate.of(ctx.getYear(), ctx.getMonth(), 1);
            LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            parameters.put("fromDate", startDate);
            parameters.put("toDate", endDate);
            clauses.add(alias + ".txn_date BETWEEN :fromDate AND :toDate");
            return clauses;
        }

        if (ctx.isYearExplicit()) {
            LocalDate startDate = LocalDate.of(ctx.getYear(), 1, 1);
            LocalDate endDate = LocalDate.of(ctx.getYear(), 12, 31);
            parameters.put("fromDate", startDate);
            parameters.put("toDate", endDate);
            clauses.add(alias + ".txn_date BETWEEN :fromDate AND :toDate");
        }

        return clauses;
    }

    private List<String> buildFilterClauses(QueryContext ctx,
                                            TableSpec tableSpec,
                                            Map<String, Object> parameters) {
        List<String> clauses = new ArrayList<>();

        if (tableSpec.kind == TableKind.RAW_ENTITY
                || tableSpec.kind == TableKind.RAW_CATEGORY
                || tableSpec.kind == TableKind.RAW_TXN) {
            if ((tableSpec.kind == TableKind.RAW_ENTITY || tableSpec.kind == TableKind.RAW_TXN)
                    && ctx.getEntityIds() != null && !ctx.getEntityIds().isEmpty()) {
                parameters.put("entityIds", ctx.getEntityIds());
                clauses.add(tableSpec.alias + ".entity_id IN (:entityIds)");
            }

            if ((tableSpec.kind == TableKind.RAW_CATEGORY || tableSpec.kind == TableKind.RAW_TXN)
                    && ctx.getCategoryIds() != null && !ctx.getCategoryIds().isEmpty()) {
                parameters.put("categoryIds", ctx.getCategoryIds());
                clauses.add("COALESCE(" + tableSpec.alias + ".user_category_id, " + tableSpec.alias + ".category_id) IN (:categoryIds)");
            }

            String txnDirection = normalizeDirection(ctx.getTxnDirection());
            if (txnDirection != null) {
                parameters.put("txnType", txnDirection);
                clauses.add(tableSpec.alias + ".txn_type = :txnType");
            }

            if (tableSpec.kind == TableKind.RAW_ENTITY) {
                clauses.add(tableSpec.alias + ".entity_id IS NOT NULL");
            } else if (tableSpec.kind == TableKind.RAW_CATEGORY) {
                clauses.add("COALESCE(" + tableSpec.alias + ".user_category_id, " + tableSpec.alias + ".category_id) IS NOT NULL");
            } else {
                String requestedDimension = normalizeDimension(ctx.getRequestedDimension());
                if ("ENTITY".equals(requestedDimension) && (ctx.getEntityIds() == null || ctx.getEntityIds().isEmpty())) {
                    clauses.add(tableSpec.alias + ".entity_id IS NOT NULL");
                } else if ("CATEGORY".equals(requestedDimension) && (ctx.getCategoryIds() == null || ctx.getCategoryIds().isEmpty())) {
                    clauses.add("COALESCE(" + tableSpec.alias + ".user_category_id, " + tableSpec.alias + ".category_id) IS NOT NULL");
                }
            }

            return clauses;
        }

        if (tableSpec.kind == TableKind.ENTITY && ctx.getEntityIds() != null && !ctx.getEntityIds().isEmpty()) {
            parameters.put("entityIds", ctx.getEntityIds());
            clauses.add(tableSpec.alias + ".entity_id IN (:entityIds)");
        }

        if (tableSpec.kind == TableKind.CATEGORY && ctx.getCategoryIds() != null && !ctx.getCategoryIds().isEmpty()) {
            parameters.put("categoryIds", ctx.getCategoryIds());
            clauses.add(tableSpec.alias + ".category_id IN (:categoryIds)");
        }

        return clauses;
    }

    private LocalDate resolveDailyDate(QueryContext ctx) {
        if (ctx.isToday()) {
            return LocalDate.now();
        }

        if (ctx.isYesterday()) {
            return LocalDate.now().minusDays(1);
        }

        if (ctx.getDay() != null && ctx.getMonth() != null && ctx.getYear() != null) {
            return LocalDate.of(ctx.getYear(), ctx.getMonth(), ctx.getDay());
        }

        return null;
    }

    private boolean hasExactDateRange(QueryContext ctx) {
        return ctx.getFromDate() != null && ctx.getToDate() != null;
    }

    private boolean isDailyQuery(QueryContext ctx) {
        return ctx.isToday() || ctx.isYesterday() || ctx.getDay() != null
                || ctx.isThisWeek() || ctx.isLastWeek();
    }

    private LocalDate[] resolveRelativeWeekRange(QueryContext ctx) {
        LocalDate now = LocalDate.now();
        if (ctx.isThisWeek()) {
            LocalDate start = now.minusDays(now.getDayOfWeek().getValue() - 1L);
            return new LocalDate[]{start, now};
        }
        if (ctx.isLastWeek()) {
            LocalDate thisWeekStart = now.minusDays(now.getDayOfWeek().getValue() - 1L);
            LocalDate start = thisWeekStart.minusWeeks(1);
            LocalDate end = thisWeekStart.minusDays(1);
            return new LocalDate[]{start, end};
        }
        return null;
    }

    private boolean isTopIntent(QueryContext ctx) {
        return "TOP".equalsIgnoreCase(normalizeIntent(ctx.getIntent()));
    }

    private String normalizeIntent(String intent) {
        return intent == null ? "SUM" : intent.toUpperCase();
    }

    private void validateSupportedCombination(QueryContext ctx, TableSpec tableSpec) {
        String intent = normalizeIntent(ctx.getIntent());
        String txnDirection = normalizeDirection(ctx.getTxnDirection());
        boolean unresolvedFilter = hasRequestedFilter(ctx) && !hasResolvedFilter(ctx);

        if ("UNKNOWN".equals(intent)) {
            throw new IllegalArgumentException("Row-level transaction listing is not supported by the analytics summary path.");
        }

        if (unresolvedFilter) {
            throw new IllegalArgumentException("I could not resolve the requested merchant or category.");
        }

        if (txnDirection == null) {
            return;
        }

        boolean rawTable = tableSpec.kind == TableKind.RAW_ENTITY
                || tableSpec.kind == TableKind.RAW_CATEGORY
                || tableSpec.kind == TableKind.RAW_TXN;

        if (!rawTable && ("COUNT".equals(intent) || "AVG".equals(intent))) {
            throw new IllegalArgumentException("Direction-specific count and average queries are not supported by the current summary tables.");
        }

        if (!rawTable && (tableSpec.kind == TableKind.ENTITY || tableSpec.kind == TableKind.CATEGORY) && "CREDIT".equals(txnDirection)) {
            throw new IllegalArgumentException("Credit analytics by merchant or category are not supported by the current summary tables.");
        }
    }

    private String dailyAmountColumn(QueryContext ctx, String alias) {
        if ("CREDIT".equals(normalizeDirection(ctx.getTxnDirection()))) {
            return alias + ".total_credit";
        }
        return alias + ".total_debit";
    }

    private String monthlyAmountColumn(QueryContext ctx, String alias) {
        if ("CREDIT".equals(normalizeDirection(ctx.getTxnDirection()))) {
            return alias + ".total_credit";
        }
        return alias + ".total_spend";
    }

    private String normalizeDirection(String txnDirection) {
        return txnDirection == null ? null : txnDirection.toUpperCase();
    }

    private String normalizeDimension(String requestedDimension) {
        return requestedDimension == null ? "NONE" : requestedDimension.toUpperCase();
    }

    private boolean hasResolvedFilter(QueryContext ctx) {
        return (ctx.getEntityIds() != null && !ctx.getEntityIds().isEmpty())
                || (ctx.getCategoryIds() != null && !ctx.getCategoryIds().isEmpty());
    }

    private boolean hasRequestedFilter(QueryContext ctx) {
        boolean searchPhraseRequested = ctx.getSearchPhrase() != null && !ctx.getSearchPhrase().isBlank();
        boolean llmResolvedFilterText = (ctx.getRawEntity() != null && !ctx.getRawEntity().isBlank())
                || (ctx.getRawCategory() != null && !ctx.getRawCategory().isBlank());

        return searchPhraseRequested || llmResolvedFilterText;
    }

    private int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 5;
        }
        return limit;
    }

    private int safeListLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private String joinWhere(List<String> clauses) {
        return String.join(" AND ", clauses);
    }

    private enum TableKind {
        DAILY,
        MONTHLY,
        ENTITY,
        CATEGORY,
        RAW_TXN,
        RAW_ENTITY,
        RAW_CATEGORY
    }

    private record TableSpec(TableKind kind, String table, String alias) {
    }
}
