package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
@Entity
@Table(name = "user_entity_category_mapping",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "entityId"}))
@Data
public class UserEntityTxnCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long entityId;
    private Long categoryId;

    private LocalDateTime createdAt = LocalDateTime.now();
}
