package com.precued.controller.dto;

import com.precued.entity.RoomParticipant;

import java.time.Instant;
import java.util.UUID;

/**
 * sessionToken is the bearer credential the caller must send as
 * "Authorization: Bearer <token>" on every subsequent request scoped to this
 * participant or their Room (see ParticipantSessionInterceptor). This is the
 * ONLY response DTO that carries it — never add it to a DTO used to list
 * OTHER participants (e.g. RoomParticipantWithGrantsResponse), or every
 * viewer in a room would be handed everyone else's credentials.
 */
public record RoomParticipantResponse(
        UUID id,
        UUID roomId,
        UUID userId,
        String livekitIdentity,
        String displayName,
        RoomParticipant.AccessLevel accessLevel,
        Instant joinedAt,
        String sessionToken) {

    public static RoomParticipantResponse from(RoomParticipant participant) {
        return new RoomParticipantResponse(
                participant.getId(),
                participant.getRoom().getId(),
                participant.getUser() == null ? null : participant.getUser().getId(),
                participant.getLivekitIdentity(),
                participant.getDisplayName(),
                participant.getAccessLevel(),
                participant.getJoinedAt(),
                participant.getSessionToken());
    }
}
