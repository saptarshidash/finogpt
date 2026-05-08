package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.QueryRequest;
import com.saptarshi.finogpt.dto.QueryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueryWorkspaceService {

    private final HybridQueryService hybridQueryService;
    private final QueryHistoryService queryHistoryService;

    public QueryResponse execute(Long userId, String query) {
        QueryRequest request = new QueryRequest();
        request.setUserId(userId);
        request.setQuery(query);
        return execute(request);
    }

    public QueryResponse execute(QueryRequest request) {
        QueryResponse response = hybridQueryService.handle(request);
        queryHistoryService.save(request.getUserId(), request, response);
        return response;
    }
}
