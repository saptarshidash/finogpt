package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class AnalyticsBreakdownResponse {

    private final String dimension;
    private final LocalDate from;
    private final LocalDate to;
    private final String sort;
    private final List<BreakdownItemResponse> items;
}
