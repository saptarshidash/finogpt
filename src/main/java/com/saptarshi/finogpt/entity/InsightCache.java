package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "insights_cache")
@Data
public class InsightCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Integer year;
    private Integer month;

    @Column(columnDefinition = "TEXT")
    private String query;

    @Column(columnDefinition = "TEXT")
    private String response;

    private LocalDateTime createdAt = LocalDateTime.now();
}
