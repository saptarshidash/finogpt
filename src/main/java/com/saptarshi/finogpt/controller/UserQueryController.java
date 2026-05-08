package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.QueryRequest;
import com.saptarshi.finogpt.dto.QueryResponse;
import com.saptarshi.finogpt.security.AuthenticatedUser;
import com.saptarshi.finogpt.service.QueryWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class UserQueryController {

    private final QueryWorkspaceService queryWorkspaceService;

    @PostMapping
    public ResponseEntity<QueryResponse> handleQuery(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam String query
    ) {
        QueryResponse response = queryWorkspaceService.execute(authenticatedUser.getUserId(), query);
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<QueryResponse> handleQueryRequest(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody QueryRequest request
    ) {
        request.setUserId(authenticatedUser.getUserId());
        QueryResponse response = queryWorkspaceService.execute(request);
        return ResponseEntity.ok(response);
    }
}
