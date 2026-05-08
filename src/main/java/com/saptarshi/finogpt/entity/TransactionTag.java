package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "transaction_tags")
@Data
public class TransactionTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long txnId;

    private String tag;
}
