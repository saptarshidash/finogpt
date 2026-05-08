package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MonthlyTrendPointResponse {

    private final Integer year;
    private final Integer month;
    private final BigDecimal totalSpend;
    private final BigDecimal totalCredit;
    private final Long txnCount;
}
