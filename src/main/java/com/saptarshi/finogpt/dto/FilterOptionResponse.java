package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FilterOptionResponse {

    private final Long id;
    private final String name;
}
