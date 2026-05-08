package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class AnalyticsSeriesResponse {

    private final String grain;
    private final String metric;
    private final LocalDate from;
    private final LocalDate to;
    private final List<AnalyticsPointResponse> points;
}
