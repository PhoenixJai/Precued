package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRoleRepository;
import com.precued.security.CurrentParticipantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Runtime assignment mutation. PR 9 moves initial guest-role assignment into
 * InviteJoinService so invite consumption and maxMembers enforcement are
 * atomic. This legacy assign endpoint remains for the Account Holder host
 * bootstrap only; guests cannot use it to select/bypass an Invite-protected
 * role after obtaining a participant session.
 */
@Service
public class ParticipantRoleAssignmentService {

    private final ParticipantRoleAssignmentRepository assignmentRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final RoomRoleRepository roomRoleRepository;
    private final VisibilityEngine engine;

    public ParticipantRoleAssignmentService(
            ParticipantRoleAssignmentRepository assignmentRepository,
            RoomParticipantRepository roomParticipantRepository,
            RoomRoleRepository roomRoleRepository,
            VisibilityEngine engine) {
        this.assignmentRepository = assignmentRepository;
        this.roomParticipantRepository = roomParticipantRepository;
        this.roomRoleRepository = roomRoleRepository;
        this.engine = engine;
    }

    public ParticipantRoleAssignment revoke(UUID assignmentId) {
        ParticipantRoleAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No ParticipantRoleAssignment with id " + assignmentId));

        UUID ownerId = assignment.getRoomParticipant().getId();
        if (!CurrentParticipantContext.get().getId().equals(ownerId)) {
            throw new IllegalStateException("Cannot revoke another participant's role assignment");
        }

        assignment.setRevokedAt(Instant.now());
        ParticipantRoleAssignment saved = assignmentRepository.save(assignment);

        engine.recomputeAndPushForRoom(roomIdOf(saved.getRoomParticipant()));
        return saved;
    }

    /** Account Holder host bootstrap only. Guest roles are assigned by InviteJoinService. */
    @Transactional
    public ParticipantRoleAssignment assign(UUID roomParticipantId, UUID roomRoleId) {
        RoomParticipant participant = roomParticipantRepository.findById(roomParticipantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No RoomParticipant with id " + roomParticipantId));
        // The same RoomRole lock used by InviteService/InviteJoinService makes
        // maxMembers a real concurrent invariant rather than a best-effort
        // pre-check.
        RoomRole role = roomRoleRepository.findByIdForUpdate(roomRoleId)
                .orElseThrow(() -> new IllegalArgumentException("No RoomRole with id " + roomRoleId));

        if (!CurrentParticipantContext.get().getId().equals(roomParticipantId)) {
            throw new IllegalStateException("Cannot assign a role to another participant");
        }
        if (participant.getRoom() == null || role.getRoom() == null
                || !participant.getRoom().getId().equals(role.getRoom().getId())) {
            throw new IllegalStateException("Participant and role must belong to the same room");
        }
        if (!role.isHostRole()) {
            throw new IllegalStateException("Guest roles must be assigned by consuming a valid invite token");
        }
        if (participant.getUser() == null) {
            throw new IllegalStateException(
                    "RoomParticipant " + roomParticipantId
                            + " has no linked User and cannot be assigned a host role");
        }
        if (role.getMaxMembers() != null
                && assignmentRepository.countByRoomRoleIdAndRevokedAtIsNull(roomRoleId) >= role.getMaxMembers()) {
            throw new IllegalStateException("Role is already at capacity");
        }

        ParticipantRoleAssignment assignment = new ParticipantRoleAssignment();
        assignment.setRoomParticipant(participant);
        assignment.setRoomRole(role);
        assignment.setAssignedAt(Instant.now());
        ParticipantRoleAssignment saved = assignmentRepository.save(assignment);

        engine.recomputeAndPushForRoom(roomIdOf(participant));
        return saved;
    }

    private UUID roomIdOf(RoomParticipant participant) {
        return participant.getRoom().getId();
    }
}
