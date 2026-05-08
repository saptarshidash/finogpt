package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.TransactionEvent;
import com.saptarshi.finogpt.entity.Category;
import com.saptarshi.finogpt.entity.EntityTxn;
import com.saptarshi.finogpt.entity.Transaction;
import com.saptarshi.finogpt.entity.User;
import com.saptarshi.finogpt.enums.TxnType;
import com.saptarshi.finogpt.repository.IngestionJobRepository;
import com.saptarshi.finogpt.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionProcessingService {

    private final TransactionRepository transactionRepository;
    private final EntityService entityService;
    private final CategoryService categoryService;
    private final SummaryService summaryService;
    private final RecurringService recurringService;
    private final AnomalyService anomalyService;
    private final IngestionService ingestionService;
    private final IngestionJobRepository ingestionJobRepository;
    private final TransactionEventValidator eventValidator;

    @Transactional
    public void process(TransactionEvent event) {
        String jobId = event != null ? event.getJob_id() : null;

        try {
            eventValidator.validate(event);

            if ("JOB_COMPLETED".equalsIgnoreCase(event.getType())) {
                log.info("Received JOB_COMPLETED event for job {}", jobId);
                ingestionService.updateTotalRecords(
                        jobId,
                        event.getTotal_records()
                );

                ingestionService.checkAndMarkCompleted(jobId);
                return;
            }

            processInternal(event);
            ingestionService.incrementProcessed(jobId);

        } catch (TransactionProcessingException ex) {
            ingestionService.incrementFailed(jobId, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            ingestionService.incrementFailed(jobId, ex.getMessage());
            throw TransactionProcessingException.retryable("Unexpected processing failure", ex);
        }
    }

    private void processInternal(TransactionEvent event) {
        log.info("Processing transaction: {}", event.getUtr_number());
        if (event.getUtr_number() == null || event.getUtr_number().isEmpty()) {
            log.info("Utr number is empty for event {}", event);
        }

        UUID jobId = parseJobId(event.getJob_id());
        Optional<User> userOpt = ingestionJobRepository.findUserByJobId(jobId);

        if (userOpt.isEmpty()) {
            String message = String.format("Unable to find user for transaction %s", event.getUtr_number());
            throw TransactionProcessingException.nonRetryable(message);
        }

        User user = userOpt.get();

        String externalTxnId = resolveExternalTxnId(event, user.getId());

        if (transactionRepository.existsByUserIdAndExternalTxnId(user.getId(), externalTxnId)) {
            String message = String.format("Duplicate transaction: %s", externalTxnId);
            log.warn(message);
            throw TransactionProcessingException.nonRetryable(message);
        }
        EntityTxn entity = entityService.getOrCreate(event.getEntity());

        Category category = categoryService.resolveCategory(event, user, entity);


        Transaction txn = buildTransaction(event, user, entity, category, externalTxnId, jobId);

        transactionRepository.save(txn);
        summaryService.update(txn);
        recurringService.detect(txn);
        anomalyService.detect(txn);

        log.info("Transaction processed successfully: {}", txn.getId());
    }
    private Transaction buildTransaction(TransactionEvent e,
                                         User user,
                                         EntityTxn entity,
                                         Category category,
                                         String externalTxnId,
                                         UUID jobId) {
        TransactionTimestampParser.ParsedTimestamp parsedTimestamp = TransactionTimestampParser.parse(e.getDate());
        LocalDate txnDate = parsedTimestamp.getDate();

        Transaction txn = new Transaction();

        txn.setUserId(user.getId());
        txn.setTxnDate(txnDate);
        txn.setTxnTime(parsedTimestamp.getTime());
        txn.setTxnMonth(txnDate.withDayOfMonth(1));

        txn.setEntityId(entity.getId());
        txn.setCategoryId(category.getId());

        txn.setAmount(BigDecimal.valueOf(e.getAmount()));
        txn.setTxnType(TxnType.valueOf(e.getType()));

        txn.setRawDetails(e.getRaw_details());
        txn.setSource("PHONEPE");

        txn.setExternalTxnId(externalTxnId);
        txn.setIngestionJobId(jobId);

        return txn;
    }

    private String resolveExternalTxnId(TransactionEvent event, Long userId) {
        if (event.getUtr_number() != null && !event.getUtr_number().isBlank()) {
            return event.getUtr_number();
        }

        return generateHash(event, userId);
    }

    private String generateHash(TransactionEvent event, Long userId) {
        String normalizedDate = normalizeDateForHash(event.getDate());

        String raw = userId +
                "|" + normalizedDate +
                "|" + event.getAmount() +
                "|" + event.getEntity() +
                "|" + event.getType() +
                "|" + event.getRaw_details();

        return DigestUtils.md5DigestAsHex(raw.getBytes());
    }

    private String normalizeDateForHash(String rawDate) {
        try {
            return TransactionTimestampParser.parse(rawDate).getDate().toString();
        } catch (Exception ex) {
            return rawDate;
        }
    }

    private UUID parseJobId(String jobId) {
        try {
            return UUID.fromString(jobId);
        } catch (Exception ex) {
            throw TransactionProcessingException.nonRetryable("Invalid job_id: " + jobId);
        }
    }
}
