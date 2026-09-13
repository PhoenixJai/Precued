package com.precued.controller.dto;

import com.precued.entity.Invite;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateInviteRequest(
        @NotNull UUID roomRoleId,
        @NotNull Invite.Mode mode,
        @Email String inviteeEmail,
        @Min(1) Integer maxUses,
        Instant expiresAt) {
}
