package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IngestionUploadResponse {

    private final String jobId;
    private final String status;
    private final String message;
}
