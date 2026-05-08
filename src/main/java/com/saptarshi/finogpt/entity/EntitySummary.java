package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "entity_summary",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "year", "month", "entityId"}))
@Data
public class EntitySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Integer year;
    private Integer month;

    private Long entityId;

    private BigDecimal totalAmount = BigDecimal.ZERO;

    private Integer txnCount = 0;
}
