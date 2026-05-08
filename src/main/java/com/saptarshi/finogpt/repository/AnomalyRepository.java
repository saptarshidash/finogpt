package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.Anomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {

    List<Anomaly> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
