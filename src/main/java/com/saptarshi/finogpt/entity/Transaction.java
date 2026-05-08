package com.saptarshi.finogpt.entity;

import com.saptarshi.finogpt.enums.TxnType;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "external_txn_id"})
)
@Data
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private LocalDate txnDate;
    private LocalTime txnTime;
    private LocalDate txnMonth;

    private Long entityId;
    private Long categoryId;
    private Long userCategoryId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TxnType txnType;

    @Column(columnDefinition = "TEXT")
    private String rawDetails;

    private String source;

    private String externalTxnId;

    private UUID ingestionJobId;

    private LocalDateTime createdAt = LocalDateTime.now();
}
