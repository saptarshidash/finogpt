package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.QueryContext;
import com.saptarshi.finogpt.dto.SqlQueryPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLBuilderServiceTest {

    private final SQLBuilderService sqlBuilderService = new SQLBuilderService();

    @Test
    void buildsMonthlySummaryQueryForTotalSpendThisYear() {
        QueryContext ctx = new QueryContext();
        ctx.setUserId(1L);
        ctx.setIntent("SUM");
        ctx.setTxnDirection("DEBIT");
        ctx.setYear(LocalDate.now().getYear());
        ctx.setYearExplicit(true);
        ctx.setRequestedDimension("NONE");
        ctx.setResolutionMode("NONE");

        SqlQueryPlan plan = sqlBuilderService.buildPlan(ctx);

        assertTrue(plan.getSql().contains("FROM monthly_summary ms"));
        assertTrue(plan.getSql().contains("ms.year = :year"));
        assertEquals(LocalDate.now().getYear(), plan.getParameters().get("year"));
    }

    @Test
    void allowsTopCategoryQueriesWithoutAResolvedFilter() {
        QueryContext ctx = new QueryContext();
        ctx.setUserId(1L);
        ctx.setIntent("TOP");
        ctx.setTxnDirection("DEBIT");
        ctx.setYear(LocalDate.now().getYear());
        ctx.setMonth(LocalDate.now().getMonthValue());
        ctx.setRequestedDimension("CATEGORY");
        ctx.setResolutionMode("NONE");
        ctx.setLimit(5);

        SqlQueryPlan plan = sqlBuilderService.buildPlan(ctx);

        assertTrue(plan.getSql().contains("FROM category_summary cs"));
        assertTrue(plan.getSql().contains("GROUP BY cs.category_id, c.name"));
        assertEquals(5, plan.getParameters().get("limit"));
    }

    @Test
    void usesRawTransactionsForCreditQueriesByEntity() {
        QueryContext ctx = new QueryContext();
        ctx.setUserId(1L);
        ctx.setIntent("SUM");
        ctx.setTxnDirection("CREDIT");
        ctx.setYear(2026);
        ctx.setMonth(4);
        ctx.setRequestedDimension("ENTITY");
        ctx.setResolutionMode("ENTITY");
        ctx.setEntityIds(List.of(31L));

        SqlQueryPlan plan = sqlBuilderService.buildPlan(ctx);

        assertTrue(plan.getSql().contains("FROM transactions t"));
        assertTrue(plan.getSql().contains("t.entity_id IN (:entityIds)"));
        assertTrue(plan.getSql().contains("t.txn_type = :txnType"));
        assertEquals("CREDIT", plan.getParameters().get("txnType"));
        assertEquals(LocalDate.of(2026, 4, 1), plan.getParameters().get("fromDate"));
        assertEquals(LocalDate.of(2026, 4, 30), plan.getParameters().get("toDate"));
    }

    @Test
    void usesRawTransactionsForTopCategoryQueriesThisWeek() {
        QueryContext ctx = new QueryContext();
        ctx.setUserId(1L);
        ctx.setIntent("TOP");
        ctx.setTxnDirection("DEBIT");
        ctx.setRequestedDimension("CATEGORY");
        ctx.setResolutionMode("NONE");
        ctx.setThisWeek(true);
        ctx.setLimit(5);

        SqlQueryPlan plan = sqlBuilderService.buildPlan(ctx);

        assertTrue(plan.getSql().contains("FROM transactions t"));
        assertTrue(plan.getSql().contains("COALESCE(t.user_category_id, t.category_id) AS category_id"));
        assertTrue(plan.getSql().contains("t.txn_type = :txnType"));
        assertEquals("DEBIT", plan.getParameters().get("txnType"));
        assertTrue(plan.getParameters().containsKey("fromDate"));
        assertTrue(plan.getParameters().containsKey("toDate"));
    }

    @Test
    void usesRawTransactionsForTopTransactionQueriesWithoutDimension() {
        QueryContext ctx = new QueryContext();
        ctx.setUserId(4L);
        ctx.setIntent("TOP");
        ctx.setYear(2026);
        ctx.setMonth(4);
        ctx.setRequestedDimension("NONE");
        ctx.setResolutionMode("NONE");
        ctx.setLimit(10);

        SqlQueryPlan plan = sqlBuilderService.buildPlan(ctx);

        assertTrue(plan.getSql().contains("FROM transactions t"));
        assertTrue(plan.getSql().contains("LEFT JOIN entities e ON e.id = t.entity_id"));
        assertTrue(plan.getSql().contains("LEFT JOIN categories c ON c.id = COALESCE(t.user_category_id, t.category_id)"));
        assertTrue(plan.getSql().contains("ORDER BY t.amount DESC, t.txn_date DESC, t.id DESC"));
        assertTrue(plan.getSql().contains("LIMIT 10"));
        assertEquals(LocalDate.of(2026, 4, 1), plan.getParameters().get("fromDate"));
        assertEquals(LocalDate.of(2026, 4, 30), plan.getParameters().get("toDate"));
    }

    @Test
    void usesRawTransactionsForListQueries() {
        QueryContext ctx = new QueryContext();
        ctx.setUserId(4L);
        ctx.setIntent("LIST");
        ctx.setYear(2026);
        ctx.setMonth(4);
        ctx.setRequestedDimension("NONE");
        ctx.setResolutionMode("NONE");
        ctx.setLimit(10);

        SqlQueryPlan plan = sqlBuilderService.buildPlan(ctx);

        assertTrue(plan.getSql().contains("FROM transactions t"));
        assertTrue(plan.getSql().contains("ORDER BY t.txn_date DESC, t.txn_time DESC NULLS LAST, t.id DESC"));
        assertTrue(plan.getSql().contains("LIMIT 10"));
        assertEquals(LocalDate.of(2026, 4, 1), plan.getParameters().get("fromDate"));
        assertEquals(LocalDate.of(2026, 4, 30), plan.getParameters().get("toDate"));
    }

    @Test
    void usesDailySummaryForExactDateRangeWithoutFilters() {
        QueryContext ctx = new QueryContext();
        ctx.setUserId(1L);
        ctx.setIntent("SUM");
        ctx.setTxnDirection("DEBIT");
        ctx.setRequestedDimension("NONE");
        ctx.setResolutionMode("NONE");
        ctx.setFromDate(LocalDate.of(2026, 4, 27));
        ctx.setToDate(LocalDate.of(2026, 5, 7));

        SqlQueryPlan plan = sqlBuilderService.buildPlan(ctx);

        assertTrue(plan.getSql().contains("FROM daily_summary ds"));
        assertTrue(plan.getSql().contains("ds.date BETWEEN :fromDate AND :toDate"));
        assertEquals(LocalDate.of(2026, 4, 27), plan.getParameters().get("fromDate"));
        assertEquals(LocalDate.of(2026, 5, 7), plan.getParameters().get("toDate"));
    }

    @Test
    void usesRawTransactionsForMaximumTransactionAmountThisYear() {
        QueryContext ctx = new QueryContext();
        ctx.setUserId(1L);
        ctx.setIntent("MAX");
        ctx.setYear(2026);
        ctx.setYearExplicit(true);
        ctx.setRequestedDimension("NONE");
        ctx.setResolutionMode("NONE");

        SqlQueryPlan plan = sqlBuilderService.buildPlan(ctx);

        assertTrue(plan.getSql().contains("FROM transactions t"));
        assertTrue(plan.getSql().contains("COALESCE(MAX(t.amount), 0) AS max_amount"));
        assertEquals(LocalDate.of(2026, 1, 1), plan.getParameters().get("fromDate"));
        assertEquals(LocalDate.of(2026, 12, 31), plan.getParameters().get("toDate"));
    }

    @Test
    void returnsEntityAndAmountForMaximumTransactionCounterpartyQuery() {
        QueryContext ctx = new QueryContext();
        ctx.setUserId(1L);
        ctx.setIntent("MAX");
        ctx.setTxnDirection("DEBIT");
        ctx.setYear(2026);
        ctx.setYearExplicit(true);
        ctx.setRequestedDimension("ENTITY");
        ctx.setResolutionMode("NONE");

        SqlQueryPlan plan = sqlBuilderService.buildPlan(ctx);

        assertTrue(plan.getSql().contains("LEFT JOIN entities e ON e.id = t.entity_id"));
        assertTrue(plan.getSql().contains("e.name AS entity_name"));
        assertTrue(plan.getSql().contains("t.amount AS amount"));
        assertTrue(plan.getSql().contains("ORDER BY t.amount DESC"));
        assertTrue(plan.getSql().contains("LIMIT 1"));
        assertTrue(plan.getSql().contains("t.entity_id IS NOT NULL"));
        assertEquals("DEBIT", plan.getParameters().get("txnType"));
        assertEquals(LocalDate.of(2026, 1, 1), plan.getParameters().get("fromDate"));
        assertEquals(LocalDate.of(2026, 12, 31), plan.getParameters().get("toDate"));
    }
}
