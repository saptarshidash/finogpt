package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClarificationOption {

    private final String type;
    private final Long id;
    private final String label;
    private final Integer score;
}
