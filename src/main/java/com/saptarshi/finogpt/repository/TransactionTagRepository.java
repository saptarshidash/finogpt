package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.TransactionTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionTagRepository extends JpaRepository<TransactionTag, Long> {

    List<TransactionTag> findByTxnId(Long txnId);

    @Modifying
    @Query(value = "DELETE FROM transaction_tags " +
            "WHERE txn_id IN (SELECT id FROM transactions WHERE user_id = :userId)", nativeQuery = true)
    void deleteByUserId(@Param("userId") Long userId);
}
