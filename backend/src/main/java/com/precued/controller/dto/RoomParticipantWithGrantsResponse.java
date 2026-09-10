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
 *
 * Deliberately no userId: this DTO lists OTHER participants to anyone in
 * the room (no host restriction on that endpoint), so it must never carry
 * another participant's internal User id — that id is exactly what let a
 * guest impersonate a host elsewhere (create a room, or join claiming to
 * be them) before the callers of that identity were required to prove it
 * via AuthSession. RoomParticipantResponse (the join response, about
 * yourself only) still carries it; that's not a leak.
 */
public record RoomParticipantWithGrantsResponse(
        UUID id,
        UUID roomId,
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
                participant.getLivekitIdentity(),
                participant.getDisplayName(),
                participant.getAccessLevel(),
                participant.getJoinedAt(),
                participant.getLeftAt(),
                activeRoomRoleId,
                activeShareRoleGrantIds);
    }
}
