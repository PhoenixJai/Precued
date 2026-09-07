package com.precued.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRoleRequest(@NotNull UUID roomParticipantId, @NotNull UUID roomRoleId) {
}
