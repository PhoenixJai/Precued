package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Config-time table. Seeded once (sales_call, mock_trial, ld_debate).
 * Read-only at runtime. Adding a 4th template = adding rows, zero schema change.
 */
@Entity
@Table(name = "template")
@Getter
@Setter
public class Template {

    @Id
    @Column(length = 64)
    private String id; // "sales_call" | "mock_trial" | "ld_debate"

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
