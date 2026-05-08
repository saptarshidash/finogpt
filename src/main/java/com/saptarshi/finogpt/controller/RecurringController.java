package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.PagedResponse;
import com.saptarshi.finogpt.dto.RecurringDetailResponse;
import com.saptarshi.finogpt.dto.RecurringListItemResponse;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import com.saptarshi.finogpt.service.RecurringReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recurring")
@RequiredArgsConstructor
public class RecurringController {

    private final RecurringReadService recurringReadService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<RecurringListItemResponse>>> list(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.<PagedResponse<RecurringListItemResponse>>builder()
                .success(true)
                .message("Recurring items loaded")
                .data(recurringReadService.listRecurring(
                        authenticatedUser.getUserId(),
                        entityId,
                        categoryId,
                        page,
                        size
                ))
                .build());
    }

    @GetMapping("/{recurringId}")
    public ResponseEntity<ApiResponse<RecurringDetailResponse>> detail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long recurringId
    ) {
        return ResponseEntity.ok(ApiResponse.<RecurringDetailResponse>builder()
                .success(true)
                .message("Recurring item loaded")
                .data(recurringReadService.getRecurring(authenticatedUser.getUserId(), recurringId))
                .build());
    }
}
