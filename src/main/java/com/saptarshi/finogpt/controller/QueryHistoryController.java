package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.PagedResponse;
import com.saptarshi.finogpt.dto.QueryHistoryDetailResponse;
import com.saptarshi.finogpt.dto.QueryHistoryItemResponse;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import com.saptarshi.finogpt.service.QueryHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query/history")
@RequiredArgsConstructor
public class QueryHistoryController {

    private final QueryHistoryService queryHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<QueryHistoryItemResponse>>> list(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.<PagedResponse<QueryHistoryItemResponse>>builder()
                .success(true)
                .message("Query history loaded")
                .data(queryHistoryService.list(authenticatedUser.getUserId(), page, size))
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QueryHistoryDetailResponse>> detail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.<QueryHistoryDetailResponse>builder()
                .success(true)
                .message("Query history entry loaded")
                .data(queryHistoryService.get(authenticatedUser.getUserId(), id))
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long id
    ) {
        queryHistoryService.delete(authenticatedUser.getUserId(), id);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Query history entry deleted")
                .data(null)
                .build());
    }
}
