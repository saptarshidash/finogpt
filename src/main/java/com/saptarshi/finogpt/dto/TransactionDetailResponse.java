package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
public class TransactionDetailResponse {

    private final Long id;
    private final LocalDate txnDate;
    private final LocalTime txnTime;
    private final Long entityId;
    private final String entityName;
    private final Long categoryId;
    private final String categoryName;
    private final Long userCategoryId;
    private final BigDecimal amount;
    private final String txnType;
    private final String rawDetails;
    private final String source;
    private final String externalTxnId;
    private final UUID ingestionJobId;
    private final LocalDateTime createdAt;
}
