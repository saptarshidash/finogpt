package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class DashboardOverviewResponse {

    private final String period;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal totalSpend;
    private final BigDecimal totalCredit;
    private final Long txnCount;
}
