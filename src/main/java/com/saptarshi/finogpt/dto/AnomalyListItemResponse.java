package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class AnomalyListItemResponse {

    private final Long id;
    private final String anomalyType;
    private final String description;
    private final String severity;
    private final Long txnId;
    private final LocalDate txnDate;
    private final BigDecimal amount;
    private final Long entityId;
    private final String entityName;
    private final LocalDateTime createdAt;
}
