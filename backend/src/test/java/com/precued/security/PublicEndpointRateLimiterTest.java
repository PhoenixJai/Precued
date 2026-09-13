package com.precued.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicEndpointRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-13T12:00:00Z"));
    private final PublicEndpointRateLimiter limiter = new PublicEndpointRateLimiter(clock);

    @Test
    void login_allowsTenAttemptsPerEmailWithinFiveMinutes_thenRejects() {
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> limiter.checkLogin("Host@Example.com"));
        }

        RateLimitExceededException exception =
                assertThrows(RateLimitExceededException.class, () -> limiter.checkLogin("host@example.com"));

        assertEquals(300, exception.getRetryAfterSeconds());
    }

    @Test
    void login_normalizesEmailButKeepsDifferentEmailsIndependent() {
        for (int i = 0; i < 10; i++) {
            limiter.checkLogin("host@example.com");
        }

        assertThrows(RateLimitExceededException.class, () -> limiter.checkLogin(" HOST@example.com "));
        assertDoesNotThrow(() -> limiter.checkLogin("other@example.com"));
    }

    @Test
    void login_windowExpiresAndAllowsRequestsAgain() {
        for (int i = 0; i < 10; i++) {
            limiter.checkLogin("host@example.com");
        }
        assertThrows(RateLimitExceededException.class, () -> limiter.checkLogin("host@example.com"));

        clock.advance(Duration.ofMinutes(5));

        assertDoesNotThrow(() -> limiter.checkLogin("host@example.com"));
    }

    @Test
    void signup_allowsFiveAttemptsPerEmailWithinTenMinutes() {
        for (int i = 0; i < 5; i++) {
            limiter.checkSignUp("new@example.com");
        }

        RateLimitExceededException exception =
                assertThrows(RateLimitExceededException.class, () -> limiter.checkSignUp("new@example.com"));

        assertEquals(600, exception.getRetryAfterSeconds());
    }

    @Test
    void magicLink_allowsThreeRequestsPerEmailWithinFifteenMinutes() {
        for (int i = 0; i < 3; i++) {
            limiter.checkMagicLink("guest@example.com");
        }

        RateLimitExceededException exception =
                assertThrows(RateLimitExceededException.class, () -> limiter.checkMagicLink("guest@example.com"));

        assertEquals(900, exception.getRetryAfterSeconds());
    }

    @Test
    void verification_isLimitedPerClientAddress() {
        for (int i = 0; i < 30; i++) {
            limiter.checkVerification("203.0.113.10");
        }

        assertThrows(RateLimitExceededException.class, () -> limiter.checkVerification("203.0.113.10"));
        assertDoesNotThrow(() -> limiter.checkVerification("203.0.113.11"));
    }

    @Test
    void roomJoin_isLimitedPerRoomAndClientAddress() {
        UUID roomA = UUID.randomUUID();
        UUID roomB = UUID.randomUUID();

        for (int i = 0; i < 30; i++) {
            limiter.checkRoomJoin(roomA, "203.0.113.10");
        }

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> limiter.checkRoomJoin(roomA, "203.0.113.10"));

        assertTrue(exception.getRetryAfterSeconds() >= 1);
        assertTrue(exception.getRetryAfterSeconds() <= 60);
        assertDoesNotThrow(() -> limiter.checkRoomJoin(roomA, "203.0.113.11"));
        assertDoesNotThrow(() -> limiter.checkRoomJoin(roomB, "203.0.113.10"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
