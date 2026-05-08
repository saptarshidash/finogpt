package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.UserSummaryResponse;
import com.saptarshi.finogpt.entity.User;
import com.saptarshi.finogpt.repository.UserRepository;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<UserSummaryResponse>> currentUser(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        User user = userRepository.findById(authenticatedUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return ResponseEntity.ok(ApiResponse.<UserSummaryResponse>builder()
                .success(true)
                .message("Current user loaded")
                .data(UserSummaryResponse.from(user))
                .build());
    }
}
