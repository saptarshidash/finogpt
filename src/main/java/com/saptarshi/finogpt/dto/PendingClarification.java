package com.saptarshi.finogpt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PendingClarification {

    private final String token;
    private final Long userId;
    private final String originalQuery;
    private final ClassificationResult classificationResult;
    private final QueryContext context;
    private final LocalDateTime createdAt;
}
