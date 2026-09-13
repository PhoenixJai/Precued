package com.precued.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimum application-level abuse protection for Precued's public entry points.
 *
 * This is intentionally small and dependency-free for the current single-app
 * deployment: fixed windows stored in-process, keyed by the least spoofable
 * identifier already available to each endpoint (normalized email where the
 * request already contains one; client address for token verification; room +
 * client address for guest joins).
 *
 * If Precued later runs multiple backend replicas, replace this implementation
 * with a shared store (for example Redis) without changing controller call sites.
 */
@Component
public class PublicEndpointRateLimiter {

    private static final Policy LOGIN = new Policy("login", 10, Duration.ofMinutes(5));
    private static final Policy SIGNUP = new Policy("signup", 5, Duration.ofMinutes(10));
    private static final Policy MAGIC_LINK = new Policy("magic-link", 3, Duration.ofMinutes(15));
    private static final Policy VERIFY = new Policy("verify", 30, Duration.ofMinutes(5));
    private static final Policy ROOM_JOIN = new Policy("room-join", 30, Duration.ofMinutes(1));

    private static final long CLEANUP_EVERY_REQUESTS = 256;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    public PublicEndpointRateLimiter() {
        this(Clock.systemUTC());
    }

    PublicEndpointRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkLogin(String email) {
        check(LOGIN, normalizeEmail(email));
    }

    public void checkSignUp(String email) {
        check(SIGNUP, normalizeEmail(email));
    }

    public void checkMagicLink(String email) {
        check(MAGIC_LINK, normalizeEmail(email));
    }

    public void checkVerification(String clientAddress) {
        check(VERIFY, normalizeClientAddress(clientAddress));
    }

    public void checkRoomJoin(UUID roomId, String clientAddress) {
        check(ROOM_JOIN, roomId + ":" + normalizeClientAddress(clientAddress));
    }

    private void check(Policy policy, String subject) {
        Instant now = clock.instant();
        String key = policy.name() + ":" + subject;

        Window result = windows.compute(key, (ignored, existing) -> {
            if (existing == null || !now.isBefore(existing.resetAt())) {
                return new Window(1, now.plus(policy.window()));
            }
            if (existing.count() >= policy.limit()) {
                return existing;
            }
            return new Window(existing.count() + 1, existing.resetAt());
        });

        if (result.count() >= policy.limit() && windows.computeIfPresent(key, (ignored, current) -> current) == result) {
            // A count exactly equal to the limit is still allowed; reject only
            // when the call arrived after the window was already full.
            // Detect that condition by checking whether this request could
            // increment the stored count. The second read above is identity-
            // stable because Window is immutable and compute is atomic per key.
            Window current = windows.get(key);
            if (current == result && current.count() == policy.limit()) {
                // We need to distinguish the request that reached the limit
                // from the one after it. Store one sentinel step beyond the
                // limit only for the rejected request.
                boolean rejected = windows.replace(key, result, new Window(policy.limit() + 1, result.resetAt()));
                if (rejected) {
                    long retryAfter = Math.max(1, Duration.between(now, result.resetAt()).toSeconds());
                    throw new RateLimitExceededException("Too many " + policy.label() + " attempts", retryAfter);
                }
            }
        }

        if (result.count() > policy.limit()) {
            long retryAfter = Math.max(1, Duration.between(now, result.resetAt()).toSeconds());
            throw new RateLimitExceededException("Too many " + policy.label() + " attempts", retryAfter);
        }

        if (requestCounter.incrementAndGet() % CLEANUP_EVERY_REQUESTS == 0) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().resetAt()));
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "<null>" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeClientAddress(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) {
            return "unknown";
        }
        return clientAddress.trim();
    }

    private record Policy(String name, int limit, Duration window) {
        String label() {
            return name.replace('-', ' ');
        }
    }

    private record Window(int count, Instant resetAt) {}
}
