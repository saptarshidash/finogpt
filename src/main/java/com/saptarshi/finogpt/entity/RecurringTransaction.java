package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "recurring_transactions")
@Data
public class RecurringTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long entityId;
    private Long categoryId;

    private Integer frequency;
    private BigDecimal avgAmount;

    private LocalDate lastSeen;

    private LocalDateTime createdAt = LocalDateTime.now();
}
