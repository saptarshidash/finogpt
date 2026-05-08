package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.QueryContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryDecisionServiceTest {

    private final QueryDecisionService queryDecisionService = new QueryDecisionService();

    @Test
    void requiresClarificationForAmbiguousEntityCandidates() {
        QueryContext ctx = new QueryContext();
        ctx.setSearchPhrase("mou");
        ctx.setAmbiguousEntity(true);
        ctx.setAmbiguousResolution(true);
        ctx.setEntityCandidateIds(List.of(62L, 319L, 446L));
        ctx.setEntityCandidateNames(List.of("Mou", "Moumita", "MOULESH"));
        ctx.setEntityCandidateScores(List.of(100, 85, 85));

        assertTrue(queryDecisionService.requiresClarification(ctx));
        assertEquals(
                "I found multiple merchants matching \"mou\". Select one or more to continue.",
                queryDecisionService.buildClarificationQuestion(ctx)
        );
    }
}
