package com.precued.controller.dto;

import java.util.List;
import java.util.UUID;

/**
 * Field is {@code shareId} rather than the {@code id} every other response
 * DTO uses for its own entity's primary key — this list is scoped under a
 * room (/api/rooms/{roomId}/active-shares), and the frontend consumes it
 * alongside room-scoped data where "id" alone would be ambiguous about
 * which resource it names.
 */
public record ActiveShareResponse(UUID shareId, String label, List<UUID> roomRoleIds) {
}
