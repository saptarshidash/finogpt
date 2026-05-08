package com.saptarshi.finogpt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private LocalDateTime createdAt = LocalDateTime.now();
}

