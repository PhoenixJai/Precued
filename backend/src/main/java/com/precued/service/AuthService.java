package com.precued.service;

import com.precued.entity.AuthSession;
import com.precued.entity.MagicLinkToken;
import com.precued.entity.User;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.MagicLinkTokenRepository;
import com.precued.repository.UserRepository;
import com.precued.util.OpaqueTokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Hybrid auth per Issue #1's resolution: magic link required for host-role
 * participants, guest join (RoomParticipant.user_id == null) unchanged for
 * everyone else.
 *
 * Deliberately two separate steps: {@link #generateMagicLink} creates,
 * stores, and emails the token via {@link ResendEmailClient} (Resend's HTTP
 * API — see .env.example; Railway blocks outbound SMTP, so the API is used
 * instead of Resend's SMTP relay). {@link #verifyMagicLink} is the only
 * place a User gets created or a token gets consumed, and never skips
 * validation regardless of how it's called.
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Short-lived by design — standard magic-link practice (e.g. 15 min). */
    private static final Duration MAGIC_LINK_TTL = Duration.ofMinutes(15);

    /** Placeholder default; make configurable if session length needs to vary. */
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final MagicLinkTokenRepository magicLinkTokenRepository;
    private final AuthSessionRepository authSessionRepository;
    private final UserRepository userRepository;
    private final ResendEmailClient resendEmailClient;
    private final String magicLinkBaseUrl;
    private final String fromAddress;

    public AuthService(
            MagicLinkTokenRepository magicLinkTokenRepository,
            AuthSessionRepository authSessionRepository,
            UserRepository userRepository,
            ResendEmailClient resendEmailClient,
            @Value("${precued.auth.magic-link.base-url}") String magicLinkBaseUrl,
            @Value("${precued.auth.magic-link.from-address}") String fromAddress) {
        this.magicLinkTokenRepository = magicLinkTokenRepository;
        this.authSessionRepository = authSessionRepository;
        this.userRepository = userRepository;
        this.resendEmailClient = resendEmailClient;
        this.magicLinkBaseUrl = magicLinkBaseUrl;
        this.fromAddress = fromAddress;
    }

    /**
     * Step 1: generate + store, then actually email the link. Never returns
     * or otherwise exposes the token to the HTTP caller — see
     * AuthController's Javadoc for why (the requester and the email's owner
     * are not necessarily the same person). The token is deliberately never
     * logged either, now that email is the real delivery path: unlike the
     * previous log-only stand-in, a token that reached this point is a live
     * bearer credential, not a debugging convenience.
     */
    public MagicLinkToken generateMagicLink(String email) {
        MagicLinkToken magicLink = new MagicLinkToken();
        magicLink.setEmail(email);
        magicLink.setToken(OpaqueTokenGenerator.generate());
        magicLink.setCreatedAt(Instant.now());
        magicLink.setExpiresAt(Instant.now().plus(MAGIC_LINK_TTL));
        MagicLinkToken saved = magicLinkTokenRepository.save(magicLink);

        resendEmailClient.send(
                fromAddress,
                email,
                "Sign in to Precued",
                "Click the link below to sign in to Precued:\n\n"
                        + magicLinkBaseUrl + "?token=" + saved.getToken()
                        + "\n\nThis link expires in 15 minutes and can only be used once. If you didn't"
                        + " request this, you can safely ignore this email.");

        log.info("Magic link email sent to {}", email);

        return saved;
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
        session.setToken(OpaqueTokenGenerator.generate());
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(SESSION_TTL));
        return authSessionRepository.save(session);
    }

    /** No display_name is collected at magic-link time — use the email's local part. */
    private static String deriveDisplayName(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

}
