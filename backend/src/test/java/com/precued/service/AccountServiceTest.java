package com.precued.service;

import com.precued.entity.AuthSession;
import com.precued.entity.User;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.UserRepository;
import com.precued.security.EmailAlreadyRegisteredException;
import com.precued.security.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Auth & Account Overhaul (supersedes Issue 1's hybrid magic-link decision
 * for hosts): Account Holders get real email+password accounts. This is a
 * parallel path to AuthService's magic-link flow, not a replacement —
 * both end up issuing the same AuthSession type, which is what the rest
 * of the app (CurrentUserContext, AuthSessionInterceptor) already
 * understands.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthSessionRepository authSessionRepository;

    // A real encoder, not a mock — signUp/logIn's whole point is verifying
    // hash/compare actually round-trips, which a mock can't tell you.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(userRepository, authSessionRepository, passwordEncoder, 24);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) user.setId(UUID.randomUUID());
            return user;
        });
        lenient().when(authSessionRepository.save(any(AuthSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void signUp_newEmail_createsUserWithHashedPasswordAndIssuesSession() {
        when(userRepository.findByEmail("host@example.com")).thenReturn(Optional.empty());

        AuthSession session = service.signUp("host@example.com", "correct horse battery", "Alex Host");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("host@example.com");
        assertThat(savedUser.getDisplayName()).isEqualTo("Alex Host");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("correct horse battery"); // never stored in plaintext
        assertThat(passwordEncoder.matches("correct horse battery", savedUser.getPasswordHash())).isTrue();

        assertThat(session.getUser()).isEqualTo(savedUser);
        assertThat(session.getToken()).isNotBlank();
        assertThat(session.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void signUp_emailAlreadyRegistered_rejectedAndNeverCreatesAUser() {
        User existing = new User();
        existing.setEmail("host@example.com");
        when(userRepository.findByEmail("host@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.signUp("host@example.com", "correct horse battery", "Alex Host"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).save(any());
        verify(authSessionRepository, never()).save(any());
    }

    @Test
    void logIn_correctCredentials_issuesSession() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("host@example.com");
        user.setPasswordHash(passwordEncoder.encode("correct horse battery"));
        when(userRepository.findByEmail("host@example.com")).thenReturn(Optional.of(user));

        AuthSession session = service.logIn("host@example.com", "correct horse battery");

        assertThat(session.getUser()).isEqualTo(user);
        assertThat(session.getToken()).isNotBlank();
        assertThat(session.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void logIn_wrongPassword_rejectedWithoutRevealingWhichFieldWasWrong() {
        User user = new User();
        user.setEmail("host@example.com");
        user.setPasswordHash(passwordEncoder.encode("correct horse battery"));
        when(userRepository.findByEmail("host@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.logIn("host@example.com", "wrong password"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(authSessionRepository, never()).save(any());
    }

    @Test
    void logIn_unknownEmail_rejectedWithTheSameExceptionAsWrongPassword() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.logIn("nobody@example.com", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void logIn_userHasNoPasswordSet_rejected_magicLinkOnlyAccountsCannotPasswordLogin() {
        User magicLinkUser = new User();
        magicLinkUser.setEmail("guest-turned-host@example.com");
        magicLinkUser.setPasswordHash(null);
        when(userRepository.findByEmail("guest-turned-host@example.com")).thenReturn(Optional.of(magicLinkUser));

        assertThatThrownBy(() -> service.logIn("guest-turned-host@example.com", "anything"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
