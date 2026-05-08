package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.UserCategoryMappingRequest;
import com.saptarshi.finogpt.dto.UserCategoryMappingResponse;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import com.saptarshi.finogpt.service.UserCategoryMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user-category-mappings")
@RequiredArgsConstructor
public class UserCategoryMappingController {

    private final UserCategoryMappingService userCategoryMappingService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserCategoryMappingResponse>>> list(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(ApiResponse.<List<UserCategoryMappingResponse>>builder()
                .success(true)
                .message("User category mappings loaded")
                .data(userCategoryMappingService.list(authenticatedUser.getUserId()))
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserCategoryMappingResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody UserCategoryMappingRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<UserCategoryMappingResponse>builder()
                .success(true)
                .message("User category mapping saved")
                .data(userCategoryMappingService.createOrUpdate(authenticatedUser.getUserId(), request))
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserCategoryMappingResponse>> update(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long id,
            @RequestBody UserCategoryMappingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.<UserCategoryMappingResponse>builder()
                .success(true)
                .message("User category mapping updated")
                .data(userCategoryMappingService.update(authenticatedUser.getUserId(), id, request))
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long id
    ) {
        userCategoryMappingService.delete(authenticatedUser.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("User category mapping deleted")
                .data(null)
                .build());
    }
}
