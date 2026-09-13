package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Pre-assignment bearer artifact that resolves to exactly one RoomRole.
 * NAMED invites are single-use; POOL invites may be consumed up to maxUses.
 * A successful consumption is recorded on ParticipantRoleAssignment for
 * history, but later role reassignment does not mutate or regain authority
 * from the originating Invite.
 */
@Entity
@Table(name = "invite")
@Getter
@Setter
public class Invite {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "room_role_id", nullable = false)
    private RoomRole roomRole;

    /** null = pool link (unnamed), set = named invite tracking label */
    @Column(name = "invitee_email")
    private String inviteeEmail;

    @Column(nullable = false, unique = true)
    private String token;

    /** Named invite = 1. Pool link = bounded host-selected/default capacity. */
    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "uses_count", nullable = false)
    private int usesCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Mode mode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** null = valid until the Room ends; service-created invites currently receive a default TTL. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    public enum Status { PENDING, USED, EXPIRED }

    public enum Mode { NAMED, POOL }
}
