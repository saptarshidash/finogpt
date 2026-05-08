package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.TransactionEvent;
import com.saptarshi.finogpt.entity.Category;
import com.saptarshi.finogpt.entity.EntityTxn;
import com.saptarshi.finogpt.entity.Transaction;
import com.saptarshi.finogpt.entity.User;
import com.saptarshi.finogpt.repository.IngestionJobRepository;
import com.saptarshi.finogpt.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionProcessingServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private EntityService entityService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private SummaryService summaryService;
    @Mock
    private RecurringService recurringService;
    @Mock
    private AnomalyService anomalyService;
    @Mock
    private IngestionService ingestionService;
    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Test
    void storesTxnDateAndTimeWhenInputCarriesIsoDateTime() {
        UUID jobId = UUID.randomUUID();
        Long userId = 7L;

        TransactionEvent event = new TransactionEvent();
        event.setJob_id(jobId.toString());
        event.setDate("2026-04-27T23:05:00");
        event.setEntity("Amazon Pay");
        event.setAmount(250.0);
        event.setType("DEBIT");
        event.setRaw_details("txn-raw");

        User user = new User();
        user.setId(userId);

        EntityTxn entity = new EntityTxn();
        entity.setId(11L);

        Category category = new Category();
        category.setId(13L);

        when(ingestionJobRepository.findUserByJobId(jobId)).thenReturn(Optional.of(user));
        when(transactionRepository.existsByUserIdAndExternalTxnId(any(), any())).thenReturn(false);
        when(entityService.getOrCreate("Amazon Pay")).thenReturn(entity);
        when(categoryService.resolveCategory(event, user, entity)).thenReturn(category);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionProcessingService transactionProcessingService = new TransactionProcessingService(
                transactionRepository,
                entityService,
                categoryService,
                summaryService,
                recurringService,
                anomalyService,
                ingestionService,
                ingestionJobRepository,
                new TransactionEventValidator()
        );

        transactionProcessingService.process(event);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        Transaction saved = transactionCaptor.getValue();
        assertEquals(LocalDate.of(2026, 4, 27), saved.getTxnDate());
        assertEquals(LocalTime.of(23, 5), saved.getTxnTime());
        assertEquals(LocalDate.of(2026, 4, 1), saved.getTxnMonth());
    }
}
