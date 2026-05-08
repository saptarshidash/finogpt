package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.UserEntityTxnCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserEntityCategoryRepository extends JpaRepository<UserEntityTxnCategory, Long> {

    Optional<UserEntityTxnCategory> findByUserIdAndEntityId(Long userId, Long entityId);

    List<UserEntityTxnCategory> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserEntityTxnCategory> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
