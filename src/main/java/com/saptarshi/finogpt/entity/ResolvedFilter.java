package com.saptarshi.finogpt.entity;

import com.saptarshi.finogpt.enums.FilterType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResolvedFilter {

    private FilterType type; // ENTITY / CATEGORY
    private Long id;
    private String name;
}
