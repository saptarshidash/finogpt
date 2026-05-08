package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.AnomalyDetailResponse;
import com.saptarshi.finogpt.dto.AnomalyFiltersResponse;
import com.saptarshi.finogpt.dto.AnomalyListItemResponse;
import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.PagedResponse;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import com.saptarshi.finogpt.service.AnomalyReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyReadService anomalyReadService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AnomalyListItemResponse>>> list(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String anomalyType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.<PagedResponse<AnomalyListItemResponse>>builder()
                .success(true)
                .message("Anomalies loaded")
                .data(anomalyReadService.listAnomalies(
                        authenticatedUser.getUserId(),
                        severity,
                        anomalyType,
                        page,
                        size
                ))
                .build());
    }

    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<AnomalyFiltersResponse>> filters(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(ApiResponse.<AnomalyFiltersResponse>builder()
                .success(true)
                .message("Anomaly filters loaded")
                .data(anomalyReadService.getFilters(authenticatedUser.getUserId()))
                .build());
    }

    @GetMapping("/{anomalyId}")
    public ResponseEntity<ApiResponse<AnomalyDetailResponse>> detail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long anomalyId
    ) {
        return ResponseEntity.ok(ApiResponse.<AnomalyDetailResponse>builder()
                .success(true)
                .message("Anomaly loaded")
                .data(anomalyReadService.getAnomaly(authenticatedUser.getUserId(), anomalyId))
                .build());
    }
}
