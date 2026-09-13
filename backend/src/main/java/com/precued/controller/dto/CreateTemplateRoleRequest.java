package com.precued.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTemplateRoleRequest(
        @NotBlank String roleKey,
        @NotBlank String name,
        boolean isHostRole,
        boolean isGuestRole,
        Integer maxMembers) {}
