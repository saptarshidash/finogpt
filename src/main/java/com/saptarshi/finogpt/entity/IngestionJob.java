package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ingestion_jobs")
@Data
public class IngestionJob {

    @Id
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "mobile_number")
    private String mobileNumber;


    @Column(name = "total_records")
    private Integer totalRecords = 0;

    @Column(name = "completion_signal_received")
    private boolean completionSignalReceived = false;

    @Column(name = "processed_count")
    private Integer processedCount = 0;

    @Column(name = "failed_count")
    private Integer failedCount = 0;
    

    @Column(name = "status")
    private String status = "PROCESSING";
    // PROCESSING / COMPLETED / FAILED / PARTIAL_SUCCESS
    

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
