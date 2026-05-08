package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    Optional<Category> findByNameIgnoreCase(String name);

    @Query("SELECT c FROM Category c " +
            "WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY LENGTH(c.name)")
    List<Category> searchFuzzy(@Param("keyword") String keyword);
}
