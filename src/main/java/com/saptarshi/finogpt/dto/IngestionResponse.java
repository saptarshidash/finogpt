package com.saptarshi.finogpt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IngestionResponse {
    private String jobId;
    private String status;
    private String message;
}
