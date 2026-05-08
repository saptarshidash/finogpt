package com.saptarshi.finogpt.dto;

import com.saptarshi.finogpt.entity.IngestionJob;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class IngestionJobSummaryResponse {

    private final String jobId;
    private final String status;
    private final Integer totalRecords;
    private final Integer processedCount;
    private final Integer failedCount;
    private final Integer progressPercent;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static IngestionJobSummaryResponse from(IngestionJob job) {
        return IngestionJobSummaryResponse.builder()
                .jobId(job.getJobId().toString())
                .status(job.getStatus())
                .totalRecords(job.getTotalRecords())
                .processedCount(job.getProcessedCount())
                .failedCount(job.getFailedCount())
                .progressPercent(progressPercent(job))
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    static int progressPercent(IngestionJob job) {
        int total = job.getTotalRecords() == null ? 0 : job.getTotalRecords();
        int done = safe(job.getProcessedCount()) + safe(job.getFailedCount());
        if (total <= 0) {
            return 0;
        }
        return Math.min(100, (int) Math.round((done * 100.0d) / total));
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
