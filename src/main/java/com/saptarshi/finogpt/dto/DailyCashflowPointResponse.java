package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class DailyCashflowPointResponse {

    private final LocalDate date;
    private final BigDecimal totalDebit;
    private final BigDecimal totalCredit;
    private final Long txnCount;
}
