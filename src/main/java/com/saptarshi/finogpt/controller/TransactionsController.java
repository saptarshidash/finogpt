package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.PagedResponse;
import com.saptarshi.finogpt.dto.TransactionDetailResponse;
import com.saptarshi.finogpt.dto.TransactionFiltersResponse;
import com.saptarshi.finogpt.dto.TransactionListItemResponse;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import com.saptarshi.finogpt.service.TransactionReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionsController {

    private final TransactionReadService transactionReadService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TransactionListItemResponse>>> list(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "txnDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(ApiResponse.<PagedResponse<TransactionListItemResponse>>builder()
                .success(true)
                .message("Transactions loaded")
                .data(transactionReadService.listTransactions(
                        authenticatedUser.getUserId(),
                        from,
                        to,
                        type,
                        entityId,
                        categoryId,
                        page,
                        size,
                        sortBy,
                        sortDir
                ))
                .build());
    }

    @GetMapping("/{txnId}")
    public ResponseEntity<ApiResponse<TransactionDetailResponse>> detail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long txnId
    ) {
        return ResponseEntity.ok(ApiResponse.<TransactionDetailResponse>builder()
                .success(true)
                .message("Transaction loaded")
                .data(transactionReadService.getTransaction(authenticatedUser.getUserId(), txnId))
                .build());
    }

    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<TransactionFiltersResponse>> filters(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(ApiResponse.<TransactionFiltersResponse>builder()
                .success(true)
                .message("Transaction filters loaded")
                .data(transactionReadService.getFilters(authenticatedUser.getUserId()))
                .build());
    }
}
