package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.AppHelpLoadResponse;
import com.saptarshi.finogpt.service.AppHelpRagService;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app-help")
@RequiredArgsConstructor
public class AppHelpController {

    private final AppHelpRagService appHelpRagService;

    @PostMapping("/load")
    public ResponseEntity<ApiResponse<AppHelpLoadResponse>> load() {
        AppHelpLoadResponse response = appHelpRagService.startKnowledgeBaseLoad();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.<AppHelpLoadResponse>builder()
                .success(true)
                .message("App-help knowledge base load started")
                .data(response)
                .build());
    }

    @GetMapping("/load-status")
    public ResponseEntity<ApiResponse<AppHelpLoadResponse>> loadStatus() {
        AppHelpLoadResponse response = appHelpRagService.getLoadStatus();
        return ResponseEntity.ok(ApiResponse.<AppHelpLoadResponse>builder()
                .success(true)
                .message("App-help knowledge base load status")
                .data(response)
                .build());
    }
}
