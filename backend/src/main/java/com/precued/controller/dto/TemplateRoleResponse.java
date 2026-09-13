package com.precued.controller.dto;

import com.precued.entity.TemplateRole;

import java.util.UUID;

public record TemplateRoleResponse(
        UUID id,
        String templateId,
        String roleKey,
        String name,
        boolean isHostRole,
        boolean isGuestRole,
        Integer maxMembers,
        int sortOrder) {

    public static TemplateRoleResponse from(TemplateRole role) {
        return new TemplateRoleResponse(
                role.getId(),
                role.getTemplate().getId(),
                role.getRoleKey(),
                role.getName(),
                role.isHostRole(),
                role.isGuestRole(),
                role.getMaxMembers(),
                role.getSortOrder());
    }
}
