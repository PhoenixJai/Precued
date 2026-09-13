package com.precued.controller.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(String sessionToken, UUID userId, String email, String displayName, Instant expiresAt) {
}
