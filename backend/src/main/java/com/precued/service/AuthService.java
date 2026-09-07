package com.precued.service;

import com.precued.entity.AuthSession;
import com.precued.entity.MagicLinkToken;
import com.precued.entity.User;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.MagicLinkTokenRepository;
import com.precued.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Hybrid auth per Issue #1's resolution: magic link required for host-role
 * participants, guest join (RoomParticipant.user_id == null) unchanged for
 * everyone else.
 *
 * Deliberately two separate steps: {@link #generateMagicLink} only ever
 * creates and stores a token — it has no opinion on delivery (email is out
 * of scope for this pass; see AuthController). {@link #verifyMagicLink} is
 * the only place a User gets created or a token gets consumed, and never
 * skips validation regardless of how it's called.
 *
 * Session tokens are opaque, DB-backed, random strings (same pattern as
 * Invite.token) rather than a self-contained signed JWT: nothing here needs
 * stateless cross-service verification yet, and this avoids adding a JWT
 * library + signing-key config for what's currently a single-service check.
 * Revisit if/when a separate service needs to verify a session without a
 * DB round-trip.
 */
@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Short-lived by design — standard magic-link practice (e.g. 15 min). */
    private static final Duration MAGIC_LINK_TTL = Duration.ofMinutes(15);

    /** Placeholder default; make configurable if session length needs to vary. */
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final MagicLinkTokenRepository magicLinkTokenRepository;
    private final AuthSessionRepository authSessionRepository;
    private final UserRepository userRepository;

    public AuthService(
            MagicLinkTokenRepository magicLinkTokenRepository,
            AuthSessionRepository authSessionRepository,
            UserRepository userRepository) {
        this.magicLinkTokenRepository = magicLinkTokenRepository;
        this.authSessionRepository = authSessionRepository;
        this.userRepository = userRepository;
    }

    /** Step 1: generate + store only. Never sends anything. */
    public MagicLinkToken generateMagicLink(String email) {
        MagicLinkToken magicLink = new MagicLinkToken();
        magicLink.setEmail(email);
        magicLink.setToken(generateOpaqueToken());
        magicLink.setCreatedAt(Instant.now());
        magicLink.setExpiresAt(Instant.now().plus(MAGIC_LINK_TTL));
        return magicLinkTokenRepository.save(magicLink);
    }

    /**
     * Step 2: fully separate from generation. Rejects an unknown, already-used,
     * or expired token before touching anything else. Creates the User on
     * first login (Issue #3's AC) or reuses the existing one by email, marks
     * the token used, and returns a fresh session.
     */
    public AuthSession verifyMagicLink(String token) {
        MagicLinkToken magicLink = magicLinkTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("No magic link token " + token));

        if (magicLink.getUsedAt() != null) {
            throw new IllegalStateException("Magic link token has already been used");
        }
        if (magicLink.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Magic link token has expired");
        }

        magicLink.setUsedAt(Instant.now());
        magicLinkTokenRepository.save(magicLink);

        User user = userRepository.findByEmail(magicLink.getEmail())
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(magicLink.getEmail());
                    newUser.setDisplayName(deriveDisplayName(magicLink.getEmail()));
                    newUser.setCreatedAt(Instant.now());
                    return userRepository.save(newUser);
                });

        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setToken(generateOpaqueToken());
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(SESSION_TTL));
        return authSessionRepository.save(session);
    }

    /** No display_name is collected at magic-link time — use the email's local part. */
    private static String deriveDisplayName(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
