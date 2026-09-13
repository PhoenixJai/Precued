package com.precued.service;

import com.precued.entity.AuthSession;
import com.precued.entity.User;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.UserRepository;
import com.precued.security.EmailAlreadyRegisteredException;
import com.precued.security.InvalidCredentialsException;
import com.precued.util.OpaqueTokenGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Auth & Account Overhaul (supersedes Issue 1's hybrid magic-link decision
 * for hosts, who need real accounts): email+password signup/login for
 * Account Holders. A parallel path to AuthService's magic-link flow, not a
 * replacement — magic-link stays, forked to guest-room-join only, unchanged
 * here. Both paths end up issuing the same AuthSession type, since that's
 * what the rest of the app (CurrentUserContext, AuthSessionInterceptor,
 * Room/Template ownership) already understands as "a logged-in User."
 */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final Duration sessionTtl;

    public AccountService(
            UserRepository userRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            @Value("${precued.auth.account.session-ttl-hours}") long sessionTtlHours) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionTtl = Duration.ofHours(sessionTtlHours);
    }

    @Transactional
    public AuthSession signUp(String email, String password, String displayName) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException("An account with email " + email + " already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreatedAt(Instant.now());
        User saved = userRepository.save(user);

        return issueSession(saved);
    }

    /**
     * Same exception and message for "no such email," "wrong password," and
     * "this email has no password set" (a magic-link-only User) — never
     * reveals which, a standard login-endpoint precaution.
     */
    @Transactional
    public AuthSession logIn(String email, String password) {
        User user = userRepository.findByEmail(email)
                .filter(candidate -> candidate.getPasswordHash() != null)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        return issueSession(user);
    }

    private AuthSession issueSession(User user) {
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setToken(OpaqueTokenGenerator.generate());
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(sessionTtl));
        return authSessionRepository.save(session);
    }
}
