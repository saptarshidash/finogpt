package com.saptarshi.finogpt.dto;


import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class QueryContext {

    private Long userId;

    // =============================
    // TIME
    // =============================
    private Integer year;
    private boolean isYearExplicit;
    private Integer month;
    private Integer day;

    private boolean isToday;
    private boolean isYesterday;

    private boolean isLastMonth;
    private boolean isThisMonth;
    private boolean isThisWeek;
    private boolean isLastWeek;

    private Integer lastNMonths;
    private boolean timeReferenced;
    private LocalDate fromDate;
    private LocalDate toDate;

    // =============================
    // LIMIT
    // =============================
    private Integer limit;

    // =============================
    // TRANSACTION DIRECTION
    // =============================
    private String txnDirection; // DEBIT / CREDIT / null

    // =============================
    // RAW NLP OUTPUT
    // =============================
    private String searchPhrase;
    private String rawEntity;
    private String rawCategory;

    // =============================
    // RESOLVED FILTERS
    // =============================
    private Long entityId;
    private String entityName;

    private Long categoryId;
    private String categoryName;

    private List<Long> entityIds;
    private List<String> entityNames;

    private List<Long> categoryIds;
    private List<String> categoryNames;

    private List<String> entityCandidateNames;
    private List<Integer> entityCandidateScores;
    private List<Long> entityCandidateIds;

    private List<String> categoryCandidateNames;
    private List<Integer> categoryCandidateScores;
    private List<Long> categoryCandidateIds;

    private boolean ambiguousEntity;
    private boolean ambiguousCategory;
    private boolean ambiguousResolution;
    private String requestedDimension; // ENTITY / CATEGORY / NONE
    private String resolutionMode; // ENTITY / CATEGORY / NONE

    // =============================
    // INTENT (🔥 VERY IMPORTANT)
    // =============================
    private String intent; // SUM, COUNT, AVG, TOP, MAX, MIN

    // =============================
    // DEBUG / TRACE
    // =============================
    private String originalQuery;
}
