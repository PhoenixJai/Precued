package com.precued.service;

import com.precued.entity.AuthSession;
import com.precued.entity.MagicLinkToken;
import com.precued.entity.User;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.MagicLinkTokenRepository;
import com.precued.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers hybrid auth per Issue #1's resolution and Issue #3's AC: a magic
 * link creates a User on first login (or reuses the existing one by email),
 * and generation/verification are genuinely separate — an expired or
 * already-used token must reject before either the User or session step
 * runs.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private MagicLinkTokenRepository magicLinkTokenRepository;
    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ResendEmailClient resendEmailClient;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                magicLinkTokenRepository,
                authSessionRepository,
                userRepository,
                resendEmailClient,
                "http://localhost:5173",
                "Precued <noreply@example.com>");
    }

    @Test
    void generateMagicLink_createsSingleUseExpiringToken() {
        when(magicLinkTokenRepository.save(any(MagicLinkToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MagicLinkToken result = authService.generateMagicLink("host@example.com");

        assertThat(result.getEmail()).isEqualTo("host@example.com");
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getUsedAt()).isNull();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(Instant.now());

        ArgumentCaptor<MagicLinkToken> captor = ArgumentCaptor.forClass(MagicLinkToken.class);
        verify(magicLinkTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo(result.getToken());
    }

    @Test
    void generateMagicLink_sendsRealEmailContainingTheLink_neverJustLogsIt() {
        when(magicLinkTokenRepository.save(any(MagicLinkToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MagicLinkToken result = authService.generateMagicLink("host@example.com");

        verify(resendEmailClient).send(
                eq("Precued <noreply@example.com>"),
                eq("host@example.com"),
                eq("Sign in to Precued"),
                contains("http://localhost:5173?token=" + result.getToken()));
    }

    @Test
    void verifyMagicLink_validToken_createsNewUserOnFirstLogin() {
        MagicLinkToken magicLink = validToken("newhost@example.com");
        when(magicLinkTokenRepository.findByToken("tok-1")).thenReturn(Optional.of(magicLink));
        when(magicLinkTokenRepository.save(any(MagicLinkToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByEmail("newhost@example.com")).thenReturn(Optional.empty());

        UUID newUserId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(newUserId);
            return user;
        });
        when(authSessionRepository.save(any(AuthSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthSession session = authService.verifyMagicLink("tok-1");

        assertThat(magicLink.getUsedAt()).isNotNull();
        assertThat(session.getUser().getId()).isEqualTo(newUserId);
        assertThat(session.getUser().getDisplayName()).isEqualTo("newhost");
        assertThat(session.getToken()).isNotBlank();
        assertThat(session.getExpiresAt()).isAfter(Instant.now());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("newhost@example.com");
    }

    @Test
    void verifyMagicLink_validToken_reusesExistingUserByEmail() {
        MagicLinkToken magicLink = validToken("returning@example.com");
        when(magicLinkTokenRepository.findByToken("tok-2")).thenReturn(Optional.of(magicLink));
        when(magicLinkTokenRepository.save(any(MagicLinkToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("returning@example.com");
        existingUser.setDisplayName("Returning Host");
        when(userRepository.findByEmail("returning@example.com")).thenReturn(Optional.of(existingUser));
        when(authSessionRepository.save(any(AuthSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthSession session = authService.verifyMagicLink("tok-2");

        assertThat(session.getUser()).isSameAs(existingUser);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void verifyMagicLink_expiredToken_rejectsAndDoesNotCreateSessionOrUser() {
        MagicLinkToken magicLink = validToken("late@example.com");
        magicLink.setExpiresAt(Instant.now().minusSeconds(60));
        when(magicLinkTokenRepository.findByToken("tok-3")).thenReturn(Optional.of(magicLink));

        assertThatThrownBy(() -> authService.verifyMagicLink("tok-3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");

        assertThat(magicLink.getUsedAt()).isNull();
        verify(magicLinkTokenRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
        verify(authSessionRepository, never()).save(any());
    }

    @Test
    void verifyMagicLink_alreadyUsedToken_rejectsAndDoesNotCreateSessionOrUser() {
        MagicLinkToken magicLink = validToken("reused@example.com");
        magicLink.setUsedAt(Instant.now().minusSeconds(30));
        when(magicLinkTokenRepository.findByToken("tok-4")).thenReturn(Optional.of(magicLink));

        assertThatThrownBy(() -> authService.verifyMagicLink("tok-4"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been used");

        verify(magicLinkTokenRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
        verify(authSessionRepository, never()).save(any());
    }

    @Test
    void verifyMagicLink_unknownToken_throwsIllegalArgumentException() {
        when(magicLinkTokenRepository.findByToken("does-not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyMagicLink("does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).findByEmail(any());
        verify(authSessionRepository, never()).save(any());
    }

    private MagicLinkToken validToken(String email) {
        MagicLinkToken token = new MagicLinkToken();
        token.setEmail(email);
        token.setToken("tok-" + UUID.randomUUID());
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(900));
        return token;
    }
}
