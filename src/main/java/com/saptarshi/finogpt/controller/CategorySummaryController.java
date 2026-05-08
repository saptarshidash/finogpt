package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.BreakdownItemResponse;
import com.saptarshi.finogpt.dto.CategoryMetadataResponse;
import com.saptarshi.finogpt.dto.DimensionSummaryDetailResponse;
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
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategorySummaryController {

    private final DimensionSummaryService dimensionSummaryService;
    private final MetadataService metadataService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryMetadataResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.<List<CategoryMetadataResponse>>builder()
                .success(true)
                .message("Categories loaded")
                .data(metadataService.listCategories())
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
                .message("Category summary loaded")
                .data(dimensionSummaryService.listCategories(
                        authenticatedUser.getUserId(),
                        year,
                        month,
                        months,
                        limit
                ))
                .build());
    }

    @GetMapping("/{categoryId}/summary")
    public ResponseEntity<ApiResponse<DimensionSummaryDetailResponse>> detail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long categoryId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer months
    ) {
        return ResponseEntity.ok(ApiResponse.<DimensionSummaryDetailResponse>builder()
                .success(true)
                .message("Category detail loaded")
                .data(dimensionSummaryService.getCategoryDetail(
                        authenticatedUser.getUserId(),
                        categoryId,
                        year,
                        month,
                        months
                ))
                .build());
    }
}
