package com.precued.controller.dto;

import com.precued.entity.RoomParticipant;

import java.time.Instant;
import java.util.UUID;

public record RoomParticipantResponse(
        UUID id,
        UUID roomId,
        UUID userId,
        String livekitIdentity,
        String displayName,
        RoomParticipant.AccessLevel accessLevel,
        Instant joinedAt) {

    public static RoomParticipantResponse from(RoomParticipant participant) {
        return new RoomParticipantResponse(
                participant.getId(),
                participant.getRoom().getId(),
                participant.getUser() == null ? null : participant.getUser().getId(),
                participant.getLivekitIdentity(),
                participant.getDisplayName(),
                participant.getAccessLevel(),
                participant.getJoinedAt());
    }
}
