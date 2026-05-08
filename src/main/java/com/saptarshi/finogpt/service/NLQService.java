package com.saptarshi.finogpt.service;


import com.saptarshi.finogpt.dto.QueryContext;
import com.saptarshi.finogpt.dto.QueryExecutionResult;
import com.saptarshi.finogpt.dto.SqlQueryPlan;
import com.saptarshi.finogpt.enums.QueryExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NLQService {

    private final LLMService llmService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ResolverService resolverService;
    private final SQLBuilderService sqlBuilderService;
    private final QueryDecisionService queryDecisionService;


    public String processQuery(Long userId, String userQuery) {
        return executeQuery(userId, userQuery).getAnswer();
    }

    public QueryExecutionResult executeQuery(Long userId, String userQuery) {
        QueryContext ctx = resolverService.resolve(userId, userQuery);
        return executeResolvedQuery(userQuery, ctx);
    }

    public QueryExecutionResult executeResolvedQuery(String userQuery, QueryContext ctx) {
        if (queryDecisionService.requiresClarification(ctx)) {
            return new QueryExecutionResult(
                    queryDecisionService.buildClarificationQuestion(ctx),
                    List.of(),
                    ctx,
                    QueryExecutionStatus.CLARIFICATION_REQUIRED
            );
        }

        SqlQueryPlan queryPlan;

        try {
            queryPlan = sqlBuilderService.buildPlan(ctx);
        } catch (IllegalArgumentException ex) {
            log.warn("Unsupported analytics query for context {}: {}", ctx, ex.getMessage());
            return new QueryExecutionResult(
                    ex.getMessage(),
                    List.of(),
                    ctx,
                    QueryExecutionStatus.UNSUPPORTED
            );
        }

        log.info("Generated SQL: {} with params {}", queryPlan.getSql(), queryPlan.getParameters());
        validateSQL(queryPlan.getSql());

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(queryPlan.getSql(), queryPlan.getParameters());
            log.info("Query result: {}", result);
            return new QueryExecutionResult(formatResponse(userQuery, result), result, ctx, QueryExecutionStatus.EXECUTED);
        } catch (DataAccessException ex) {
            log.error("Deterministic SQL execution failed for context {}", ctx, ex);
            return new QueryExecutionResult("I could not execute that analytics query.", List.of(), ctx, QueryExecutionStatus.FAILED);
        }
    }

    private void validateSQL(String sql) {

        String lower = sql.toLowerCase();

        if (!lower.startsWith("select")) {
            throw new RuntimeException("Only SELECT queries allowed");
        }

        if (lower.contains("delete") ||
                lower.contains("update") ||
                lower.contains("insert") ||
                lower.contains("drop") ||
                lower.contains("truncate") ||
                lower.contains("--") ||
                lower.contains(";")) {

            throw new RuntimeException("Unsafe SQL detected");
        }

        // optional: enforce user_id presence
        if (!lower.contains("user_id")) {
            throw new RuntimeException("Query must filter by user_id");
        }
    }
    private String formatResponse(String userQuery, List<Map<String, Object>> data) {

        if (data.isEmpty()) {
            return "No data found for your query.";
        }

        String prompt = String.format(
                "Convert the following data into a user-friendly financial answer.%n%n" +
                        "User Query:%n" +
                        "%s%n%n" +
                        "Data:%n" +
                        "%s%n%n" +
                        "Keep it:%n" +
                        "- short%n" +
                        "- clear%n" +
                        "- numeric where needed%n" +
                        "- plain text only%n" +
                        "- no markdown%n" +
                        "- no bold, no italics, no bullet points%n" +
                        "- all monetary amounts are in Indian rupees%n" +
                        "- use Rs before amounts%n" +
                        "- never use $ or the word dollars",
                userQuery,
                data.toString()
        );

        return sanitizeFinancialAnswer(llmService.generate(prompt));
    }

    private String sanitizeFinancialAnswer(String answer) {
        if (answer == null) {
            return null;
        }

        return answer
                .replace("**", "")
                .replace("__", "")
                .replace("$", "Rs ")
                .replace(" dollars", " rupees")
                .replace(" dollar", " rupee")
                .trim();
    }
}
