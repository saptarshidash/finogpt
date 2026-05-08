package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryMetadataResponse {

    private final Long id;
    private final String name;
    private final String type;
}
