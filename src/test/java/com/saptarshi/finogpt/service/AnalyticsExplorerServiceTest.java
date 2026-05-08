package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.AnalyticsSeriesResponse;
import com.saptarshi.finogpt.dto.BreakdownItemResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalyticsExplorerServiceTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
    private final AnalyticsExplorerService analyticsExplorerService = new AnalyticsExplorerService(jdbcTemplate);

    @Test
    void monthlySummaryClipsToTheRequestedDateRangeAndGroupsByMonth() {
        Mockito.when(jdbcTemplate.query(
                        Mockito.anyString(),
                        Mockito.any(MapSqlParameterSource.class),
                        Mockito.<RowMapper<com.saptarshi.finogpt.dto.AnalyticsPointResponse>>any()))
                .thenReturn(List.of(
                        com.saptarshi.finogpt.dto.AnalyticsPointResponse.builder()
                                .periodStart(LocalDate.of(2026, 4, 1))
                                .year(2026)
                                .month(4)
                                .value(new BigDecimal("400.00"))
                                .build(),
                        com.saptarshi.finogpt.dto.AnalyticsPointResponse.builder()
                                .periodStart(LocalDate.of(2026, 5, 1))
                                .year(2026)
                                .month(5)
                                .value(new BigDecimal("700.00"))
                                .build()
                ));

        AnalyticsSeriesResponse response = analyticsExplorerService.getSummarySeries(
                1L,
                "monthly",
                "spend",
                LocalDate.of(2026, 4, 27),
                LocalDate.of(2026, 5, 7)
        );

        ArgumentCaptor<MapSqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        Mockito.verify(jdbcTemplate).query(
                Mockito.contains("FROM daily_summary"),
                parameterCaptor.capture(),
                Mockito.<RowMapper<com.saptarshi.finogpt.dto.AnalyticsPointResponse>>any()
        );

        assertEquals(LocalDate.of(2026, 4, 27), response.getFrom());
        assertEquals(LocalDate.of(2026, 5, 7), response.getTo());
        assertEquals(LocalDate.of(2026, 4, 27), parameterCaptor.getValue().getValue("fromDate"));
        assertEquals(LocalDate.of(2026, 5, 7), parameterCaptor.getValue().getValue("toDate"));
        assertEquals(LocalDate.of(2026, 4, 1), response.getPoints().get(0).getPeriodStart());
        assertEquals(new BigDecimal("400.00"), response.getPoints().get(0).getValue());
        assertEquals(LocalDate.of(2026, 5, 1), response.getPoints().get(1).getPeriodStart());
        assertEquals(new BigDecimal("700.00"), response.getPoints().get(1).getValue());
    }

    @Test
    void categoryBreakdownClipsToTheRequestedDateRange() {
        Mockito.when(jdbcTemplate.query(
                        Mockito.anyString(),
                        Mockito.any(MapSqlParameterSource.class),
                        Mockito.<RowMapper<BreakdownItemResponse>>any()))
                .thenReturn(List.of(
                        BreakdownItemResponse.builder()
                                .id(10L)
                                .name("Food")
                                .totalAmount(new BigDecimal("250.00"))
                                .txnCount(2L)
                                .build()
                ));

        var response = analyticsExplorerService.getCategoryBreakdown(
                1L,
                LocalDate.of(2026, 4, 27),
                LocalDate.of(2026, 4, 30),
                10,
                "amount"
        );

        ArgumentCaptor<MapSqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        Mockito.verify(jdbcTemplate).query(
                Mockito.contains("FROM transactions t"),
                parameterCaptor.capture(),
                Mockito.<RowMapper<BreakdownItemResponse>>any()
        );

        assertEquals(LocalDate.of(2026, 4, 27), response.getFrom());
        assertEquals(LocalDate.of(2026, 4, 30), response.getTo());
        assertEquals(LocalDate.of(2026, 4, 27), parameterCaptor.getValue().getValue("fromDate"));
        assertEquals(LocalDate.of(2026, 4, 30), parameterCaptor.getValue().getValue("toDate"));
        assertEquals(10L, response.getItems().get(0).getId());
        assertEquals(new BigDecimal("250.00"), response.getItems().get(0).getTotalAmount());
        assertEquals(2L, response.getItems().get(0).getTxnCount());
    }

    @Test
    void breakdownEndpointUsesExactDateRangeForEntityBreakdown() {
        Mockito.when(jdbcTemplate.query(
                        Mockito.anyString(),
                        Mockito.any(MapSqlParameterSource.class),
                        Mockito.<RowMapper<BreakdownItemResponse>>any()))
                .thenReturn(List.of(
                        BreakdownItemResponse.builder()
                                .id(21L)
                                .name("Amazon")
                                .totalAmount(new BigDecimal("125.00"))
                                .txnCount(1L)
                                .build()
                ));

        var response = analyticsExplorerService.getBreakdown(
                1L,
                "entity",
                LocalDate.of(2026, 4, 27),
                LocalDate.of(2026, 4, 30),
                10,
                "amount"
        );

        ArgumentCaptor<MapSqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        Mockito.verify(jdbcTemplate, Mockito.times(1)).query(
                Mockito.contains("JOIN entities e"),
                parameterCaptor.capture(),
                Mockito.<RowMapper<BreakdownItemResponse>>any()
        );

        assertEquals("entity", response.getDimension());
        assertEquals(LocalDate.of(2026, 4, 27), response.getFrom());
        assertEquals(LocalDate.of(2026, 4, 30), response.getTo());
        assertEquals(LocalDate.of(2026, 4, 27), parameterCaptor.getValue().getValue("fromDate"));
        assertEquals(LocalDate.of(2026, 4, 30), parameterCaptor.getValue().getValue("toDate"));
        assertEquals(21L, response.getItems().get(0).getId());
        assertEquals(new BigDecimal("125.00"), response.getItems().get(0).getTotalAmount());
    }
}
