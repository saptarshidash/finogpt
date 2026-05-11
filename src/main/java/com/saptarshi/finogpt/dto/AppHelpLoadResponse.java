package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AppHelpLoadResponse {

    private final String state;
    private final String sourcePath;
    private final String documentName;
    private final int totalChunks;
    private final int processedChunks;
    private final int createdChunks;
    private final int updatedChunks;
    private final int unchangedChunks;
    private final int deletedChunks;
    private final double percentComplete;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String errorMessage;
}
