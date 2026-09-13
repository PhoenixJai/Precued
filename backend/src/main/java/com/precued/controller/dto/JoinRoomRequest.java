package com.precued.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * userId is nullable for guests. Guest joins must supply inviteToken; Account
 * Holder joins (the room creator/host bootstrap) use userId + AuthSession.
 */
public record JoinRoomRequest(
        @NotNull UUID roomId,
        UUID userId,
        @NotBlank String displayName,
        String inviteToken) {
}
