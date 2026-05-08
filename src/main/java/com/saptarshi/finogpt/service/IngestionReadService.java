package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.IngestionJobDetailResponse;
import com.saptarshi.finogpt.dto.IngestionJobSummaryResponse;
import com.saptarshi.finogpt.dto.PagedResponse;
import com.saptarshi.finogpt.dto.TransactionListItemResponse;
import com.saptarshi.finogpt.entity.Category;
import com.saptarshi.finogpt.entity.EntityTxn;
import com.saptarshi.finogpt.entity.IngestionJob;
import com.saptarshi.finogpt.entity.Transaction;
import com.saptarshi.finogpt.repository.CategoryRepository;
import com.saptarshi.finogpt.repository.EntityTxnRepository;
import com.saptarshi.finogpt.repository.IngestionJobRepository;
import com.saptarshi.finogpt.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngestionReadService {

    private final IngestionJobRepository ingestionJobRepository;
    private final TransactionRepository transactionRepository;
    private final EntityTxnRepository entityTxnRepository;
    private final CategoryRepository categoryRepository;

    public PagedResponse<IngestionJobSummaryResponse> listJobs(Long userId, int page, int size) {
        Page<IngestionJob> jobs = ingestionJobRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(page, size)
        );

        return toPagedResponse(jobs.map(IngestionJobSummaryResponse::from));
    }

    public IngestionJobDetailResponse getJob(Long userId, String jobId) {
        IngestionJob job = ingestionJobRepository.findByJobIdAndUserId(parseJobId(jobId), userId)
                .orElseThrow(() -> new IllegalArgumentException("Ingestion job not found"));

        return IngestionJobDetailResponse.from(job);
    }

    public PagedResponse<TransactionListItemResponse> getJobTransactions(Long userId,
                                                                         String jobId,
                                                                         int page,
                                                                         int size) {
        UUID parsedJobId = parseJobId(jobId);
        ingestionJobRepository.findByJobIdAndUserId(parsedJobId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Ingestion job not found"));

        Page<Transaction> transactions = transactionRepository.findByUserIdAndIngestionJobId(
                userId,
                parsedJobId,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("txnDate"),
                        Sort.Order.desc("createdAt")
                ))
        );

        Map<Long, String> entityNames = entityTxnRepository.findAllById(
                transactions.getContent().stream()
                        .map(Transaction::getEntityId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(EntityTxn::getId, EntityTxn::getName));

        Map<Long, String> categoryNames = categoryRepository.findAllById(
                transactions.getContent().stream()
                        .map(Transaction::getCategoryId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(Category::getId, Category::getName));

        Page<TransactionListItemResponse> mapped = transactions.map(transaction ->
                TransactionListItemResponse.from(
                        transaction,
                        entityNames.get(transaction.getEntityId()),
                        categoryNames.get(transaction.getCategoryId())
                )
        );

        return toPagedResponse(mapped);
    }

    private UUID parseJobId(String jobId) {
        try {
            return UUID.fromString(jobId);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid job id");
        }
    }

    private <T> PagedResponse<T> toPagedResponse(Page<T> page) {
        return PagedResponse.<T>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
