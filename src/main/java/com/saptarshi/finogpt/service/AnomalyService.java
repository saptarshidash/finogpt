package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.entity.Anomaly;
import com.saptarshi.finogpt.entity.Transaction;
import com.saptarshi.finogpt.repository.AnomalyRepository;
import com.saptarshi.finogpt.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyService {

    private final TransactionRepository transactionRepository;
    private final AnomalyRepository anomalyRepository;

    private static final double HIGH_SPEND_MULTIPLIER = 3.0;
    private static final double ENTITY_SPIKE_MULTIPLIER = 2.0;
    private static final BigDecimal FIRST_TXN_HIGH_THRESHOLD = BigDecimal.valueOf(2000);

    @Transactional
    public void detect(Transaction txn) {

     
        if (isHighSpend(txn)) {
            saveAnomaly(txn, "HIGH_SPEND",
                    "Transaction significantly higher than your usual spending",
                    "HIGH");
        }

        
        if (txn.getEntityId() != null && isEntitySpike(txn)) {
            saveAnomaly(txn, "ENTITY_SPIKE",
                    "Unusual spike in spending for this merchant",
                    "MEDIUM");
        }

        if (txn.getEntityId() != null && isFirstTimeHigh(txn)) {
            saveAnomaly(txn, "FIRST_HIGH_TXN",
                    "High value transaction with a new merchant",
                    "MEDIUM");
        }
    }
    
    private boolean isHighSpend(Transaction txn) {

        List<Transaction> recent = transactionRepository
                .findTop20ByUserIdOrderByTxnDateDesc(txn.getUserId());

        if (recent.size() < 5) return false;

        double avg = recent.stream()
                .mapToDouble(t -> t.getAmount().doubleValue())
                .average()
                .orElse(0);

        return txn.getAmount().doubleValue() > avg * HIGH_SPEND_MULTIPLIER;
    }
    
    private boolean isEntitySpike(Transaction txn) {

        List<Transaction> recent = transactionRepository
                .findTop10ByUserIdAndEntityIdOrderByTxnDateDesc(
                        txn.getUserId(),
                        txn.getEntityId()
                );

        if (recent.size() < 3) return false;

        double avg = recent.stream()
                .mapToDouble(t -> t.getAmount().doubleValue())
                .average()
                .orElse(0);

        return txn.getAmount().doubleValue() > avg * ENTITY_SPIKE_MULTIPLIER;
    }
    
    private boolean isFirstTimeHigh(Transaction txn) {

        List<Transaction> all = transactionRepository
                .findTop2ByUserIdAndEntityIdOrderByTxnDateDesc(
                        txn.getUserId(),
                        txn.getEntityId()
                );

        return all.size() == 1 &&
                txn.getAmount().compareTo(FIRST_TXN_HIGH_THRESHOLD) > 0;
    }
    
    private void saveAnomaly(Transaction txn,
                             String type,
                             String description,
                             String severity) {

        Anomaly anomaly = new Anomaly();

        anomaly.setUserId(txn.getUserId());
        anomaly.setTxnId(txn.getId());
        anomaly.setAnomalyType(type);
        anomaly.setDescription(description);
        anomaly.setSeverity(severity);

        anomalyRepository.save(anomaly);

        log.warn("Anomaly detected: type={}, txnId={}", type, txn.getId());
    }
}
