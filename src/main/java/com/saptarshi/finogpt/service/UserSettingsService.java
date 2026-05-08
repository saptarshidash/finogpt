package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.UpdateSettingsRequest;
import com.saptarshi.finogpt.dto.UserSettingsResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionEmbeddingRepository transactionEmbeddingRepository;
    private final TransactionTagRepository transactionTagRepository;
    private final AnomalyRepository anomalyRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final InsightCacheRepository insightCacheRepository;
    private final UserEntityCategoryRepository userEntityCategoryRepository;
    private final DailySummaryRepository dailySummaryRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;
    private final CategorySummaryRepository categorySummaryRepository;
    private final EntitySummaryRepository entitySummaryRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final ClarificationSessionService clarificationSessionService;

    @Transactional(readOnly = true)
    public UserSettingsResponse get(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return UserSettingsResponse.builder()
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
    }

    @Transactional
    public UserSettingsResponse update(Long userId, UpdateSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new IllegalArgumentException("name cannot be blank");
            }
            user.setName(request.getName().trim());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().isBlank() ? null : request.getPhone().trim());
        }

        User saved = userRepository.save(user);

        return UserSettingsResponse.builder()
                .name(saved.getName())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .build();
    }

    @Transactional
    public void deleteAllData(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        clarificationSessionService.removeSessionsForUser(userId);
        anomalyRepository.deleteByUserId(userId);
        transactionEmbeddingRepository.deleteByUserId(userId);
        transactionTagRepository.deleteByUserId(userId);
        recurringTransactionRepository.deleteByUserId(userId);
        insightCacheRepository.deleteByUserId(userId);
        userEntityCategoryRepository.deleteByUserId(userId);
        dailySummaryRepository.deleteByUserId(userId);
        monthlySummaryRepository.deleteByUserId(userId);
        categorySummaryRepository.deleteByUserId(userId);
        entitySummaryRepository.deleteByUserId(userId);
        transactionRepository.deleteByUserId(userId);
        ingestionJobRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }
}
