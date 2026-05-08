package com.saptarshi.finogpt.service;


import com.saptarshi.finogpt.entity.Transaction;
import com.saptarshi.finogpt.enums.TxnType;
import com.saptarshi.finogpt.repository.SummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private final SummaryRepository summaryRepository;

    @Transactional
    public void update(Transaction txn) {

        Long userId = txn.getUserId();
        LocalDate date = txn.getTxnDate();

        int year = date.getYear();
        int month = date.getMonthValue();

        String type = txn.getTxnType().name();
        
        summaryRepository.upsertDaily(
                userId,
                date,
                txn.getAmount(),
                type
        );
        
        summaryRepository.upsertMonthly(
                userId,
                year,
                month,
                txn.getAmount(),
                type
        );

        if (!isExpenseTransaction(txn)) {
            return;
        }

        Long effectiveCategoryId = txn.getUserCategoryId() != null
                ? txn.getUserCategoryId()
                : txn.getCategoryId();

        if (effectiveCategoryId != null) {
            summaryRepository.upsertCategory(
                    userId,
                    year,
                    month,
                    effectiveCategoryId,
                    txn.getAmount()
            );
        }


        if (txn.getEntityId() != null) {
            summaryRepository.upsertEntity(
                    userId,
                    year,
                    month,
                    txn.getEntityId(),
                    txn.getAmount()
            );
        }
    }

    @Transactional
    public void rebuildMonth(Long userId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        summaryRepository.deleteDailyRange(userId, startDate, endDate);
        summaryRepository.deleteMonthly(userId, year, month);
        summaryRepository.deleteCategory(userId, year, month);
        summaryRepository.deleteEntity(userId, year, month);

        summaryRepository.rebuildDailyRange(userId, startDate, endDate);
        summaryRepository.rebuildMonthly(userId, year, month, startDate, endDate);
        summaryRepository.rebuildCategory(userId, year, month, startDate, endDate);
        summaryRepository.rebuildEntity(userId, year, month, startDate, endDate);
    }

    private boolean isExpenseTransaction(Transaction txn) {
        return txn.getTxnType() == TxnType.DEBIT;
    }
}
