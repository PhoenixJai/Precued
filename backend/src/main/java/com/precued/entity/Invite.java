package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Sits in front of ParticipantRoleAssignment as a pre-assignment layer.
 * One-time-use join artifact: once consumed it has no ongoing authority
 * over the participant's role history. Reassigning a participant's role
 * mid-call via ParticipantRoleAssignment does NOT touch the originating
 * Invite row.
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

    /** null = pool link (unnamed), set = named invite */
    @Column(name = "invitee_email")
    private String inviteeEmail;

    @Column(nullable = false, unique = true)
    private String token;

    /** Named invite = 1. Pool link = RoomRole.maxMembers (or host-set cap if null). */
    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "uses_count", nullable = false)
    private int usesCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    /** Host-set per RoomRole at room creation; defaults from RoomRole.maxMembers. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Mode mode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** null = expires at Room.endedAt */
    @Column(name = "expires_at")
    private Instant expiresAt;

    public enum Status { PENDING, USED, EXPIRED }

    public enum Mode { NAMED, POOL }
}
