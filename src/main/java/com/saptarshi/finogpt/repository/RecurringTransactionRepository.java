package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.RecurringTransaction;
import com.saptarshi.finogpt.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    List<RecurringTransaction> findByUserId(Long userId);


    Optional<RecurringTransaction> findByUserIdAndEntityId(Long userId, Long entityId);

    void deleteByUserId(Long userId);
}
