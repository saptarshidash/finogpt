package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.CategoryMetadataResponse;
import com.saptarshi.finogpt.dto.FilterOptionResponse;
import com.saptarshi.finogpt.repository.CategoryRepository;
import com.saptarshi.finogpt.repository.EntityTxnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MetadataService {

    private final CategoryRepository categoryRepository;
    private final EntityTxnRepository entityTxnRepository;

    public List<CategoryMetadataResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(category -> category.getName().toLowerCase()))
                .map(category -> CategoryMetadataResponse.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .type(category.getType())
                        .build())
                .toList();
    }

    public List<FilterOptionResponse> searchEntities(String query, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 25);

        if (query == null || query.isBlank()) {
            return entityTxnRepository.findAll().stream()
                    .sorted(Comparator.comparing(entity -> entity.getName().toLowerCase()))
                    .limit(safeLimit)
                    .map(entity -> FilterOptionResponse.builder()
                            .id(entity.getId())
                            .name(entity.getName())
                            .build())
                    .toList();
        }

        return entityTxnRepository.searchFuzzy(query.trim()).stream()
                .limit(safeLimit)
                .map(entity -> FilterOptionResponse.builder()
                        .id(entity.getId())
                        .name(entity.getName())
                        .build())
                .toList();
    }
}
