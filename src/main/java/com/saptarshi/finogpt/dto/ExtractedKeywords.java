package com.saptarshi.finogpt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExtractedKeywords {
    private String entity;
    private String category;
}
