package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "category_summary",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "year", "month", "categoryId"}))
@Data
public class CategorySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Integer year;
    private Integer month;

    private Long categoryId;

    private BigDecimal totalAmount = BigDecimal.ZERO;

    private Integer txnCount = 0;
}
