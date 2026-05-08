package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.BreakdownItemResponse;
import com.saptarshi.finogpt.dto.DailyCashflowPointResponse;
import com.saptarshi.finogpt.dto.DashboardAnomalySummaryResponse;
import com.saptarshi.finogpt.dto.DashboardOverviewResponse;
import com.saptarshi.finogpt.dto.DashboardRecurringSummaryResponse;
import com.saptarshi.finogpt.dto.MonthlyTrendPointResponse;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import com.saptarshi.finogpt.service.AnomalyReadService;
import com.saptarshi.finogpt.service.DashboardService;
import com.saptarshi.finogpt.service.RecurringReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AnomalyReadService anomalyReadService;
    private final RecurringReadService recurringReadService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> overview(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "this_month") String period
    ) {
        return ResponseEntity.ok(ApiResponse.<DashboardOverviewResponse>builder()
                .success(true)
                .message("Dashboard overview loaded")
                .data(dashboardService.getOverview(authenticatedUser.getUserId(), period))
                .build());
    }

    @GetMapping("/spend-trend")
    public ResponseEntity<ApiResponse<List<MonthlyTrendPointResponse>>> spendTrend(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "6") int months
    ) {
        return ResponseEntity.ok(ApiResponse.<List<MonthlyTrendPointResponse>>builder()
                .success(true)
                .message("Spend trend loaded")
                .data(dashboardService.getSpendTrend(authenticatedUser.getUserId(), months))
                .build());
    }

    @GetMapping("/cashflow-daily")
    public ResponseEntity<ApiResponse<List<DailyCashflowPointResponse>>> cashflowDaily(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(ApiResponse.<List<DailyCashflowPointResponse>>builder()
                .success(true)
                .message("Daily cashflow loaded")
                .data(dashboardService.getCashflowDaily(authenticatedUser.getUserId(), days))
                .build());
    }

    @GetMapping("/top-merchants")
    public ResponseEntity<ApiResponse<List<BreakdownItemResponse>>> topMerchants(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.<List<BreakdownItemResponse>>builder()
                .success(true)
                .message("Top merchants loaded")
                .data(dashboardService.getTopMerchants(authenticatedUser.getUserId(), year, month, limit))
                .build());
    }

    @GetMapping("/top-categories")
    public ResponseEntity<ApiResponse<List<BreakdownItemResponse>>> topCategories(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.<List<BreakdownItemResponse>>builder()
                .success(true)
                .message("Top categories loaded")
                .data(dashboardService.getTopCategories(authenticatedUser.getUserId(), year, month, limit))
                .build());
    }

    @GetMapping("/anomalies/summary")
    public ResponseEntity<ApiResponse<DashboardAnomalySummaryResponse>> anomalySummary(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.<DashboardAnomalySummaryResponse>builder()
                .success(true)
                .message("Anomaly summary loaded")
                .data(anomalyReadService.getDashboardSummary(authenticatedUser.getUserId(), limit))
                .build());
    }

    @GetMapping("/recurring/summary")
    public ResponseEntity<ApiResponse<DashboardRecurringSummaryResponse>> recurringSummary(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.<DashboardRecurringSummaryResponse>builder()
                .success(true)
                .message("Recurring summary loaded")
                .data(recurringReadService.getDashboardSummary(authenticatedUser.getUserId(), limit))
                .build());
    }
}
