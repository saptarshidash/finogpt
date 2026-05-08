package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DashboardRecurringSummaryResponse {

    private final Long totalRecurring;
    private final Long dueSoonCount;
    private final List<RecurringListItemResponse> recurringItems;
}
