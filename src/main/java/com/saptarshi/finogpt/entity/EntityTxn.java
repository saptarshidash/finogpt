package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "entities")
@Data
public class EntityTxn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String normalizedName;

    private LocalDateTime createdAt = LocalDateTime.now();
}
