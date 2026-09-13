package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Which RoomRole a RoomParticipant currently holds, with history.
 * MVP rule: at most one active (revokedAt == null) assignment per
 * participant at a time — enforce this in the service layer (see
 * ParticipantRoleAssignmentService), not just as a convention here.
 */
@Entity
@Table(name = "participant_role_assignment")
@Getter
@Setter
public class ParticipantRoleAssignment {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "room_participant_id", nullable = false)
    private RoomParticipant roomParticipant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "room_role_id", nullable = false)
    private RoomRole roomRole;

    /**
     * Historical traceability only. Once an Invite successfully creates this
     * assignment, later invite expiry/use-state changes never revoke it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invite_id")
    private Invite invite;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    /** null = currently active */
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
