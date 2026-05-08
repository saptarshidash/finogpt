package com.saptarshi.finogpt.service;




import com.saptarshi.finogpt.entity.RecurringTransaction;
import com.saptarshi.finogpt.entity.Transaction;
import com.saptarshi.finogpt.repository.RecurringTransactionRepository;
import com.saptarshi.finogpt.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringService {

    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringRepository;

    private static final int MIN_TXN_REQUIRED = 3;
    private static final int DATE_TOLERANCE_DAYS = 2;

    @Transactional
    public void detect(Transaction txn) {

        if (txn.getEntityId() == null) return;

        Long userId = txn.getUserId();
        Long entityId = txn.getEntityId();

   
        List<Transaction> txns = transactionRepository
                .findTop10ByUserIdAndEntityIdOrderByTxnDateDesc(userId, entityId);

        if (txns.size() < MIN_TXN_REQUIRED) return;

        // Sort ascending
        txns.sort(Comparator.comparing(Transaction::getTxnDate));

        if (!isRecurring(txns)) return;
        
        saveOrUpdateRecurring(txns);
    }
    

    private boolean isRecurring(List<Transaction> txns) {

        int n = txns.size();

        int diff1 = daysBetween(txns.get(n - 1), txns.get(n - 2));
        int diff2 = daysBetween(txns.get(n - 2), txns.get(n - 3));

        return Math.abs(diff1 - diff2) <= DATE_TOLERANCE_DAYS;
    }

    private int daysBetween(Transaction t1, Transaction t2) {
        return Math.abs((int) (t1.getTxnDate().toEpochDay() - t2.getTxnDate().toEpochDay()));
    }

    private void saveOrUpdateRecurring(List<Transaction> txns) {

        Transaction latest = txns.get(txns.size() - 1);

        Long userId = latest.getUserId();
        Long entityId = latest.getEntityId();

        RecurringTransaction existing = recurringRepository
                .findByUserIdAndEntityId(userId, entityId)
                .orElse(null);

        double avgAmount = txns.stream()
                .mapToDouble(t -> t.getAmount().doubleValue())
                .average()
                .orElse(0);

        int frequency = calculateFrequency(txns);

        if (existing == null) {
            RecurringTransaction r = new RecurringTransaction();
            r.setUserId(userId);
            r.setEntityId(entityId);
            r.setCategoryId(latest.getCategoryId());
            r.setAvgAmount(BigDecimal.valueOf(avgAmount));
            r.setFrequency(frequency);
            r.setLastSeen(latest.getTxnDate());

            recurringRepository.save(r);

            log.info("New recurring detected: user={}, entity={}", userId, entityId);

        } else {
            existing.setAvgAmount(BigDecimal.valueOf(avgAmount));
            existing.setFrequency(frequency);
            existing.setLastSeen(latest.getTxnDate());

            recurringRepository.save(existing);
        }
    }

    private int calculateFrequency(List<Transaction> txns) {

        int n = txns.size();

        int diff = daysBetween(txns.get(n - 1), txns.get(n - 2));

        return diff; 
    }
}
