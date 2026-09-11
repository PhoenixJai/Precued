package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The allow-list. No grant row for a (Share, RoomRole) pair = that role
 * never subscribes to the Share's tracks — not hidden client-side, never
 * sent. See VisibilityEngine for the runtime compile step.
 */
@Entity
@Table(name = "share_role_grant")
@Getter
@Setter
public class ShareRoleGrant {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "share_id", nullable = false)
    private Share share;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "room_role_id", nullable = false)
    private RoomRole roomRole;

    /**
     * NEW (Chunk 1, Precued_DataModel.md "Presentations Feature"). Null =
     * whole-share grant (existing behavior, unaffected). A set value scopes
     * this grant to only that slide being current — see the Runtime Rule's
     * Chunk 1 extension.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_slide_id")
    private ShareSlide shareSlide;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    /** null = currently active */
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
