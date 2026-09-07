package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use, expiring magic-link token for hybrid auth (Issue #1,
 * resolved: magic link required for host-role participants, guest join
 * unchanged for everyone else). Generation and verification are
 * deliberately separate steps — see AuthService.
 */
@Entity
@Table(name = "magic_link_token")
@Getter
@Setter
public class MagicLinkToken {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** null = not yet consumed */
    @Column(name = "used_at")
    private Instant usedAt;
}
