package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class QueryTimeWindow {

    private final Integer year;
    private final Integer month;
    private final Integer day;
    private final Integer lastNMonths;
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final boolean today;
    private final boolean yesterday;
    private final boolean lastMonth;
    private final boolean thisMonth;
    private final boolean thisWeek;
    private final boolean lastWeek;
}
