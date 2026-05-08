package com.saptarshi.finogpt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class SqlQueryPlan {

    private final String sql;
    private final Map<String, Object> parameters;
}
