package com.saptarshi.finogpt.service;

public class TransactionProcessingException extends RuntimeException {

    private final boolean retryable;

    private TransactionProcessingException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public static TransactionProcessingException nonRetryable(String message) {
        return new TransactionProcessingException(message, false, null);
    }

    public static TransactionProcessingException retryable(String message, Throwable cause) {
        return new TransactionProcessingException(message, true, cause);
    }

    public boolean isRetryable() {
        return retryable;
    }
}
