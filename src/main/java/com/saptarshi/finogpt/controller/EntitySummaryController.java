package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.BreakdownItemResponse;
import com.saptarshi.finogpt.dto.DimensionSummaryDetailResponse;
import com.saptarshi.finogpt.dto.FilterOptionResponse;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import com.saptarshi.finogpt.service.DimensionSummaryService;
import com.saptarshi.finogpt.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/entities")
@RequiredArgsConstructor
public class EntitySummaryController {

    private final DimensionSummaryService dimensionSummaryService;
    private final MetadataService metadataService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<FilterOptionResponse>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.<List<FilterOptionResponse>>builder()
                .success(true)
                .message("Entities loaded")
                .data(metadataService.searchEntities(q, limit))
                .build());
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<List<BreakdownItemResponse>>> summary(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer months,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.<List<BreakdownItemResponse>>builder()
                .success(true)
                .message("Entity summary loaded")
                .data(dimensionSummaryService.listEntities(
                        authenticatedUser.getUserId(),
                        year,
                        month,
                        months,
                        limit
                ))
                .build());
    }

    @GetMapping("/{entityId}/summary")
    public ResponseEntity<ApiResponse<DimensionSummaryDetailResponse>> detail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long entityId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer months
    ) {
        return ResponseEntity.ok(ApiResponse.<DimensionSummaryDetailResponse>builder()
                .success(true)
                .message("Entity detail loaded")
                .data(dimensionSummaryService.getEntityDetail(
                        authenticatedUser.getUserId(),
                        entityId,
                        year,
                        month,
                        months
                ))
                .build());
    }
}
