package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class QueryResponse {

    private final String answer;
    private final List<Map<String, Object>> data;
    private final QueryDecision decision;
    private final QueryMetadata metadata;
}
