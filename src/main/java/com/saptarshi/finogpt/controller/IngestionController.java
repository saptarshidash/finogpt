package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.IngestionJobDetailResponse;
import com.saptarshi.finogpt.dto.IngestionJobSummaryResponse;
import com.saptarshi.finogpt.dto.IngestionResponse;
import com.saptarshi.finogpt.dto.IngestionUploadResponse;
import com.saptarshi.finogpt.dto.PagedResponse;
import com.saptarshi.finogpt.dto.TransactionListItemResponse;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import com.saptarshi.finogpt.service.IngestionReadService;
import com.saptarshi.finogpt.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;
    private final IngestionReadService ingestionReadService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<IngestionUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam("mobile") String mobile
    ) {
        IngestionResponse response = ingestionService.startIngestion(file, authenticatedUser.getUserId(), mobile);

        return ResponseEntity.ok(ApiResponse.<IngestionUploadResponse>builder()
                .success(true)
                .message("Ingestion started")
                .data(IngestionUploadResponse.builder()
                        .jobId(response.getJobId())
                        .status(response.getStatus())
                        .message(response.getMessage())
                        .build())
                .build());
    }

    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<PagedResponse<IngestionJobSummaryResponse>>> jobs(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.<PagedResponse<IngestionJobSummaryResponse>>builder()
                .success(true)
                .message("Ingestion jobs loaded")
                .data(ingestionReadService.listJobs(authenticatedUser.getUserId(), page, size))
                .build());
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<IngestionJobDetailResponse>> job(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String jobId
    ) {
        return ResponseEntity.ok(ApiResponse.<IngestionJobDetailResponse>builder()
                .success(true)
                .message("Ingestion job loaded")
                .data(ingestionReadService.getJob(authenticatedUser.getUserId(), jobId))
                .build());
    }

    @GetMapping("/jobs/{jobId}/transactions")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionListItemResponse>>> jobTransactions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.<PagedResponse<TransactionListItemResponse>>builder()
                .success(true)
                .message("Ingestion job transactions loaded")
                .data(ingestionReadService.getJobTransactions(authenticatedUser.getUserId(), jobId, page, size))
                .build());
    }
}
