package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.TransactionEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionEventValidatorTest {

    private final TransactionEventValidator validator = new TransactionEventValidator();

    @Test
    void validatesAndNormalizesTransactionEvent() {
        TransactionEvent event = new TransactionEvent();
        event.setJob_id("job-1");
        event.setDate("2026-05-01");
        event.setEntity(" Amazon Pay ");
        event.setAmount(250.0);
        event.setType("debit");
        event.setUtr_number("  utr-123  ");

        validator.validate(event);

        assertEquals("DEBIT", event.getType());
        assertEquals("Amazon Pay", event.getEntity());
        assertEquals("utr-123", event.getUtr_number());
    }

    @Test
    void rejectsMissingJobId() {
        TransactionEvent event = new TransactionEvent();
        event.setDate("2026-05-01");
        event.setEntity("Zomato");
        event.setAmount(99.0);
        event.setType("DEBIT");

        assertThrows(TransactionProcessingException.class, () -> validator.validate(event));
    }

    @Test
    void validatesCompletionEvent() {
        TransactionEvent event = new TransactionEvent();
        event.setJob_id("job-1");
        event.setType("JOB_COMPLETED");
        event.setTotal_records(0);

        validator.validate(event);

        assertEquals("JOB_COMPLETED", event.getType());
    }

    @Test
    void acceptsIsoDateTimeInDateField() {
        TransactionEvent event = new TransactionEvent();
        event.setJob_id("job-1");
        event.setDate("2026-04-27T23:05:00");
        event.setEntity("Netflix");
        event.setAmount(499.0);
        event.setType("DEBIT");

        assertDoesNotThrow(() -> validator.validate(event));
    }
}
