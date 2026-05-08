package com.saptarshi.finogpt.dto;

import com.saptarshi.finogpt.enums.QueryExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class QueryExecutionResult {

    private final String answer;
    private final List<Map<String, Object>> data;
    private final QueryContext context;
    private final QueryExecutionStatus status;
}
