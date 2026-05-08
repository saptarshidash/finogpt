package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class RecurringDetailResponse {

    private final Long id;
    private final Long entityId;
    private final String entityName;
    private final Long categoryId;
    private final String categoryName;
    private final Integer frequencyDays;
    private final BigDecimal avgAmount;
    private final LocalDate lastSeen;
    private final LocalDate nextExpectedDate;
    private final LocalDateTime createdAt;
    private final List<TransactionListItemResponse> recentTransactions;
}
