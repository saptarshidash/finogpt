package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class QueryHistoryDetailResponse {

    private final Long id;
    private final String query;
    private final Object response;
    private final LocalDateTime createdAt;
}
