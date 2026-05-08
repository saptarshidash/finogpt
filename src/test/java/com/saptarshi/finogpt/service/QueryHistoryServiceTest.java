package com.saptarshi.finogpt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saptarshi.finogpt.dto.QueryHistoryDetailResponse;
import com.saptarshi.finogpt.entity.InsightCache;
import com.saptarshi.finogpt.repository.InsightCacheRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class QueryHistoryServiceTest {

    private final InsightCacheRepository insightCacheRepository = Mockito.mock(InsightCacheRepository.class);
    private final QueryHistoryService queryHistoryService =
            new QueryHistoryService(insightCacheRepository, new ObjectMapper());

    @Test
    void returnsSavedQueryResponsePayloadForHistoryDetail() {
        InsightCache record = new InsightCache();
        record.setId(76L);
        record.setUserId(4L);
        record.setQuery("Show my top merchants this month.");
        record.setResponse("{\"answer\":\"Top merchants\",\"data\":[{\"entity_name\":\"Amazon\",\"total_amount\":1000}],\"decision\":{\"action\":\"EXECUTE\"},\"metadata\":{\"intent\":\"TOP\"}}");
        record.setCreatedAt(LocalDateTime.of(2026, 5, 8, 22, 46, 35));

        Mockito.when(insightCacheRepository.findByIdAndUserId(76L, 4L)).thenReturn(Optional.of(record));

        QueryHistoryDetailResponse response = queryHistoryService.get(4L, 76L);

        assertInstanceOf(Map.class, response.getResponse());
        Map<?, ?> payload = (Map<?, ?>) response.getResponse();
        assertEquals("Top merchants", payload.get("answer"));
        assertEquals("Show my top merchants this month.", response.getQuery());
    }
}
