package com.precued.controller.dto;

import java.time.Instant;

/**
 * TEMPORARY: `token` is returned directly in the response body as a
 * stand-in for actually emailing it — real delivery (spring-boot-starter-mail
 * is already a dependency, provisioned for this) is out of scope for this
 * pass. Once email delivery exists, this endpoint should stop echoing the
 * token to the caller.
 */
public record MagicLinkResponse(String token, Instant expiresAt) {
}
