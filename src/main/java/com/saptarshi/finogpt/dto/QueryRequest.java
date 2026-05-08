package com.saptarshi.finogpt.dto;

import lombok.Data;

import java.util.List;

@Data
public class QueryRequest {

    private Long userId;
    private String query;
    private String clarificationToken;
    private Long selectedEntityId;
    private Long selectedCategoryId;
    private List<Long> selectedEntityIds;
    private List<Long> selectedCategoryIds;
}
