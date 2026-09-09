package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A person's stable membership in a room for the room's whole lifetime.
 * userId is nullable by design (hybrid auth, see ADR-002): host-role
 * participants always have a User; non-host participants may be guests.
 */
@Entity
@Table(name = "room_participant")
@Getter
@Setter
public class RoomParticipant {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** Nullable for guest joins (e.g. a client with no account). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "livekit_identity", nullable = false)
    private String livekitIdentity; // unique per room, stable across reconnects

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** Administrative control — separate from content role (RoomRole). */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 16)
    private AccessLevel accessLevel = AccessLevel.MEMBER;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    /**
     * Opaque bearer credential issued at join time (RoomParticipantService.join).
     * Presented as "Authorization: Bearer <token>" on every subsequent request
     * scoped to this participant or their Room — see ParticipantSessionInterceptor.
     * Nullable only because rows created before this column existed have none.
     */
    @Column(name = "session_token", unique = true)
    private String sessionToken;

    public enum AccessLevel { HOST, MEMBER }
}
