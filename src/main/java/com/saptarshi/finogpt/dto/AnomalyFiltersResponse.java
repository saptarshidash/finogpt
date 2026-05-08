package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AnomalyFiltersResponse {

    private final List<AnomalyFilterOptionResponse> severities;
    private final List<AnomalyFilterOptionResponse> anomalyTypes;
}
