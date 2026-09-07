package com.precued.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateShareRoleGrantRequest(@NotNull UUID shareId, @NotNull UUID roomRoleId) {
}
