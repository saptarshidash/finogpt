package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.ClassificationResult;
import com.saptarshi.finogpt.dto.PendingClarification;
import com.saptarshi.finogpt.dto.QueryContext;
import com.saptarshi.finogpt.dto.QueryExecutionResult;
import com.saptarshi.finogpt.dto.QueryRequest;
import com.saptarshi.finogpt.dto.QueryResponse;
import com.saptarshi.finogpt.enums.QueryExecutionStatus;
import com.saptarshi.finogpt.enums.QueryType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridQueryServiceTest {

    private final NLQService nlqService = Mockito.mock(NLQService.class);
    private final RagService ragService = Mockito.mock(RagService.class);
    private final LLMService llmService = Mockito.mock(LLMService.class);
    private final AppHelpRagService appHelpRagService = Mockito.mock(AppHelpRagService.class);
    private final ClarificationSessionService clarificationSessionService = Mockito.mock(ClarificationSessionService.class);
    private final HybridQueryService hybridQueryService = new HybridQueryService(
            nlqService,
            ragService,
            llmService,
            appHelpRagService,
            new QueryDecisionService(),
            clarificationSessionService,
            Mockito.mock(RecurringReadService.class),
            Mockito.mock(AnomalyReadService.class)
    );

    @Test
    void classifiesStructuredTransactionListQueriesAsNlq() {
        ClassificationResult result = hybridQueryService.classify("show latest 10 debit transactions in april");

        assertEquals(QueryType.NLQ, result.getType());
    }

    @Test
    void classifiesMonthScopedTransactionQueriesAsNlq() {
        ClassificationResult result = hybridQueryService.classify("transactions in april");

        assertEquals(QueryType.NLQ, result.getType());
    }

    @Test
    void classifiesQuarterScopedTransactionQueriesAsNlq() {
        ClassificationResult result = hybridQueryService.classify("transactions this quarter");

        assertEquals(QueryType.NLQ, result.getType());
    }

    @Test
    void keepsSemanticSimilarTransactionQueriesInRag() {
        ClassificationResult result = hybridQueryService.classify("show transactions related to travel");

        assertEquals(QueryType.RAG, result.getType());
    }

    @Test
    void returnsFeatureSpecificHelpForAnalyticsQuestions() {
        Mockito.when(appHelpRagService.answer("how to use analytics"))
                .thenReturn("Use analytics to review daily or monthly series for spend, credit, or transaction-count metrics.");

        QueryResponse response = hybridQueryService.handle(7L, "how to use analytics");

        assertTrue(response.getAnswer().contains("analytics"));
        assertTrue(response.getAnswer().contains("daily or monthly"));
        assertTrue(response.getAnswer().contains("spend, credit, or transaction-count"));
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void mapsAlertsFeatureQuestionsToAnomaliesGuidance() {
        Mockito.when(appHelpRagService.answer("what is the Alerts feature"))
                .thenReturn("This app uses anomalies for unusual activity rather than a separate alerts page.");

        QueryResponse response = hybridQueryService.handle(7L, "what is the Alerts feature");

        assertTrue(response.getAnswer().contains("anomalies"));
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void appliesMultiSelectEntityClarificationSubset() {
        QueryContext pendingContext = new QueryContext();
        pendingContext.setUserId(4L);
        pendingContext.setIntent("SUM");
        pendingContext.setRequestedDimension("NONE");
        pendingContext.setResolutionMode("ENTITY");
        pendingContext.setSearchPhrase("mou");
        pendingContext.setRawEntity("mou");
        pendingContext.setEntityId(62L);
        pendingContext.setEntityName("Mou");
        pendingContext.setEntityIds(List.of(62L));
        pendingContext.setEntityNames(List.of("Mou"));
        pendingContext.setEntityCandidateIds(List.of(62L, 319L, 446L));
        pendingContext.setEntityCandidateNames(List.of("Mou", "Moumita", "MOULESH"));
        pendingContext.setEntityCandidateScores(List.of(100, 85, 85));
        pendingContext.setAmbiguousEntity(true);
        pendingContext.setAmbiguousResolution(true);

        PendingClarification session = new PendingClarification(
                "token-1",
                4L,
                "how much did I spend on mou last year",
                new ClassificationResult(QueryType.NLQ, 0.8, false),
                pendingContext,
                LocalDateTime.now()
        );

        Mockito.when(clarificationSessionService.getSession("token-1", 4L)).thenReturn(session);
        Mockito.when(nlqService.executeResolvedQuery(Mockito.eq(session.getOriginalQuery()), Mockito.any(QueryContext.class)))
                .thenAnswer(invocation -> new QueryExecutionResult(
                        "resolved",
                        List.of(),
                        invocation.getArgument(1),
                        QueryExecutionStatus.EXECUTED
                ));

        QueryRequest request = new QueryRequest();
        request.setUserId(4L);
        request.setClarificationToken("token-1");
        request.setSelectedEntityIds(List.of(319L, 62L));

        QueryResponse response = hybridQueryService.handle(request);

        ArgumentCaptor<QueryContext> ctxCaptor = ArgumentCaptor.forClass(QueryContext.class);
        Mockito.verify(nlqService).executeResolvedQuery(Mockito.eq(session.getOriginalQuery()), ctxCaptor.capture());
        QueryContext clarified = ctxCaptor.getValue();

        assertEquals(List.of(62L, 319L), clarified.getEntityIds());
        assertEquals(List.of("Mou", "Moumita"), clarified.getEntityNames());
        assertNull(clarified.getEntityId());
        assertNull(clarified.getEntityName());
        assertEquals("NONE", clarified.getRequestedDimension());
        assertFalse(clarified.isAmbiguousResolution());
        assertEquals(List.of(62L, 319L), response.getMetadata().getEntityIds());
        assertEquals("EXECUTE", response.getDecision().getAction());
        Mockito.verify(clarificationSessionService).removeSession("token-1");
    }

    @Test
    void keepsClarificationOpenWhenSelectionIsInvalid() {
        QueryContext pendingContext = new QueryContext();
        pendingContext.setUserId(4L);
        pendingContext.setSearchPhrase("mou");
        pendingContext.setRawEntity("mou");
        pendingContext.setEntityCandidateIds(List.of(62L, 319L, 446L));
        pendingContext.setEntityCandidateNames(List.of("Mou", "Moumita", "MOULESH"));
        pendingContext.setEntityCandidateScores(List.of(100, 85, 85));
        pendingContext.setAmbiguousEntity(true);
        pendingContext.setAmbiguousResolution(true);

        PendingClarification session = new PendingClarification(
                "token-2",
                4L,
                "how much did I spend on mou last year",
                new ClassificationResult(QueryType.NLQ, 0.8, false),
                pendingContext,
                LocalDateTime.now()
        );

        Mockito.when(clarificationSessionService.getSession("token-2", 4L)).thenReturn(session);

        QueryRequest request = new QueryRequest();
        request.setUserId(4L);
        request.setClarificationToken("token-2");
        request.setSelectedEntityIds(List.of(999L));

        QueryResponse response = hybridQueryService.handle(request);

        assertEquals("CLARIFY", response.getDecision().getAction());
        assertTrue(response.getDecision().isRequiresClarification());
        assertEquals("Please choose one or more of the provided clarification options.", response.getAnswer());
        Mockito.verifyNoInteractions(nlqService);
    }
}
