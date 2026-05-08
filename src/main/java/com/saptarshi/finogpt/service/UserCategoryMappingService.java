package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.UserCategoryMappingRequest;
import com.saptarshi.finogpt.dto.UserCategoryMappingResponse;
import com.saptarshi.finogpt.entity.Category;
import com.saptarshi.finogpt.entity.EntityTxn;
import com.saptarshi.finogpt.entity.UserEntityTxnCategory;
import com.saptarshi.finogpt.repository.CategoryRepository;
import com.saptarshi.finogpt.repository.EntityTxnRepository;
import com.saptarshi.finogpt.repository.TransactionRepository;
import com.saptarshi.finogpt.repository.UserEntityCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserCategoryMappingService {

    private final UserEntityCategoryRepository mappingRepository;
    private final EntityTxnRepository entityTxnRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final SummaryService summaryService;

    @Transactional(readOnly = true)
    public List<UserCategoryMappingResponse> list(Long userId) {
        List<UserEntityTxnCategory> mappings = mappingRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return toResponses(mappings);
    }

    @Transactional
    public UserCategoryMappingResponse createOrUpdate(Long userId, UserCategoryMappingRequest request) {
        validateRequest(request);

        EntityTxn entity = entityTxnRepository.findById(request.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("Entity not found"));
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        UserEntityTxnCategory mapping = mappingRepository.findByUserIdAndEntityId(userId, entity.getId())
                .orElseGet(UserEntityTxnCategory::new);

        mapping.setUserId(userId);
        mapping.setEntityId(entity.getId());
        mapping.setCategoryId(category.getId());

        UserEntityTxnCategory saved = mappingRepository.save(mapping);
        applyMappingToExistingTransactions(userId, entity.getId(), category.getId());

        return UserCategoryMappingResponse.builder()
                .id(saved.getId())
                .entityId(entity.getId())
                .entityName(entity.getName())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public UserCategoryMappingResponse update(Long userId, Long id, UserCategoryMappingRequest request) {
        validateRequest(request);

        UserEntityTxnCategory existing = mappingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("User category mapping not found"));

        EntityTxn entity = entityTxnRepository.findById(request.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("Entity not found"));
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        Long previousEntityId = existing.getEntityId();
        if (!previousEntityId.equals(entity.getId())) {
            mappingRepository.findByUserIdAndEntityId(userId, entity.getId())
                    .filter(mapping -> !mapping.getId().equals(existing.getId()))
                    .ifPresent(mapping -> {
                        throw new IllegalArgumentException("A mapping for this entity already exists");
                    });
        }

        existing.setEntityId(entity.getId());
        existing.setCategoryId(category.getId());

        UserEntityTxnCategory saved = mappingRepository.save(existing);
        if (!previousEntityId.equals(entity.getId())) {
            clearMappingFromExistingTransactions(userId, previousEntityId);
        }
        applyMappingToExistingTransactions(userId, entity.getId(), category.getId());

        return UserCategoryMappingResponse.builder()
                .id(saved.getId())
                .entityId(entity.getId())
                .entityName(entity.getName())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public void delete(Long userId, Long id) {
        UserEntityTxnCategory mapping = mappingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("User category mapping not found"));

        mappingRepository.delete(mapping);
        clearMappingFromExistingTransactions(userId, mapping.getEntityId());
    }

    private void applyMappingToExistingTransactions(Long userId, Long entityId, Long categoryId) {
        Set<YearMonth> affectedMonths = findAffectedMonths(userId, entityId);

        transactionRepository.updateUserCategoryIdByUserIdAndEntityId(userId, entityId, categoryId);
        rebuildMonths(userId, affectedMonths);
    }

    private void clearMappingFromExistingTransactions(Long userId, Long entityId) {
        Set<YearMonth> affectedMonths = findAffectedMonths(userId, entityId);
        transactionRepository.clearUserCategoryIdByUserIdAndEntityId(userId, entityId);
        rebuildMonths(userId, affectedMonths);
    }

    private Set<YearMonth> findAffectedMonths(Long userId, Long entityId) {
        return transactionRepository.findByUserIdAndEntityId(userId, entityId).stream()
                .map(transaction -> YearMonth.from(transaction.getTxnDate()))
                .collect(Collectors.toSet());
    }

    private void rebuildMonths(Long userId, Set<YearMonth> affectedMonths) {
        affectedMonths.stream()
                .sorted(Comparator.naturalOrder())
                .forEach(yearMonth -> summaryService.rebuildMonth(userId, yearMonth.getYear(), yearMonth.getMonthValue()));
    }

    private List<UserCategoryMappingResponse> toResponses(List<UserEntityTxnCategory> mappings) {
        Map<Long, String> entityNames = entityTxnRepository.findAllById(
                mappings.stream().map(UserEntityTxnCategory::getEntityId).distinct().toList()
        ).stream().collect(Collectors.toMap(EntityTxn::getId, EntityTxn::getName));

        Map<Long, String> categoryNames = categoryRepository.findAllById(
                mappings.stream().map(UserEntityTxnCategory::getCategoryId).distinct().toList()
        ).stream().collect(Collectors.toMap(Category::getId, Category::getName));

        return mappings.stream()
                .map(mapping -> UserCategoryMappingResponse.builder()
                        .id(mapping.getId())
                        .entityId(mapping.getEntityId())
                        .entityName(entityNames.get(mapping.getEntityId()))
                        .categoryId(mapping.getCategoryId())
                        .categoryName(categoryNames.get(mapping.getCategoryId()))
                        .createdAt(mapping.getCreatedAt())
                        .build())
                .toList();
    }

    private void validateRequest(UserCategoryMappingRequest request) {
        if (request == null || request.getEntityId() == null) {
            throw new IllegalArgumentException("entityId is required");
        }
        if (request.getCategoryId() == null) {
            throw new IllegalArgumentException("categoryId is required");
        }
    }
}
