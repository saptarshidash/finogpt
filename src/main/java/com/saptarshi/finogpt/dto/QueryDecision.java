package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class QueryDecision {

    private final String action;
    private final boolean executed;
    private final boolean requiresClarification;
    private final String reason;
    private final Double classificationConfidence;
    private final Double resolutionConfidence;
    private final String clarificationToken;
    private final String clarificationQuestion;
    private final List<ClarificationOption> clarificationOptions;
}
