package com.saptarshi.finogpt.service;


import com.saptarshi.finogpt.dto.TransactionEvent;
import com.saptarshi.finogpt.entity.*;
import com.saptarshi.finogpt.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserEntityCategoryRepository mappingRepository;
    
    @Transactional(readOnly = true)
    public Category resolveCategory(TransactionEvent event, User user, EntityTxn entity) {

 
        Category userMapped = getUserMappedCategory(user.getId(), entity.getId());
        if (userMapped != null) {
            return userMapped;
        }
        
        Category kafkaCategory = getOrCreateCategory(event.getCategory(), event.getType());
        if (kafkaCategory != null) {
            return kafkaCategory;
        }
        
        return getDefaultCategory(event.getType());
    }
    
    private Category getUserMappedCategory(Long userId, Long entityId) {

        return mappingRepository.findByUserIdAndEntityId(userId, entityId)
                .map(mapping -> categoryRepository.findById(mapping.getCategoryId()).orElse(null))
                .orElse(null);
    }
    
    private Category getOrCreateCategory(String categoryName, String txnType) {

        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }

        return categoryRepository.findByName(categoryName.trim())
                .orElseGet(() -> createCategory(categoryName, txnType));
    }

    private Category createCategory(String name, String txnType) {

        Category category = new Category();
        category.setName(name.trim());
        
        category.setType(resolveType(txnType));

        log.info("Creating new category: {}", name);

        return categoryRepository.save(category);
    }
    
    private Category getDefaultCategory(String txnType) {

        String defaultName = "OTHERS";

        return categoryRepository.findByName(defaultName)
                .orElseGet(() -> createCategory(defaultName, txnType));
    }


    private String resolveType(String txnType) {

        if (txnType == null) {
            return "EXPENSE";
        }

        String normalizedType = txnType.toUpperCase();
        if ("CREDIT".equals(normalizedType)) {
            return "INCOME";
        }

        return "EXPENSE";
    }
}
