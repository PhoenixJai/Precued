package com.precued.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** userId is nullable — a guest join has no User (Precued_DataModel.md's RoomParticipant.user_id). */
public record JoinRoomRequest(
        @NotNull UUID roomId,
        UUID userId,
        @NotBlank String displayName) {
}
