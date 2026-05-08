package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class TransactionFiltersResponse {

    private final LocalDate minDate;
    private final LocalDate maxDate;
    private final List<FilterOptionResponse> entities;
    private final List<FilterOptionResponse> categories;
    private final List<String> types;
}
