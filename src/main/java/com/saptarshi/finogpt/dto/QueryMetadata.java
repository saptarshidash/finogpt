package com.saptarshi.finogpt.dto;

import com.saptarshi.finogpt.enums.QueryType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class QueryMetadata {

    private final String originalQuery;
    private final QueryType queryType;
    private final double classificationConfidence;
    private final boolean hybrid;
    private final String intent;
    private final String txnDirection;
    private final Integer limit;
    private final String requestedDimension;
    private final String resolutionMode;
    private final boolean ambiguousResolution;
    private final boolean ambiguousEntity;
    private final boolean ambiguousCategory;
    private final Double resolutionConfidence;
    private final List<Long> entityIds;
    private final List<String> entityNames;
    private final List<Long> entityCandidateIds;
    private final List<String> entityCandidateNames;
    private final List<Integer> entityCandidateScores;
    private final List<Long> categoryIds;
    private final List<String> categoryNames;
    private final List<Long> categoryCandidateIds;
    private final List<String> categoryCandidateNames;
    private final List<Integer> categoryCandidateScores;
    private final QueryTimeWindow timeWindow;
}
