package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "transaction_embeddings")
@Data
public class TransactionEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long txnId;

    // store as string for now (vector support later)
    @Column(columnDefinition = "TEXT")
    private String embedding;
}
