package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.entity.User;
import com.saptarshi.finogpt.repository.AnomalyRepository;
import com.saptarshi.finogpt.repository.CategorySummaryRepository;
import com.saptarshi.finogpt.repository.DailySummaryRepository;
import com.saptarshi.finogpt.repository.EntitySummaryRepository;
import com.saptarshi.finogpt.repository.IngestionJobRepository;
import com.saptarshi.finogpt.repository.InsightCacheRepository;
import com.saptarshi.finogpt.repository.MonthlySummaryRepository;
import com.saptarshi.finogpt.repository.RecurringTransactionRepository;
import com.saptarshi.finogpt.repository.TransactionEmbeddingRepository;
import com.saptarshi.finogpt.repository.TransactionRepository;
import com.saptarshi.finogpt.repository.TransactionTagRepository;
import com.saptarshi.finogpt.repository.UserEntityCategoryRepository;
import com.saptarshi.finogpt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionEmbeddingRepository transactionEmbeddingRepository;
    @Mock
    private TransactionTagRepository transactionTagRepository;
    @Mock
    private AnomalyRepository anomalyRepository;
    @Mock
    private RecurringTransactionRepository recurringTransactionRepository;
    @Mock
    private InsightCacheRepository insightCacheRepository;
    @Mock
    private UserEntityCategoryRepository userEntityCategoryRepository;
    @Mock
    private DailySummaryRepository dailySummaryRepository;
    @Mock
    private MonthlySummaryRepository monthlySummaryRepository;
    @Mock
    private CategorySummaryRepository categorySummaryRepository;
    @Mock
    private EntitySummaryRepository entitySummaryRepository;
    @Mock
    private IngestionJobRepository ingestionJobRepository;
    @Mock
    private ClarificationSessionService clarificationSessionService;

    @InjectMocks
    private UserSettingsService userSettingsService;

    @Test
    void deleteAllDataRemovesUserOwnedDataBeforeDeletingUser() {
        Long userId = 42L;
        User user = new User();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userSettingsService.deleteAllData(userId);

        InOrder inOrder = inOrder(
                userRepository,
                clarificationSessionService,
                anomalyRepository,
                transactionEmbeddingRepository,
                transactionTagRepository,
                recurringTransactionRepository,
                insightCacheRepository,
                userEntityCategoryRepository,
                dailySummaryRepository,
                monthlySummaryRepository,
                categorySummaryRepository,
                entitySummaryRepository,
                transactionRepository,
                ingestionJobRepository
        );

        inOrder.verify(userRepository).findById(userId);
        inOrder.verify(clarificationSessionService).removeSessionsForUser(userId);
        inOrder.verify(anomalyRepository).deleteByUserId(userId);
        inOrder.verify(transactionEmbeddingRepository).deleteByUserId(userId);
        inOrder.verify(transactionTagRepository).deleteByUserId(userId);
        inOrder.verify(recurringTransactionRepository).deleteByUserId(userId);
        inOrder.verify(insightCacheRepository).deleteByUserId(userId);
        inOrder.verify(userEntityCategoryRepository).deleteByUserId(userId);
        inOrder.verify(dailySummaryRepository).deleteByUserId(userId);
        inOrder.verify(monthlySummaryRepository).deleteByUserId(userId);
        inOrder.verify(categorySummaryRepository).deleteByUserId(userId);
        inOrder.verify(entitySummaryRepository).deleteByUserId(userId);
        inOrder.verify(transactionRepository).deleteByUserId(userId);
        inOrder.verify(ingestionJobRepository).deleteByUserId(userId);
        inOrder.verify(userRepository).delete(user);

        verifyNoMoreInteractions(
                userRepository,
                clarificationSessionService,
                anomalyRepository,
                transactionEmbeddingRepository,
                transactionTagRepository,
                recurringTransactionRepository,
                insightCacheRepository,
                userEntityCategoryRepository,
                dailySummaryRepository,
                monthlySummaryRepository,
                categorySummaryRepository,
                entitySummaryRepository,
                transactionRepository,
                ingestionJobRepository
        );
    }

    @Test
    void deleteAllDataRejectsUnknownUser() {
        Long userId = 42L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userSettingsService.deleteAllData(userId));
    }
}
