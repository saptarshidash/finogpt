package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnomalyFilterOptionResponse {

    private final String value;
    private final Long count;
}
