package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Config-time seed rows (sales_call, mock_trial, ld_debate — createdBy
 * null, read-only, public) coexist with user-created custom templates
 * (M-Templates, Precued_Issues_Update_3.md) — private-by-default: only
 * visible to and mutable by createdBy (see TemplateService). A custom
 * row's id is a generated UUID string, not a hardcoded slug.
 */
@Entity
@Table(name = "template")
@Getter
@Setter
public class Template {

    @Id
    @Column(length = 64)
    private String id; // "sales_call" | "mock_trial" | "ld_debate" | a generated UUID for a custom template

    @Column(nullable = false)
    private String name;

    /** Null for a built-in seeded template. Non-null means custom, private to this User. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
