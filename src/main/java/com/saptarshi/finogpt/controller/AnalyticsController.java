package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.AnalyticsBreakdownResponse;
import com.saptarshi.finogpt.dto.AnalyticsSeriesResponse;
import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import com.saptarshi.finogpt.service.AnalyticsExplorerService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsExplorerService analyticsExplorerService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AnalyticsSeriesResponse>> summary(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "monthly") String grain,
            @RequestParam(defaultValue = "spend") String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.<AnalyticsSeriesResponse>builder()
                .success(true)
                .message("Analytics summary loaded")
                .data(analyticsExplorerService.getSummarySeries(
                        authenticatedUser.getUserId(),
                        grain,
                        metric,
                        from,
                        to
                ))
                .build());
    }

    @GetMapping("/entities")
    public ResponseEntity<ApiResponse<AnalyticsBreakdownResponse>> entities(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "amount") String sort
    ) {
        return ResponseEntity.ok(ApiResponse.<AnalyticsBreakdownResponse>builder()
                .success(true)
                .message("Entity analytics loaded")
                .data(analyticsExplorerService.getEntityBreakdown(
                        authenticatedUser.getUserId(),
                        from,
                        to,
                        limit,
                        sort
                ))
                .build());
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<AnalyticsBreakdownResponse>> categories(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "amount") String sort
    ) {
        return ResponseEntity.ok(ApiResponse.<AnalyticsBreakdownResponse>builder()
                .success(true)
                .message("Category analytics loaded")
                .data(analyticsExplorerService.getCategoryBreakdown(
                        authenticatedUser.getUserId(),
                        from,
                        to,
                        limit,
                        sort
                ))
                .build());
    }

    @GetMapping("/breakdown")
    public ResponseEntity<ApiResponse<AnalyticsBreakdownResponse>> breakdown(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam String dimension,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "amount") String sort
    ) {
        return ResponseEntity.ok(ApiResponse.<AnalyticsBreakdownResponse>builder()
                .success(true)
                .message("Analytics breakdown loaded")
                .data(analyticsExplorerService.getBreakdown(
                        authenticatedUser.getUserId(),
                        dimension,
                        from,
                        to,
                        limit,
                        sort
                ))
                .build());
    }
}
