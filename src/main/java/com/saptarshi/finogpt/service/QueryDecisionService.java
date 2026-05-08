package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.ClarificationOption;
import com.saptarshi.finogpt.dto.ClassificationResult;
import com.saptarshi.finogpt.dto.QueryContext;
import com.saptarshi.finogpt.dto.QueryDecision;
import com.saptarshi.finogpt.dto.QueryExecutionResult;
import com.saptarshi.finogpt.enums.QueryExecutionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class QueryDecisionService {

    public boolean requiresClarification(QueryContext ctx) {
        if (ctx == null) {
            return false;
        }

        if (ctx.isAmbiguousEntity() && hasMultiple(ctx.getEntityCandidateNames())) {
            return true;
        }

        if (ctx.isAmbiguousCategory() && hasMultiple(ctx.getCategoryCandidateNames())) {
            return true;
        }

        return hasAny(ctx.getEntityCandidateNames()) && hasAny(ctx.getCategoryCandidateNames());
    }

    public String buildClarificationQuestion(QueryContext ctx) {
        String searchPhrase = ctx.getSearchPhrase() != null && !ctx.getSearchPhrase().isBlank()
                ? "\"" + ctx.getSearchPhrase() + "\""
                : "your request";

        if (ctx.isAmbiguousEntity() && hasMultiple(ctx.getEntityCandidateNames())) {
            return "I found multiple merchants matching " + searchPhrase + ". Select one or more to continue.";
        }

        if (ctx.isAmbiguousCategory() && hasMultiple(ctx.getCategoryCandidateNames())) {
            return "I found multiple categories matching " + searchPhrase + ". Select one or more to continue.";
        }

        if (hasAny(ctx.getEntityCandidateNames()) && hasAny(ctx.getCategoryCandidateNames())) {
            return "I found both merchant and category matches for " + searchPhrase + ". Select one or more from a single group to continue.";
        }

        return "I need a more specific query before I can continue.";
    }

    public List<ClarificationOption> buildClarificationOptions(QueryContext ctx) {
        List<ClarificationOption> options = new ArrayList<>();

        appendOptions(
                options,
                "ENTITY",
                ctx.getEntityCandidateIds(),
                ctx.getEntityCandidateNames(),
                ctx.getEntityCandidateScores()
        );
        appendOptions(
                options,
                "CATEGORY",
                ctx.getCategoryCandidateIds(),
                ctx.getCategoryCandidateNames(),
                ctx.getCategoryCandidateScores()
        );

        return options;
    }

    public Double resolutionConfidence(QueryContext ctx) {
        if (ctx == null) {
            return null;
        }

        Integer bestScore = null;

        if ("ENTITY".equals(ctx.getResolutionMode()) && !isEmpty(ctx.getEntityCandidateScores())) {
            bestScore = ctx.getEntityCandidateScores().get(0);
        } else if ("CATEGORY".equals(ctx.getResolutionMode()) && !isEmpty(ctx.getCategoryCandidateScores())) {
            bestScore = ctx.getCategoryCandidateScores().get(0);
        } else {
            bestScore = maxFirstScore(ctx.getEntityCandidateScores(), ctx.getCategoryCandidateScores());
        }

        if (bestScore == null) {
            return null;
        }

        return Math.min(1.0d, Math.max(0.0d, bestScore / 100.0d));
    }

    public QueryDecision buildDecision(ClassificationResult classificationResult,
                                       QueryExecutionResult executionResult) {
        return buildDecision(classificationResult, executionResult, null);
    }

    public QueryDecision buildDecision(ClassificationResult classificationResult,
                                       QueryExecutionResult executionResult,
                                       String clarificationToken) {
        QueryExecutionStatus status = executionResult != null ? executionResult.getStatus() : null;
        QueryContext ctx = executionResult != null ? executionResult.getContext() : null;

        String action = "RESPOND";
        boolean executed = false;
        boolean requiresClarification = false;
        String reason = "Direct response generated.";
        String clarificationQuestion = null;
        List<ClarificationOption> clarificationOptions = List.of();

        if (status == QueryExecutionStatus.EXECUTED) {
            action = "EXECUTE";
            executed = true;
            reason = "Query executed successfully.";
        } else if (status == QueryExecutionStatus.CLARIFICATION_REQUIRED) {
            action = "CLARIFY";
            requiresClarification = true;
            reason = "Multiple merchant/category candidates require user selection.";
            clarificationQuestion = buildClarificationQuestion(ctx);
            clarificationOptions = buildClarificationOptions(ctx);
        } else if (status == QueryExecutionStatus.UNSUPPORTED) {
            action = "UNSUPPORTED";
            reason = "Requested query shape is not supported by the current system.";
        } else if (status == QueryExecutionStatus.FAILED) {
            action = "FAILED";
            reason = "Query execution failed.";
        }

        return QueryDecision.builder()
                .action(action)
                .executed(executed)
                .requiresClarification(requiresClarification)
                .reason(reason)
                .classificationConfidence(classificationResult != null ? classificationResult.getConfidence() : null)
                .resolutionConfidence(resolutionConfidence(ctx))
                .clarificationToken(clarificationToken)
                .clarificationQuestion(clarificationQuestion)
                .clarificationOptions(clarificationOptions)
                .build();
    }

    private void appendOptions(List<ClarificationOption> options,
                               String type,
                               List<Long> ids,
                               List<String> names,
                               List<Integer> scores) {
        if (isEmpty(names)) {
            return;
        }

        for (int index = 0; index < names.size(); index++) {
            options.add(ClarificationOption.builder()
                    .type(type)
                    .id(ids != null && ids.size() > index ? ids.get(index) : null)
                    .label(names.get(index))
                    .score(scores != null && scores.size() > index ? scores.get(index) : null)
                    .build());
        }
    }

    private Integer maxFirstScore(List<Integer> first, List<Integer> second) {
        Integer firstScore = !isEmpty(first) ? first.get(0) : null;
        Integer secondScore = !isEmpty(second) ? second.get(0) : null;

        if (firstScore == null) {
            return secondScore;
        }
        if (secondScore == null) {
            return firstScore;
        }
        return Math.max(firstScore, secondScore);
    }

    private boolean hasMultiple(List<?> values) {
        return values != null && values.size() > 1;
    }

    private boolean hasAny(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }
}
