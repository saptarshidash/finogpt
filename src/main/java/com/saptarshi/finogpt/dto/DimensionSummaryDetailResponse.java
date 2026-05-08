package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class DimensionSummaryDetailResponse {

    private final Long id;
    private final String name;
    private final String dimension;
    private final String period;
    private final BigDecimal totalAmount;
    private final Long txnCount;
    private final List<DimensionTrendPointResponse> trend;
}
