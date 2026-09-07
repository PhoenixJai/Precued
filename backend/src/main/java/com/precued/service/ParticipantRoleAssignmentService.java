package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRoleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal write + VisibilityEngine trigger wiring for the
 * ParticipantRoleAssignment row of the trigger table. Does not enforce
 * the "at most one active assignment" MVP rule beyond the DB's partial
 * unique index — full role-assignment validation (e.g. checking the new
 * role belongs to the same room) belongs to a future assignment feature,
 * not this trigger-wiring pass.
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

    /**
     * Revokes an active assignment, then recomputes every active Share in
     * the participant's room. Callers doing a reassignment must call this
     * BEFORE {@link #assign}, as two separate writes — never combine them
     * into one atomic swap, or the fail-closed gap the spec relies on
     * (Precued_DataModel.md § "Failure / race handling") never happens.
     */
    public ParticipantRoleAssignment revoke(UUID assignmentId) {
        ParticipantRoleAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No ParticipantRoleAssignment with id " + assignmentId));

        assignment.setRevokedAt(Instant.now());
        ParticipantRoleAssignment saved = assignmentRepository.save(assignment);

        engine.recomputeAndPushForRoom(roomIdOf(saved.getRoomParticipant()));
        return saved;
    }

    /** Creates a new active assignment, then recomputes every active Share in the room. */
    public ParticipantRoleAssignment assign(UUID roomParticipantId, UUID roomRoleId) {
        RoomParticipant participant = roomParticipantRepository.findById(roomParticipantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No RoomParticipant with id " + roomParticipantId));
        RoomRole role = roomRoleRepository.findById(roomRoleId)
                .orElseThrow(() -> new IllegalArgumentException("No RoomRole with id " + roomRoleId));

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
