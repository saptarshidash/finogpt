package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.EntityTxn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntityTxnRepository extends JpaRepository<EntityTxn, Long> {

    Optional<EntityTxn> findByName(String name);

    Optional<EntityTxn> findByNormalizedName(String normalizedName);

    Optional<EntityTxn> findByNameIgnoreCase(String name);

    @Query("SELECT e FROM EntityTxn e " +
            "WHERE LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(COALESCE(e.normalizedName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY LENGTH(COALESCE(e.normalizedName, e.name)), LENGTH(e.name)")
    List<EntityTxn> searchFuzzy(@Param("keyword") String keyword);
}
