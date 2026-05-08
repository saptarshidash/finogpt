package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_summary",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "date"}))
@Data
public class DailySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private LocalDate date;

    private BigDecimal totalDebit = BigDecimal.ZERO;
    private BigDecimal totalCredit = BigDecimal.ZERO;

    private Integer txnCount = 0;

    private LocalDateTime createdAt = LocalDateTime.now();
}
