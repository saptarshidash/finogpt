package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class AnomalyDetailResponse {

    private final Long id;
    private final String anomalyType;
    private final String description;
    private final String severity;
    private final Long txnId;
    private final LocalDate txnDate;
    private final BigDecimal amount;
    private final String txnType;
    private final Long entityId;
    private final String entityName;
    private final Long categoryId;
    private final String categoryName;
    private final String rawDetails;
    private final String source;
    private final LocalDateTime createdAt;
}
