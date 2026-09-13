package com.precued.controller.dto;

import com.precued.entity.Invite;

import java.time.Instant;
import java.util.UUID;

public record InviteResponse(
        UUID id,
        UUID roomRoleId,
        String inviteeEmail,
        String token,
        Invite.Mode mode,
        int maxUses,
        int usesCount,
        int remainingUses,
        Invite.Status status,
        Instant createdAt,
        Instant expiresAt) {

    public static InviteResponse from(Invite invite) {
        return new InviteResponse(
                invite.getId(),
                invite.getRoomRole().getId(),
                invite.getInviteeEmail(),
                invite.getToken(),
                invite.getMode(),
                invite.getMaxUses(),
                invite.getUsesCount(),
                Math.max(0, invite.getMaxUses() - invite.getUsesCount()),
                invite.getStatus(),
                invite.getCreatedAt(),
                invite.getExpiresAt());
    }
}
