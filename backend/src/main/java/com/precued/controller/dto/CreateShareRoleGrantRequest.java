package com.precued.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** shareSlideId is optional — null (or omitted) means a whole-share grant, unaffected existing behavior. */
public record CreateShareRoleGrantRequest(@NotNull UUID shareId, @NotNull UUID roomRoleId, UUID shareSlideId) {
}
