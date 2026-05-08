package com.saptarshi.finogpt.dto;

import com.saptarshi.finogpt.entity.IngestionJob;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class IngestionJobDetailResponse {

    private final String jobId;
    private final String status;
    private final String mobileNumber;
    private final Integer totalRecords;
    private final Integer processedCount;
    private final Integer failedCount;
    private final Integer progressPercent;
    private final boolean completionSignalReceived;
    private final String errorMessage;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static IngestionJobDetailResponse from(IngestionJob job) {
        return IngestionJobDetailResponse.builder()
                .jobId(job.getJobId().toString())
                .status(job.getStatus())
                .mobileNumber(job.getMobileNumber())
                .totalRecords(job.getTotalRecords())
                .processedCount(job.getProcessedCount())
                .failedCount(job.getFailedCount())
                .progressPercent(IngestionJobSummaryResponse.progressPercent(job))
                .completionSignalReceived(job.isCompletionSignalReceived())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
