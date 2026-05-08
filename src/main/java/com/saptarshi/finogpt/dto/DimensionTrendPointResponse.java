package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class DimensionTrendPointResponse {

    private final Integer year;
    private final Integer month;
    private final BigDecimal totalAmount;
    private final Long txnCount;
}
