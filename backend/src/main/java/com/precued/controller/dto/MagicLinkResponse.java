package com.precued.controller.dto;

import java.time.Instant;

/**
 * Deliberately carries no token — the magic-link token is a bearer
 * credential for the recipient's email and must never appear in an API
 * response readable by whoever submitted the email (which may not be its
 * owner). See AuthService#generateMagicLink for where the token actually
 * goes (emailed to the recipient).
 */
public record MagicLinkResponse(Instant expiresAt) {
}
