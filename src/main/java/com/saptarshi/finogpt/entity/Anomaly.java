package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "anomalies")
@Data
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long txnId;

    private String anomalyType;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String severity;

    private LocalDateTime createdAt = LocalDateTime.now();
}
