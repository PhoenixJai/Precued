package com.precued.security;

/**
 * Thrown before an abuse-prone public operation reaches its service layer.
 * Carries the number of seconds the caller should wait so the HTTP layer can
 * return a standards-friendly Retry-After header with its 429 response.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
