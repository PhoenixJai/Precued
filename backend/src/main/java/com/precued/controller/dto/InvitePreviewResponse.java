package com.precued.controller.dto;

import com.precued.entity.Invite;

import java.time.Instant;
import java.util.UUID;

/** Minimal public information revealed to someone already holding the opaque invite token. */
public record InvitePreviewResponse(
        UUID roomId,
        UUID roomRoleId,
        String roleKey,
        String roleName,
        Invite.Mode mode,
        Instant expiresAt) {

    public static InvitePreviewResponse from(Invite invite) {
        return new InvitePreviewResponse(
                invite.getRoomRole().getRoom().getId(),
                invite.getRoomRole().getId(),
                invite.getRoomRole().getRoleKey(),
                invite.getRoomRole().getName(),
                invite.getMode(),
                invite.getExpiresAt());
    }
}
