package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.ClassificationResult;
import com.saptarshi.finogpt.dto.DashboardAnomalySummaryResponse;
import com.saptarshi.finogpt.dto.DashboardRecurringSummaryResponse;
import com.saptarshi.finogpt.dto.PagedResponse;
import com.saptarshi.finogpt.dto.PendingClarification;
import com.saptarshi.finogpt.dto.QueryContext;
import com.saptarshi.finogpt.dto.QueryDecision;
import com.saptarshi.finogpt.dto.QueryExecutionResult;
import com.saptarshi.finogpt.dto.QueryMetadata;
import com.saptarshi.finogpt.dto.QueryRequest;
import com.saptarshi.finogpt.dto.QueryResponse;
import com.saptarshi.finogpt.dto.QueryTimeWindow;
import com.saptarshi.finogpt.dto.AnomalyListItemResponse;
import com.saptarshi.finogpt.dto.RecurringListItemResponse;
import com.saptarshi.finogpt.enums.QueryExecutionStatus;
import com.saptarshi.finogpt.enums.QueryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.saptarshi.finogpt.enums.QueryType.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class HybridQueryService {

    private final NLQService nlqService;
    private final RagService ragService;
    private final LLMService llmService;
    private final QueryDecisionService queryDecisionService;
    private final ClarificationSessionService clarificationSessionService;
    private final RecurringReadService recurringReadService;
    private final AnomalyReadService anomalyReadService;

    // =============================
    // MAIN ENTRY
    // =============================
    public QueryResponse handle(Long userId, String query) {
        QueryRequest request = new QueryRequest();
        request.setUserId(userId);
        request.setQuery(query);
        return handle(request);
    }

    public QueryResponse handle(QueryRequest request) {
        if (request.getClarificationToken() != null && !request.getClarificationToken().isBlank()) {
            return handleClarification(request);
        }

        return handleFreshQuery(request.getUserId(), request.getQuery());
    }

    private QueryResponse handleFreshQuery(Long userId, String query) {
        DirectQueryRoute directRoute = routeDirectQuery(query);
        if (directRoute != null) {
            return handleDirectQuery(userId, query, directRoute);
        }

        ClassificationResult result = classify(query);

        log.info("Query '{}' classification result {}", query, result);

       
        if (result.isHybrid()) {
            return handleHybrid(userId, query, result);
        }

        if (result.getType() == NLQ) {
            QueryExecutionResult executionResult = nlqService.executeQuery(userId, query);
            return buildResponse(query, result, executionResult);
        }

        if (result.getType() == RAG) {
            QueryExecutionResult executionResult = ragService.executeSearch(userId, query);
            return buildResponse(query, result, executionResult);
        }

        QueryDecision decision = queryDecisionService.buildDecision(result, null);
        return QueryResponse.builder()
                .answer(llmService.generate(buildLLMPrompt(query)))
                .data(List.of())
                .decision(decision)
                .metadata(buildMetadata(query, result, null))
                .build();
    }

    private QueryResponse handleDirectQuery(Long userId, String query, DirectQueryRoute route) {
        QueryExecutionResult executionResult;
        DirectQueryKind kind = route.getKind();
        if (kind == DirectQueryKind.RECURRING_DUE_SOON_LIST) {
            executionResult = executeRecurringDueSoonList(userId, query);
        } else if (kind == DirectQueryKind.RECURRING_SUMMARY) {
            executionResult = executeRecurringSummary(userId, query);
        } else if (kind == DirectQueryKind.RECURRING_LIST) {
            executionResult = executeRecurringList(userId, query);
        } else if (kind == DirectQueryKind.ANOMALY_SUMMARY) {
            executionResult = executeAnomalySummary(userId, query);
        } else if (kind == DirectQueryKind.ANOMALY_LIST) {
            executionResult = executeAnomalyList(userId, query);
        } else {
            throw new IllegalArgumentException("Unsupported direct query route");
        }

        return buildResponse(query, route.getClassificationResult(), executionResult);
    }

    private QueryResponse handleClarification(QueryRequest request) {
        PendingClarification session = clarificationSessionService.getSession(
                request.getClarificationToken(),
                request.getUserId()
        );

        if (session == null) {
            return QueryResponse.builder()
                    .answer("This clarification request is invalid or has expired.")
                    .data(List.of())
                    .decision(QueryDecision.builder()
                            .action("FAILED")
                            .executed(false)
                            .requiresClarification(false)
                            .reason("Clarification session was not found or has expired.")
                            .clarificationToken(request.getClarificationToken())
                            .build())
                    .metadata(null)
                    .build();
        }

        QueryContext clarifiedContext = applyClarificationSelection(
                session.getContext(),
                request.getSelectedEntityIds(),
                request.getSelectedCategoryIds(),
                request.getSelectedEntityId(),
                request.getSelectedCategoryId()
        );

        if (clarifiedContext == null) {
            QueryExecutionResult pendingResult = new QueryExecutionResult(
                    "Please choose one or more of the provided clarification options.",
                    List.of(),
                    session.getContext(),
                    QueryExecutionStatus.CLARIFICATION_REQUIRED
            );
            return buildResponse(
                    session.getOriginalQuery(),
                    session.getClassificationResult(),
                    pendingResult,
                    request.getClarificationToken()
            );
        }

        QueryResponse response = continueResolvedQuery(session, clarifiedContext);
        if (response.getDecision() != null && !response.getDecision().isRequiresClarification()) {
            clarificationSessionService.removeSession(request.getClarificationToken());
        }
        return response;
    }


    private QueryResponse handleHybrid(Long userId, String query, ClassificationResult classificationResult) {
        return handleHybrid(query, classificationResult, nlqService.executeQuery(userId, query));
    }

    private QueryResponse handleHybrid(String query,
                                       ClassificationResult classificationResult,
                                       QueryExecutionResult executionResult) {
        if (executionResult.getStatus() != QueryExecutionStatus.EXECUTED) {
            return buildResponse(query, classificationResult, executionResult);
        }
        String data = executionResult.getData().toString();

        String prompt = "You are a personal finance analyst.\n\n" +
                "The user asked:\n" +
                "\"" + query + "\"\n\n" +
                "Here is the relevant financial data:\n" +
                data + "\n\n" +
                "Your task:\n" +
                "1. Explain WHY this happened (based on data)\n" +
                "2. Highlight key insights (patterns, increases, unusual behavior)\n" +
                "3. Suggest actionable improvements (if applicable)\n\n" +
                "Rules:\n" +
                "- Be specific (use numbers from data)\n" +
                "- Do NOT make up information\n" +
                "- Keep response concise (max 5 bullet points)\n" +
                "- Focus on useful insights, not generic statements\n\n" +
                "Output format:\n" +
                "- Bullet points";

        return QueryResponse.builder()
                .answer(llmService.generate(prompt))
                .data(executionResult.getData())
                .decision(queryDecisionService.buildDecision(classificationResult, executionResult))
                .metadata(buildMetadata(query, classificationResult, executionResult.getContext()))
                .build();
    }
    
    private boolean isHybrid(String query) {

        String q = query.toLowerCase();

        return (q.contains("why") || q.contains("explain"))
                && (q.contains("spend") || q.contains("expense") || q.contains("total"));
    }


    public ClassificationResult classify(String query) {

        String q = query.toLowerCase().trim();

        double nlqScore = scoreNLQ(q);
        double ragScore = scoreRAG(q);
        double llmScore = scoreLLM(q);

        boolean isHybrid = isHybrid(q);

        // pick highest
        QueryType type = NLQ;
        double max = nlqScore;

        if (ragScore > max) {
            type = RAG;
            max = ragScore;
        }

        if (llmScore > max) {
            type = LLM;
            max = llmScore;
        }
        
        if (max < 0.6) {
            return classifyWithLLM(query);
        }

        return new ClassificationResult(type, max, isHybrid);
    }


    private ClassificationResult classifyWithLLM(String query) {

        try {

            String prompt = "You are an intent classifier for a financial analytics application.\n\n" +
                    "Your job is to classify user queries into ONE of the following categories:\n\n" +
                    "NLQ = questions about numbers, totals, aggregations, comparisons\n" +
                    "RAG = searching or filtering transactions based on meaning\n" +
                    "LLM = insights, reasoning, advice, or explanations\n\n" +
                    "Examples:\n" +
                    "\"How much did I spend last month?\" = NLQ\n" +
                    "\"Top 5 merchants\" = NLQ\n" +
                    "\"Show transactions related to travel\" = RAG\n" +
                    "\"Find similar payments\" = RAG\n" +
                    "\"Why is my spending high?\" = LLM\n" +
                    "\"How can I save money?\" = LLM\n\n" +
                    "Rules:\n" +
                    "- Return ONLY one word: NLQ or RAG or LLM\n" +
                    "- Do NOT explain\n" +
                    "- Do NOT return anything else\n\n" +
                    "Query:\n" + query;

            String response = llmService.generate(prompt)
                    .trim()
                    .toUpperCase();


            return new ClassificationResult(
                    QueryType.valueOf(response),
                    0.7,
                    isHybrid(query)
            );

        } catch (Exception e) {
            return new ClassificationResult(LLM, 0.5, isHybrid(query));
        }
    }

    private QueryResponse buildResponse(String originalQuery,
                                        ClassificationResult classificationResult,
                                        QueryExecutionResult executionResult) {
        String clarificationToken = null;
        if (executionResult.getStatus() == QueryExecutionStatus.CLARIFICATION_REQUIRED
                && executionResult.getContext() != null) {
            clarificationToken = clarificationSessionService.createSession(
                    executionResult.getContext().getUserId(),
                    originalQuery,
                    classificationResult,
                    executionResult.getContext()
            );
        }

        return buildResponse(originalQuery, classificationResult, executionResult, clarificationToken);
    }

    private QueryResponse buildResponse(String originalQuery,
                                        ClassificationResult classificationResult,
                                        QueryExecutionResult executionResult,
                                        String clarificationToken) {
        QueryDecision decision = queryDecisionService.buildDecision(classificationResult, executionResult, clarificationToken);
        return QueryResponse.builder()
                .answer(executionResult.getAnswer())
                .data(executionResult.getData())
                .decision(decision)
                .metadata(buildMetadata(originalQuery, classificationResult, executionResult.getContext()))
                .build();
    }

    private QueryResponse continueResolvedQuery(PendingClarification session, QueryContext clarifiedContext) {
        ClassificationResult classificationResult = session.getClassificationResult();

        if (classificationResult.isHybrid()) {
            QueryExecutionResult executionResult = nlqService.executeResolvedQuery(session.getOriginalQuery(), clarifiedContext);
            return handleHybrid(session.getOriginalQuery(), classificationResult, executionResult);
        }

        if (classificationResult.getType() == QueryType.NLQ) {
            QueryExecutionResult executionResult = nlqService.executeResolvedQuery(session.getOriginalQuery(), clarifiedContext);
            return buildResponse(session.getOriginalQuery(), classificationResult, executionResult);
        }

        if (classificationResult.getType() == QueryType.RAG) {
            QueryExecutionResult executionResult = ragService.executeResolvedSearch(session.getOriginalQuery(), clarifiedContext);
            return buildResponse(session.getOriginalQuery(), classificationResult, executionResult);
        }

        return QueryResponse.builder()
                .answer(llmService.generate(buildLLMPrompt(session.getOriginalQuery())))
                .data(List.of())
                .decision(queryDecisionService.buildDecision(classificationResult, null))
                .metadata(buildMetadata(session.getOriginalQuery(), classificationResult, clarifiedContext))
                .build();
    }

    private QueryContext applyClarificationSelection(QueryContext original,
                                                     List<Long> selectedEntityIds,
                                                     List<Long> selectedCategoryIds,
                                                     Long selectedEntityId,
                                                     Long selectedCategoryId) {
        QueryContext clarified = copyContext(original);

        List<Long> entitySelection = normalizeSelection(selectedEntityIds, selectedEntityId);
        List<Long> categorySelection = normalizeSelection(selectedCategoryIds, selectedCategoryId);

        if (!entitySelection.isEmpty() && !categorySelection.isEmpty()) {
            return null;
        }

        if (!entitySelection.isEmpty()) {
            return applyEntitySelection(clarified, original, entitySelection);
        }

        if (!categorySelection.isEmpty()) {
            return applyCategorySelection(clarified, original, categorySelection);
        }

        return null;
    }

    private QueryContext applyEntitySelection(QueryContext clarified,
                                              QueryContext original,
                                              List<Long> selectedEntityIds) {
        List<Long> candidateIds = original.getEntityCandidateIds();
        List<String> candidateNames = original.getEntityCandidateNames();
        List<Long> validatedIds = filterSelection(candidateIds, selectedEntityIds);

        if (validatedIds.isEmpty()) {
            return null;
        }

        List<String> validatedNames = namesForSelection(candidateIds, candidateNames, validatedIds);

        clarified.setEntityIds(validatedIds);
        clarified.setEntityNames(validatedNames);
        clarified.setEntityId(validatedIds.size() == 1 ? validatedIds.get(0) : null);
        clarified.setEntityName(validatedNames.size() == 1 ? validatedNames.get(0) : null);
        clarified.setCategoryId(null);
        clarified.setCategoryIds(null);
        clarified.setCategoryName(null);
        clarified.setCategoryNames(null);
        clarified.setResolutionMode("ENTITY");
        clarified.setAmbiguousEntity(false);
        clarified.setAmbiguousCategory(false);
        clarified.setAmbiguousResolution(false);
        return clarified;
    }

    private QueryContext applyCategorySelection(QueryContext clarified,
                                                QueryContext original,
                                                List<Long> selectedCategoryIds) {
        List<Long> candidateIds = original.getCategoryCandidateIds();
        List<String> candidateNames = original.getCategoryCandidateNames();
        List<Long> validatedIds = filterSelection(candidateIds, selectedCategoryIds);

        if (validatedIds.isEmpty()) {
            return null;
        }

        List<String> validatedNames = namesForSelection(candidateIds, candidateNames, validatedIds);

        clarified.setCategoryIds(validatedIds);
        clarified.setCategoryNames(validatedNames);
        clarified.setCategoryId(validatedIds.size() == 1 ? validatedIds.get(0) : null);
        clarified.setCategoryName(validatedNames.size() == 1 ? validatedNames.get(0) : null);
        clarified.setEntityId(null);
        clarified.setEntityIds(null);
        clarified.setEntityName(null);
        clarified.setEntityNames(null);
        clarified.setResolutionMode("CATEGORY");
        clarified.setAmbiguousEntity(false);
        clarified.setAmbiguousCategory(false);
        clarified.setAmbiguousResolution(false);
        return clarified;
    }

    private List<Long> normalizeSelection(List<Long> selections, Long singleSelection) {
        Set<Long> normalized = new LinkedHashSet<>();
        if (selections != null) {
            normalized.addAll(selections);
        }
        if (singleSelection != null) {
            normalized.add(singleSelection);
        }
        return new ArrayList<>(normalized);
    }

    private List<Long> filterSelection(List<Long> candidateIds, List<Long> selectedIds) {
        if (candidateIds == null || candidateIds.isEmpty() || selectedIds == null || selectedIds.isEmpty()) {
            return List.of();
        }

        Set<Long> selectedSet = new LinkedHashSet<>(selectedIds);
        List<Long> filtered = new ArrayList<>();
        for (Long candidateId : candidateIds) {
            if (selectedSet.contains(candidateId)) {
                filtered.add(candidateId);
            }
        }
        return filtered;
    }

    private List<String> namesForSelection(List<Long> candidateIds,
                                           List<String> candidateNames,
                                           List<Long> selectedIds) {
        List<String> names = new ArrayList<>();
        if (candidateIds == null || candidateNames == null) {
            return names;
        }

        for (Long selectedId : selectedIds) {
            int index = candidateIds.indexOf(selectedId);
            if (index >= 0 && candidateNames.size() > index) {
                names.add(candidateNames.get(index));
            }
        }
        return names;
    }

    private QueryContext copyContext(QueryContext source) {
        QueryContext target = new QueryContext();
        target.setUserId(source.getUserId());
        target.setYear(source.getYear());
        target.setYearExplicit(source.isYearExplicit());
        target.setMonth(source.getMonth());
        target.setDay(source.getDay());
        target.setToday(source.isToday());
        target.setYesterday(source.isYesterday());
        target.setLastMonth(source.isLastMonth());
        target.setThisMonth(source.isThisMonth());
        target.setThisWeek(source.isThisWeek());
        target.setLastWeek(source.isLastWeek());
        target.setLastNMonths(source.getLastNMonths());
        target.setTimeReferenced(source.isTimeReferenced());
        target.setFromDate(source.getFromDate());
        target.setToDate(source.getToDate());
        target.setLimit(source.getLimit());
        target.setTxnDirection(source.getTxnDirection());
        target.setSearchPhrase(source.getSearchPhrase());
        target.setRawEntity(source.getRawEntity());
        target.setRawCategory(source.getRawCategory());
        target.setEntityId(source.getEntityId());
        target.setEntityName(source.getEntityName());
        target.setCategoryId(source.getCategoryId());
        target.setCategoryName(source.getCategoryName());
        target.setEntityIds(copyList(source.getEntityIds()));
        target.setEntityNames(copyList(source.getEntityNames()));
        target.setCategoryIds(copyList(source.getCategoryIds()));
        target.setCategoryNames(copyList(source.getCategoryNames()));
        target.setEntityCandidateIds(copyList(source.getEntityCandidateIds()));
        target.setEntityCandidateNames(copyList(source.getEntityCandidateNames()));
        target.setEntityCandidateScores(copyList(source.getEntityCandidateScores()));
        target.setCategoryCandidateIds(copyList(source.getCategoryCandidateIds()));
        target.setCategoryCandidateNames(copyList(source.getCategoryCandidateNames()));
        target.setCategoryCandidateScores(copyList(source.getCategoryCandidateScores()));
        target.setAmbiguousEntity(source.isAmbiguousEntity());
        target.setAmbiguousCategory(source.isAmbiguousCategory());
        target.setAmbiguousResolution(source.isAmbiguousResolution());
        target.setRequestedDimension(source.getRequestedDimension());
        target.setResolutionMode(source.getResolutionMode());
        target.setIntent(source.getIntent());
        target.setOriginalQuery(source.getOriginalQuery());
        return target;
    }

    private <T> List<T> copyList(List<T> values) {
        return values == null ? null : new ArrayList<>(values);
    }

    private QueryMetadata buildMetadata(String originalQuery,
                                        ClassificationResult classificationResult,
                                        QueryContext ctx) {
        return QueryMetadata.builder()
                .originalQuery(originalQuery)
                .queryType(classificationResult.getType())
                .classificationConfidence(classificationResult.getConfidence())
                .hybrid(classificationResult.isHybrid())
                .intent(ctx != null ? ctx.getIntent() : null)
                .txnDirection(ctx != null ? ctx.getTxnDirection() : null)
                .limit(ctx != null ? ctx.getLimit() : null)
                .requestedDimension(ctx != null ? ctx.getRequestedDimension() : null)
                .resolutionMode(ctx != null ? ctx.getResolutionMode() : null)
                .ambiguousResolution(ctx != null && ctx.isAmbiguousResolution())
                .ambiguousEntity(ctx != null && ctx.isAmbiguousEntity())
                .ambiguousCategory(ctx != null && ctx.isAmbiguousCategory())
                .resolutionConfidence(queryDecisionService.resolutionConfidence(ctx))
                .entityIds(ctx != null ? ctx.getEntityIds() : null)
                .entityNames(ctx != null ? ctx.getEntityNames() : null)
                .entityCandidateIds(ctx != null ? ctx.getEntityCandidateIds() : null)
                .entityCandidateNames(ctx != null ? ctx.getEntityCandidateNames() : null)
                .entityCandidateScores(ctx != null ? ctx.getEntityCandidateScores() : null)
                .categoryIds(ctx != null ? ctx.getCategoryIds() : null)
                .categoryNames(ctx != null ? ctx.getCategoryNames() : null)
                .categoryCandidateIds(ctx != null ? ctx.getCategoryCandidateIds() : null)
                .categoryCandidateNames(ctx != null ? ctx.getCategoryCandidateNames() : null)
                .categoryCandidateScores(ctx != null ? ctx.getCategoryCandidateScores() : null)
                .timeWindow(buildTimeWindow(ctx))
                .build();
    }

    private QueryTimeWindow buildTimeWindow(QueryContext ctx) {
        if (ctx == null) {
            return null;
        }

        return QueryTimeWindow.builder()
                .year(ctx.getYear())
                .month(ctx.getMonth())
                .day(ctx.getDay())
                .lastNMonths(ctx.getLastNMonths())
                .fromDate(ctx.getFromDate())
                .toDate(ctx.getToDate())
                .today(ctx.isToday())
                .yesterday(ctx.isYesterday())
                .lastMonth(ctx.isLastMonth())
                .thisMonth(ctx.isThisMonth())
                .thisWeek(ctx.isThisWeek())
                .lastWeek(ctx.isLastWeek())
                .build();
    }

    private DirectQueryRoute routeDirectQuery(String query) {
        String normalized = query.toLowerCase().trim();

        if (isRecurringQuery(normalized)) {
            if (normalized.contains("due soon") || normalized.contains("upcoming")) {
                if (requestsCount(normalized)) {
                    return new DirectQueryRoute(
                            DirectQueryKind.RECURRING_SUMMARY,
                            new ClassificationResult(NLQ, 0.95, false)
                    );
                }
                return new DirectQueryRoute(
                        DirectQueryKind.RECURRING_DUE_SOON_LIST,
                        new ClassificationResult(RAG, 0.95, false)
                );
            }

            if (requestsCount(normalized)) {
                return new DirectQueryRoute(
                        DirectQueryKind.RECURRING_SUMMARY,
                        new ClassificationResult(NLQ, 0.9, false)
                );
            }

            if (requestsList(normalized)) {
                return new DirectQueryRoute(
                        DirectQueryKind.RECURRING_LIST,
                        new ClassificationResult(RAG, 0.9, false)
                );
            }
        }

        if (isAnomalyQuery(normalized)) {
            if (requestsCount(normalized) || normalized.contains("summary")) {
                return new DirectQueryRoute(
                        DirectQueryKind.ANOMALY_SUMMARY,
                        new ClassificationResult(NLQ, 0.9, false)
                );
            }

            if (requestsList(normalized)) {
                return new DirectQueryRoute(
                        DirectQueryKind.ANOMALY_LIST,
                        new ClassificationResult(RAG, 0.9, false)
                );
            }
        }

        return null;
    }

    private QueryExecutionResult executeRecurringDueSoonList(Long userId, String query) {
        List<RecurringListItemResponse> items = recurringReadService.listDueSoon(userId, 10);
        QueryContext context = directContext(userId, query, "LIST");
        return new QueryExecutionResult(
                summarizeRecurringDueSoon(items),
                items.stream().map(this::toRecurringMap).toList(),
                context,
                QueryExecutionStatus.EXECUTED
        );
    }

    private QueryExecutionResult executeRecurringSummary(Long userId, String query) {
        DashboardRecurringSummaryResponse summary = recurringReadService.getDashboardSummary(userId, 5);
        QueryContext context = directContext(userId, query, "COUNT");
        return new QueryExecutionResult(
                summarizeRecurringCounts(query, summary),
                List.of(toRecurringSummaryMap(summary)),
                context,
                QueryExecutionStatus.EXECUTED
        );
    }

    private QueryExecutionResult executeRecurringList(Long userId, String query) {
        PagedResponse<RecurringListItemResponse> page = recurringReadService.listRecurring(userId, null, null, 0, 10);
        QueryContext context = directContext(userId, query, "LIST");
        return new QueryExecutionResult(
                summarizeRecurringList(page.getItems(), page.getTotalItems()),
                page.getItems().stream().map(this::toRecurringMap).toList(),
                context,
                QueryExecutionStatus.EXECUTED
        );
    }

    private QueryExecutionResult executeAnomalySummary(Long userId, String query) {
        DashboardAnomalySummaryResponse summary = anomalyReadService.getDashboardSummary(userId, 5);
        QueryContext context = directContext(userId, query, "COUNT");
        return new QueryExecutionResult(
                summarizeAnomalyCounts(summary),
                List.of(toAnomalySummaryMap(summary)),
                context,
                QueryExecutionStatus.EXECUTED
        );
    }

    private QueryExecutionResult executeAnomalyList(Long userId, String query) {
        String normalized = query.toLowerCase();
        String severity = extractAnomalySeverity(normalized);
        String anomalyType = extractAnomalyType(normalized);
        PagedResponse<AnomalyListItemResponse> page = anomalyReadService.listAnomalies(userId, severity, anomalyType, 0, 10);
        QueryContext context = directContext(userId, query, "LIST");
        return new QueryExecutionResult(
                summarizeAnomalyList(page.getItems(), page.getTotalItems(), severity, anomalyType),
                page.getItems().stream().map(this::toAnomalyMap).toList(),
                context,
                QueryExecutionStatus.EXECUTED
        );
    }

    private QueryContext directContext(Long userId, String query, String intent) {
        QueryContext ctx = new QueryContext();
        ctx.setUserId(userId);
        ctx.setIntent(intent);
        ctx.setRequestedDimension("NONE");
        ctx.setResolutionMode("NONE");
        ctx.setOriginalQuery(query);
        return ctx;
    }

    private String summarizeRecurringDueSoon(List<RecurringListItemResponse> items) {
        if (items.isEmpty()) {
            return "You have no recurring payments due in the next 7 days.";
        }

        List<String> highlights = items.stream()
                .limit(3)
                .map(item -> item.getEntityName() + " on " + item.getNextExpectedDate())
                .toList();
        return "You have " + items.size() + " recurring payments due in the next 7 days: " + String.join(", ", highlights) + ".";
    }

    private String summarizeRecurringCounts(String query, DashboardRecurringSummaryResponse summary) {
        String normalized = query.toLowerCase();
        if (normalized.contains("due soon") || normalized.contains("upcoming")) {
            return "You have " + summary.getDueSoonCount() + " recurring payments due in the next 7 days.";
        }
        return "You have " + summary.getTotalRecurring() + " recurring payments tracked, with " + summary.getDueSoonCount() + " due in the next 7 days.";
    }

    private String summarizeRecurringList(List<RecurringListItemResponse> items, long totalItems) {
        if (items.isEmpty()) {
            return "You do not have any recurring payments tracked yet.";
        }

        List<String> highlights = items.stream()
                .limit(3)
                .map(item -> item.getEntityName() + " every " + item.getFrequencyDays() + " days")
                .toList();
        return "You have " + totalItems + " recurring payments tracked. Recent ones: " + String.join(", ", highlights) + ".";
    }

    private String summarizeAnomalyCounts(DashboardAnomalySummaryResponse summary) {
        return "You have " + summary.getTotalAnomalies() + " anomalies in total: "
                + summary.getHighSeverityCount() + " high, "
                + summary.getMediumSeverityCount() + " medium, and "
                + summary.getLowSeverityCount() + " low severity.";
    }

    private String summarizeAnomalyList(List<AnomalyListItemResponse> items,
                                        long totalItems,
                                        String severity,
                                        String anomalyType) {
        if (items.isEmpty()) {
            return "No anomalies matched that filter.";
        }

        String scope = severity != null ? severity.toLowerCase() + " severity " : "";
        if (anomalyType != null) {
            scope = anomalyType.toLowerCase().replace('_', ' ') + " ";
        }

        List<String> highlights = items.stream()
                .limit(3)
                .map(item -> item.getAnomalyType() + " on " + item.getTxnDate())
                .toList();
        return "I found " + totalItems + " " + scope + "anomalies. Recent ones: " + String.join(", ", highlights) + ".";
    }

    private Map<String, Object> toRecurringMap(RecurringListItemResponse item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("entityId", item.getEntityId());
        map.put("entityName", item.getEntityName());
        map.put("categoryId", item.getCategoryId());
        map.put("categoryName", item.getCategoryName());
        map.put("frequencyDays", item.getFrequencyDays());
        map.put("avgAmount", item.getAvgAmount());
        map.put("lastSeen", item.getLastSeen());
        map.put("nextExpectedDate", item.getNextExpectedDate());
        map.put("createdAt", item.getCreatedAt());
        return map;
    }

    private Map<String, Object> toRecurringSummaryMap(DashboardRecurringSummaryResponse summary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalRecurring", summary.getTotalRecurring());
        map.put("dueSoonCount", summary.getDueSoonCount());
        map.put("recurringItems", summary.getRecurringItems().stream().map(this::toRecurringMap).toList());
        return map;
    }

    private Map<String, Object> toAnomalyMap(AnomalyListItemResponse item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("anomalyType", item.getAnomalyType());
        map.put("description", item.getDescription());
        map.put("severity", item.getSeverity());
        map.put("txnId", item.getTxnId());
        map.put("txnDate", item.getTxnDate());
        map.put("amount", item.getAmount());
        map.put("entityId", item.getEntityId());
        map.put("entityName", item.getEntityName());
        map.put("createdAt", item.getCreatedAt());
        return map;
    }

    private Map<String, Object> toAnomalySummaryMap(DashboardAnomalySummaryResponse summary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalAnomalies", summary.getTotalAnomalies());
        map.put("highSeverityCount", summary.getHighSeverityCount());
        map.put("mediumSeverityCount", summary.getMediumSeverityCount());
        map.put("lowSeverityCount", summary.getLowSeverityCount());
        map.put("recentAnomalies", summary.getRecentAnomalies().stream().map(this::toAnomalyMap).toList());
        return map;
    }

    private boolean isRecurringQuery(String normalized) {
        return normalized.contains("recurring")
                || normalized.contains("subscription")
                || normalized.contains("subscriptions")
                || normalized.contains("autopay")
                || normalized.contains("autopays")
                || normalized.contains("due soon");
    }

    private boolean isAnomalyQuery(String normalized) {
        return normalized.contains("anomaly") || normalized.contains("anomalies");
    }

    private boolean requestsCount(String normalized) {
        return normalized.contains("how many")
                || normalized.contains("count")
                || normalized.contains("total")
                || normalized.contains("number of");
    }

    private boolean requestsList(String normalized) {
        return normalized.contains("show")
                || normalized.contains("list")
                || normalized.contains("get")
                || normalized.contains("which")
                || normalized.contains("what");
    }

    private String extractAnomalySeverity(String normalized) {
        if (normalized.contains("high severity") || normalized.matches(".*\\bhigh\\b.*")) {
            return "HIGH";
        }
        if (normalized.contains("medium severity") || normalized.matches(".*\\bmedium\\b.*")) {
            return "MEDIUM";
        }
        if (normalized.contains("low severity") || normalized.matches(".*\\blow\\b.*")) {
            return "LOW";
        }
        return null;
    }

    private String extractAnomalyType(String normalized) {
        if (normalized.contains("high spend")) {
            return "HIGH_SPEND";
        }
        if (normalized.contains("entity spike") || normalized.contains("merchant spike")) {
            return "ENTITY_SPIKE";
        }
        if (normalized.contains("first high")) {
            return "FIRST_HIGH_TXN";
        }
        return null;
    }

    private String buildLLMPrompt(String query) {

        return "You are a personal finance assistant.\n\n" +
                "Answer clearly and concisely:\n\n" +
                query;
    }

    private double scoreNLQ(String q) {

        double score = 0;

        if (q.matches(".*\\b(how much|total|sum|spent|expense|income|balance|credit|credits|debit|debits)\\b.*")) score += 0.6;
        if (q.matches(".*\\b(top|highest|lowest|max|min|most|least)\\b.*")) score += 0.2;
        if (q.matches(".*\\b(compare|trend|increase|decrease)\\b.*")) score += 0.2;
        if (q.matches(".*\\b(month|today|week|year|quarter|q[1-4]|january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)\\b.*")) score += 0.2;
        if (q.matches(".*\\b20\\d{2}-\\d{2}-\\d{2}\\b.*")) score += 0.2;
        if (q.matches(".*\\b(transactions|transaction|payments|payment|records|record|debits|credits)\\b.*")) score += 0.2;
        if (q.matches(".*\\b(transactions|transaction|payments|payment|records|record|debits|credits)\\b.*")
                && q.matches(".*\\b(today|yesterday|last|this|month|week|year|quarter|q[1-4]|january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec|between|from)\\b.*")) score += 0.3;
        if (q.matches(".*\\b(show|list|get|latest|recent)\\b.*") && q.matches(".*\\b(transactions|transaction|payments|payment|records|record|debits|credits)\\b.*")) score += 0.5;

        return Math.min(score, 1.0);
    }

    private double scoreRAG(String q) {

        double score = 0;

        if (q.matches(".*\\b(find|search)\\b.*")) score += 0.2;
        if (q.matches(".*\\b(transactions|payments|history|records)\\b.*")) score += 0.2;
        if (q.matches(".*\\b(related|similar|like)\\b.*")) score += 0.6;

        return Math.min(score, 1.0);
    }

    private double scoreLLM(String q) {

        double score = 0;

        if (q.matches(".*\\b(why|explain|reason|analyze)\\b.*")) score += 0.4;
        if (q.matches(".*\\b(suggest|recommend|advice|improve)\\b.*")) score += 0.3;
        if (q.matches(".*\\b(am i|should i|can i)\\b.*")) score += 0.3;

        return Math.min(score, 1.0);
    }

    private enum DirectQueryKind {
        RECURRING_DUE_SOON_LIST,
        RECURRING_SUMMARY,
        RECURRING_LIST,
        ANOMALY_SUMMARY,
        ANOMALY_LIST
    }

    private static class DirectQueryRoute {
        private final DirectQueryKind kind;
        private final ClassificationResult classificationResult;

        private DirectQueryRoute(DirectQueryKind kind, ClassificationResult classificationResult) {
            this.kind = kind;
            this.classificationResult = classificationResult;
        }

        private DirectQueryKind getKind() {
            return kind;
        }

        private ClassificationResult getClassificationResult() {
            return classificationResult;
        }
    }
}
