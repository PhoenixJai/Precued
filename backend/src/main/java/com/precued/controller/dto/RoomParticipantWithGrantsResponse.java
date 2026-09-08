package com.precued.controller.dto;

import com.precued.entity.RoomParticipant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One RoomParticipant plus their currently active ParticipantRoleAssignment
 * (as just its RoomRole id — null if the gap between a revoke and the next
 * assign) and every ShareRoleGrant currently applicable to that role (active
 * grant, on a still-active Share). IDs only, matching
 * ParticipantRoleAssignmentResponse / ShareRoleGrantResponse's shape
 * elsewhere — never nested lazy entities.
 */
public record RoomParticipantWithGrantsResponse(
        UUID id,
        UUID roomId,
        UUID userId,
        String livekitIdentity,
        String displayName,
        RoomParticipant.AccessLevel accessLevel,
        Instant joinedAt,
        Instant leftAt,
        UUID activeRoomRoleId,
        List<UUID> activeShareRoleGrantIds) {

    public static RoomParticipantWithGrantsResponse from(
            RoomParticipant participant, UUID activeRoomRoleId, List<UUID> activeShareRoleGrantIds) {
        return new RoomParticipantWithGrantsResponse(
                participant.getId(),
                participant.getRoom().getId(),
                participant.getUser() == null ? null : participant.getUser().getId(),
                participant.getLivekitIdentity(),
                participant.getDisplayName(),
                participant.getAccessLevel(),
                participant.getJoinedAt(),
                participant.getLeftAt(),
                activeRoomRoleId,
                activeShareRoleGrantIds);
    }
}
