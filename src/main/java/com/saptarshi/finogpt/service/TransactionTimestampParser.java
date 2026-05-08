package com.saptarshi.finogpt.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

public final class TransactionTimestampParser {

    private TransactionTimestampParser() {
    }

    public static ParsedTimestamp parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("date is required");
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("date is required");
        }

        try {
            return new ParsedTimestamp(LocalDate.parse(trimmed), null);
        } catch (Exception ignored) {
            // Try full ISO datetime parsing next.
        }

        try {
            TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(
                    trimmed,
                    OffsetDateTime::from,
                    ZonedDateTime::from,
                    LocalDateTime::from
            );

            if (parsed instanceof OffsetDateTime) {
                OffsetDateTime offsetDateTime = (OffsetDateTime) parsed;
                return new ParsedTimestamp(offsetDateTime.toLocalDate(), offsetDateTime.toLocalTime());
            }

            if (parsed instanceof ZonedDateTime) {
                ZonedDateTime zonedDateTime = (ZonedDateTime) parsed;
                return new ParsedTimestamp(zonedDateTime.toLocalDate(), zonedDateTime.toLocalTime());
            }

            LocalDateTime localDateTime = (LocalDateTime) parsed;
            return new ParsedTimestamp(localDateTime.toLocalDate(), localDateTime.toLocalTime());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid transaction date: " + value, ex);
        }
    }

    public static final class ParsedTimestamp {
        private final LocalDate date;
        private final LocalTime time;

        public ParsedTimestamp(LocalDate date, LocalTime time) {
            this.date = date;
            this.time = time;
        }

        public LocalDate getDate() {
            return date;
        }

        public LocalTime getTime() {
            return time;
        }
    }
}
