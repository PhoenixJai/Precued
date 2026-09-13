package com.precued.controller.dto;

import com.precued.entity.RoomRole;

import java.util.UUID;

public record RoomRoleResponse(
        UUID id,
        UUID roomId,
        String roleKey,
        String name,
        boolean isHostRole,
        boolean isGuestRole,
        Integer maxMembers) {

    public static RoomRoleResponse from(RoomRole role) {
        return new RoomRoleResponse(
                role.getId(),
                role.getRoom().getId(),
                role.getRoleKey(),
                role.getName(),
                role.isHostRole(),
                role.isGuestRole(),
                role.getMaxMembers());
    }
}
