package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.TransactionEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionEmbeddingRepository extends JpaRepository<TransactionEmbedding, Long> {

    Optional<TransactionEmbedding> findByTxnId(Long txnId);

    @Modifying
    @Query(value = "DELETE FROM transaction_embeddings " +
            "WHERE txn_id IN (SELECT id FROM transactions WHERE user_id = :userId)", nativeQuery = true)
    void deleteByUserId(@Param("userId") Long userId);
}
