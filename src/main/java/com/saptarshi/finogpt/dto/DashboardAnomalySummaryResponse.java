package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DashboardAnomalySummaryResponse {

    private final Long totalAnomalies;
    private final Long highSeverityCount;
    private final Long mediumSeverityCount;
    private final Long lowSeverityCount;
    private final List<AnomalyListItemResponse> recentAnomalies;
}
