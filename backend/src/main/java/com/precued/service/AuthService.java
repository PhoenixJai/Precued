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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Hybrid auth per Issue #1's resolution: magic link required for host-role
 * participants, guest join (RoomParticipant.user_id == null) unchanged for
 * everyone else.
 *
 * Deliberately two separate steps: {@link #generateMagicLink} creates,
 * stores, and emails the token via {@link JavaMailSender} (Resend's SMTP
 * relay in production — see .env.example). {@link #verifyMagicLink} is the
 * only place a User gets created or a token gets consumed, and never skips
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
    private final JavaMailSender mailSender;
    private final String magicLinkBaseUrl;
    private final String fromAddress;

    public AuthService(
            MagicLinkTokenRepository magicLinkTokenRepository,
            AuthSessionRepository authSessionRepository,
            UserRepository userRepository,
            JavaMailSender mailSender,
            @Value("${precued.auth.magic-link.base-url}") String magicLinkBaseUrl,
            @Value("${precued.auth.magic-link.from-address}") String fromAddress,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${spring.mail.password:}") String smtpPassword) {
        this.magicLinkTokenRepository = magicLinkTokenRepository;
        this.authSessionRepository = authSessionRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.magicLinkBaseUrl = magicLinkBaseUrl;
        this.fromAddress = fromAddress;

        // Live incident: SMTP_PASSWORD reached JavaMailSenderImpl blank in
        // production despite being confirmed set in Railway — found only by
        // reading a raw jakarta.mail.AuthenticationFailedException stack
        // trace from an actual failed send. This surfaces the same
        // condition at boot instead, so a misconfigured/misscoped Railway
        // variable shows up in the startup log immediately, not on the
        // first user-facing failure. Never logs a credential's actual value.
        List<String> blank = blankSmtpCredentialNames(smtpHost, smtpUsername, smtpPassword);
        if (!blank.isEmpty()) {
            log.warn("Blank at startup, magic-link emails will fail to send: {}", blank);
        }
    }

    static List<String> blankSmtpCredentialNames(String host, String username, String password) {
        List<String> blank = new ArrayList<>();
        if (host.isBlank()) blank.add("SMTP_HOST");
        if (username.isBlank()) blank.add("SMTP_USER");
        if (password.isBlank()) blank.add("SMTP_PASSWORD");
        return blank;
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

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Sign in to Precued");
        message.setText("Click the link below to sign in to Precued:\n\n"
                + magicLinkBaseUrl + "?token=" + saved.getToken()
                + "\n\nThis link expires in 15 minutes and can only be used once. If you didn't"
                + " request this, you can safely ignore this email.");
        mailSender.send(message);

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
