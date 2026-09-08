package com.precued.controller.dto;

import java.util.List;
import java.util.UUID;

public record ActiveShareResponse(UUID id, String label, List<UUID> roomRoleIds) {
}
