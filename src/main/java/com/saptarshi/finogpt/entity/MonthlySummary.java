package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_summary",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "year", "month"}))
@Data
public class MonthlySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Integer year;
    private Integer month;

    private BigDecimal totalSpend = BigDecimal.ZERO;
    private BigDecimal totalCredit = BigDecimal.ZERO;

    private Integer txnCount = 0;

    private LocalDateTime createdAt = LocalDateTime.now();
}
