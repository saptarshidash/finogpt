package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.TransactionEvent;
import com.saptarshi.finogpt.enums.TxnType;
import org.springframework.stereotype.Service;

@Service
public class TransactionEventValidator {

    public void validate(TransactionEvent event) {
        if (event == null) {
            throw TransactionProcessingException.nonRetryable("Transaction event is null");
        }

        if (isBlank(event.getJob_id())) {
            throw TransactionProcessingException.nonRetryable("job_id is required");
        }

        if (isJobCompleted(event)) {
            validateCompletionEvent(event);
            event.setType("JOB_COMPLETED");
            return;
        }

        if (isBlank(event.getDate())) {
            throw TransactionProcessingException.nonRetryable("date is required");
        }

        try {
            TransactionTimestampParser.parse(event.getDate());
        } catch (Exception ex) {
            throw TransactionProcessingException.nonRetryable("Invalid transaction date: " + event.getDate());
        }

        if (isBlank(event.getEntity())) {
            throw TransactionProcessingException.nonRetryable("entity is required");
        }

        if (event.getAmount() == null || event.getAmount() <= 0) {
            throw TransactionProcessingException.nonRetryable("amount must be greater than zero");
        }

        if (isBlank(event.getType())) {
            throw TransactionProcessingException.nonRetryable("type is required");
        }

        try {
            TxnType.valueOf(event.getType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw TransactionProcessingException.nonRetryable("Unsupported transaction type: " + event.getType());
        }

        event.setType(event.getType().trim().toUpperCase());
        event.setEntity(event.getEntity().trim());
        if (event.getCategory() != null) {
            event.setCategory(event.getCategory().trim());
        }
        if (event.getUtr_number() != null) {
            event.setUtr_number(event.getUtr_number().trim());
        }
    }

    private void validateCompletionEvent(TransactionEvent event) {
        if (event.getTotal_records() == null || event.getTotal_records() < 0) {
            throw TransactionProcessingException.nonRetryable("JOB_COMPLETED requires non-negative total_records");
        }
    }

    private boolean isJobCompleted(TransactionEvent event) {
        return "JOB_COMPLETED".equalsIgnoreCase(event.getType());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
