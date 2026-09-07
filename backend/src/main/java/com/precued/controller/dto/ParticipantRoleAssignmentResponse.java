package com.precued.controller.dto;

import com.precued.entity.ParticipantRoleAssignment;

import java.time.Instant;
import java.util.UUID;

public record ParticipantRoleAssignmentResponse(
        UUID id, UUID roomParticipantId, UUID roomRoleId, Instant assignedAt, Instant revokedAt) {

    public static ParticipantRoleAssignmentResponse from(ParticipantRoleAssignment assignment) {
        return new ParticipantRoleAssignmentResponse(
                assignment.getId(),
                assignment.getRoomParticipant().getId(),
                assignment.getRoomRole().getId(),
                assignment.getAssignedAt(),
                assignment.getRevokedAt());
    }
}
