package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.entity.Transaction;
import com.saptarshi.finogpt.enums.TxnType;
import com.saptarshi.finogpt.repository.SummaryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;

class SummaryServiceTest {

    private final SummaryRepository summaryRepository = Mockito.mock(SummaryRepository.class);
    private final SummaryService summaryService = new SummaryService(summaryRepository);

    @Test
    void creditTransactionsDoNotUpdateCategoryOrEntitySummaries() {
        Transaction txn = new Transaction();
        txn.setUserId(7L);
        txn.setTxnDate(LocalDate.of(2026, 5, 1));
        txn.setAmount(new BigDecimal("250.00"));
        txn.setTxnType(TxnType.CREDIT);
        txn.setCategoryId(11L);
        txn.setEntityId(22L);

        summaryService.update(txn);

        Mockito.verify(summaryRepository).upsertDaily(7L, LocalDate.of(2026, 5, 1), new BigDecimal("250.00"), "CREDIT");
        Mockito.verify(summaryRepository).upsertMonthly(7L, 2026, 5, new BigDecimal("250.00"), "CREDIT");
        Mockito.verify(summaryRepository, Mockito.never()).upsertCategory(Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.any());
        Mockito.verify(summaryRepository, Mockito.never()).upsertEntity(Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.any());
    }

    @Test
    void rebuildMonthRefreshesAllSummaryTablesFromTransactions() {
        summaryService.rebuildMonth(9L, 2026, 4);

        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);

        Mockito.verify(summaryRepository).deleteDailyRange(9L, startDate, endDate);
        Mockito.verify(summaryRepository).deleteMonthly(9L, 2026, 4);
        Mockito.verify(summaryRepository).deleteCategory(9L, 2026, 4);
        Mockito.verify(summaryRepository).deleteEntity(9L, 2026, 4);
        Mockito.verify(summaryRepository).rebuildDailyRange(9L, startDate, endDate);
        Mockito.verify(summaryRepository).rebuildMonthly(9L, 2026, 4, startDate, endDate);
        Mockito.verify(summaryRepository).rebuildCategory(9L, 2026, 4, startDate, endDate);
        Mockito.verify(summaryRepository).rebuildEntity(9L, 2026, 4, startDate, endDate);
    }
}
