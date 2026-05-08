package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class AnalyticsPointResponse {

    private final LocalDate periodStart;
    private final Integer year;
    private final Integer month;
    private final BigDecimal value;
}
