package com.precued.repository;

import com.precued.entity.ParticipantRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRoleAssignmentRepository
        extends JpaRepository<ParticipantRoleAssignment, UUID> {
    /** Relies on the partial unique index — at most one row will ever match. */
    Optional<ParticipantRoleAssignment> findByRoomParticipantIdAndRevokedAtIsNull(UUID participantId);

    List<ParticipantRoleAssignment> findByRoomParticipantIdOrderByAssignedAtDesc(UUID participantId);
}
