package com.saptarshi.finogpt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saptarshi.finogpt.dto.PagedResponse;
import com.saptarshi.finogpt.dto.QueryHistoryDetailResponse;
import com.saptarshi.finogpt.dto.QueryHistoryItemResponse;
import com.saptarshi.finogpt.dto.QueryRequest;
import com.saptarshi.finogpt.dto.QueryResponse;
import com.saptarshi.finogpt.entity.InsightCache;
import com.saptarshi.finogpt.enums.QueryType;
import com.saptarshi.finogpt.repository.InsightCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryHistoryService {

    private final InsightCacheRepository insightCacheRepository;
    private final ObjectMapper objectMapper;

    public void save(Long userId, QueryRequest request, QueryResponse response) {
        String originalQuery = extractOriginalQuery(request, response);
        if (originalQuery == null || originalQuery.isBlank()) {
            return;
        }

        try {
            InsightCache record = new InsightCache();
            record.setUserId(userId);
            record.setYear(response.getMetadata() != null && response.getMetadata().getTimeWindow() != null
                    ? response.getMetadata().getTimeWindow().getYear()
                    : null);
            record.setMonth(response.getMetadata() != null && response.getMetadata().getTimeWindow() != null
                    ? response.getMetadata().getTimeWindow().getMonth()
                    : null);
            record.setQuery(originalQuery);
            record.setResponse(objectMapper.writeValueAsString(response));

            insightCacheRepository.save(record);
        } catch (Exception ex) {
            log.warn("Failed to persist query history for user {}: {}", userId, ex.getMessage());
        }
    }

    public PagedResponse<QueryHistoryItemResponse> list(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Page<InsightCache> records = insightCacheRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(safePage, safeSize)
        );

        Page<QueryHistoryItemResponse> mapped = records.map(this::toHistoryItem);

        return PagedResponse.<QueryHistoryItemResponse>builder()
                .items(mapped.getContent())
                .page(mapped.getNumber())
                .size(mapped.getSize())
                .totalItems(mapped.getTotalElements())
                .totalPages(mapped.getTotalPages())
                .build();
    }

    public QueryHistoryDetailResponse get(Long userId, Long id) {
        InsightCache record = insightCacheRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Query history entry not found"));

        return QueryHistoryDetailResponse.builder()
                .id(record.getId())
                .query(record.getQuery())
                .response(parseResponsePayload(record.getResponse()))
                .createdAt(record.getCreatedAt())
                .build();
    }

    public void delete(Long userId, Long id) {
        InsightCache record = insightCacheRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Query history entry not found"));

        insightCacheRepository.delete(record);
    }

    private QueryHistoryItemResponse toHistoryItem(InsightCache record) {
        JsonNode responseNode = parseResponse(record.getResponse());
        String answer = textValue(responseNode, "answer");
        QueryType queryType = null;

        JsonNode metadataNode = responseNode != null ? responseNode.get("metadata") : null;
        if (metadataNode != null && metadataNode.hasNonNull("queryType")) {
            try {
                queryType = QueryType.valueOf(metadataNode.get("queryType").asText());
            } catch (IllegalArgumentException ignored) {
                queryType = null;
            }
        }

        return QueryHistoryItemResponse.builder()
                .id(record.getId())
                .query(record.getQuery())
                .answerPreview(truncate(answer, 180))
                .queryType(queryType)
                .createdAt(record.getCreatedAt())
                .build();
    }

    private JsonNode parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return objectMapper.getNodeFactory().nullNode();
        }

        try {
            return objectMapper.readTree(response);
        } catch (Exception ex) {
            return objectMapper.createObjectNode().put("answer", response);
        }
    }

    private Object parseResponsePayload(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(response, Object.class);
        } catch (Exception ex) {
            return objectMapper.createObjectNode().put("answer", response);
        }
    }

    private String extractOriginalQuery(QueryRequest request, QueryResponse response) {
        if (request != null && request.getQuery() != null && !request.getQuery().isBlank()) {
            return request.getQuery();
        }

        if (response != null && response.getMetadata() != null) {
            return response.getMetadata().getOriginalQuery();
        }

        return null;
    }

    private String textValue(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
