package com.saptarshi.finogpt.dto;

import com.saptarshi.finogpt.entity.Transaction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
public class TransactionListItemResponse {

    private final Long id;
    private final LocalDate txnDate;
    private final LocalTime txnTime;
    private final Long entityId;
    private final String entityName;
    private final Long categoryId;
    private final String categoryName;
    private final BigDecimal amount;
    private final String txnType;
    private final String rawDetails;
    private final String source;
    private final String externalTxnId;
    private final LocalDateTime createdAt;

    public static TransactionListItemResponse from(Transaction transaction,
                                                   String entityName,
                                                   String categoryName) {
        return TransactionListItemResponse.builder()
                .id(transaction.getId())
                .txnDate(transaction.getTxnDate())
                .txnTime(transaction.getTxnTime())
                .entityId(transaction.getEntityId())
                .entityName(entityName)
                .categoryId(transaction.getCategoryId())
                .categoryName(categoryName)
                .amount(transaction.getAmount())
                .txnType(transaction.getTxnType() != null ? transaction.getTxnType().name() : null)
                .rawDetails(transaction.getRawDetails())
                .source(transaction.getSource())
                .externalTxnId(transaction.getExternalTxnId())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
