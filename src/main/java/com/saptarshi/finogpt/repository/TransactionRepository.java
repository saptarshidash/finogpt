package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    boolean existsByUserIdAndExternalTxnId(Long userId, String externalTxnId);

    List<Transaction> findTop10ByUserIdAndEntityIdOrderByTxnDateDesc(Long userId, Long entityId);

    List<Transaction> findByUserId(Long userId);

    List<Transaction> findByUserIdAndTxnDateBetween(Long userId, LocalDate start, LocalDate end);

    List<Transaction> findTop20ByUserIdOrderByTxnDateDesc(Long userId);
    List<Transaction> findTop2ByUserIdAndEntityIdOrderByTxnDateDesc(Long userId, Long entityId);
    List<Transaction> findByUserIdAndEntityId(Long userId, Long entityId);

    List<Transaction> findByIngestionJobId(UUID ingestionJobId);

    Page<Transaction> findByUserIdAndIngestionJobId(Long userId, UUID ingestionJobId, Pageable pageable);

    @Modifying
    @Query("UPDATE Transaction t SET t.userCategoryId = :categoryId WHERE t.userId = :userId AND t.entityId = :entityId")
    int updateUserCategoryIdByUserIdAndEntityId(@Param("userId") Long userId,
                                                @Param("entityId") Long entityId,
                                                @Param("categoryId") Long categoryId);

    @Modifying
    @Query("UPDATE Transaction t SET t.userCategoryId = null WHERE t.userId = :userId AND t.entityId = :entityId")
    int clearUserCategoryIdByUserIdAndEntityId(@Param("userId") Long userId,
                                               @Param("entityId") Long entityId);

    void deleteByUserId(Long userId);
}
