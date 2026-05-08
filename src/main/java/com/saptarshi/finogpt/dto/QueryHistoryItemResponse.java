package com.saptarshi.finogpt.dto;

import com.saptarshi.finogpt.enums.QueryType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class QueryHistoryItemResponse {

    private final Long id;
    private final String query;
    private final String answerPreview;
    private final QueryType queryType;
    private final LocalDateTime createdAt;
}
