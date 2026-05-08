package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.UpdateSettingsRequest;
import com.saptarshi.finogpt.dto.UserSettingsResponse;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import com.saptarshi.finogpt.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UserSettingsService userSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserSettingsResponse>> get(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(ApiResponse.<UserSettingsResponse>builder()
                .success(true)
                .message("Settings loaded")
                .data(userSettingsService.get(authenticatedUser.getUserId()))
                .build());
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserSettingsResponse>> update(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody UpdateSettingsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.<UserSettingsResponse>builder()
                .success(true)
                .message("Settings updated")
                .data(userSettingsService.update(authenticatedUser.getUserId(), request))
                .build());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAllData(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        userSettingsService.deleteAllData(authenticatedUser.getUserId());

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("All user data deleted")
                .data(null)
                .build());
    }
}
