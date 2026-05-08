package com.saptarshi.finogpt.dto;

import com.saptarshi.finogpt.enums.QueryType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ClassificationResult {

    private QueryType type;
    private double confidence;
    private boolean isHybrid;
}
