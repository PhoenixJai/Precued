package com.precued.controller;

import com.precued.entity.AuthSession;
import com.precued.entity.MagicLinkToken;
import com.precued.entity.User;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.security.EmailAlreadyRegisteredException;
import com.precued.security.InvalidCredentialsException;
import com.precued.service.AccountService;
import com.precued.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.precued.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
// Real SecurityConfig, not disabled — @WebMvcTest doesn't pick up plain
// @Configuration beans like SecurityConfig on its own.
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AuthService authService;
    @MockBean private AccountService accountService;
    // /api/auth/** is excluded from ParticipantSessionInterceptor and never
    // reaches AuthSessionInterceptor either, but WebMvcConfig (which
    // @WebMvcTest picks up) wires both interceptor beans regardless, so
    // both repositories must be mockable for the context to load.
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    @MockBean private AuthSessionRepository authSessionRepository;

    @Test
    void requestMagicLink_validEmail_returns202WithoutToken() throws Exception {
        MagicLinkToken token = new MagicLinkToken();
        token.setEmail("host@example.com");
        token.setToken("opaque-token-value");
        token.setExpiresAt(Instant.now().plusSeconds(900));
        when(authService.generateMagicLink(eq("host@example.com"))).thenReturn(token);

        mockMvc.perform(post("/api/auth/magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"host@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void requestMagicLink_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void verify_validToken_returns200WithSession() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("host@example.com");
        user.setDisplayName("host");

        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setToken("session-token-value");
        session.setExpiresAt(Instant.now().plusSeconds(86400));
        when(authService.verifyMagicLink(eq("opaque-token-value"))).thenReturn(session);

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"opaque-token-value\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value("session-token-value"))
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("host@example.com"))
                .andExpect(jsonPath("$.displayName").value("host"));
    }

    @Test
    void verify_expiredToken_returns403() throws Exception {
        when(authService.verifyMagicLink(eq("expired-token")))
                .thenThrow(new IllegalStateException("Magic link token has expired"));

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"expired-token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Magic link token has expired"));
    }

    @Test
    void signUp_validRequest_returns201WithSession() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("host@example.com");
        user.setDisplayName("Alex Host");

        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setToken("session-token-value");
        session.setExpiresAt(Instant.now().plusSeconds(86400));
        when(accountService.signUp(eq("host@example.com"), eq("correct horse battery"), eq("Alex Host")))
                .thenReturn(session);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"host@example.com\",\"password\":\"correct horse battery\",\"displayName\":\"Alex Host\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionToken").value("session-token-value"))
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("host@example.com"))
                .andExpect(jsonPath("$.displayName").value("Alex Host"));
    }

    @Test
    void signUp_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"host@example.com\",\"password\":\"short\",\"displayName\":\"Alex Host\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signUp_emailAlreadyRegistered_returns409() throws Exception {
        when(accountService.signUp(eq("host@example.com"), eq("correct horse battery"), eq("Alex Host")))
                .thenThrow(new EmailAlreadyRegisteredException("An account with email host@example.com already exists"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"host@example.com\",\"password\":\"correct horse battery\",\"displayName\":\"Alex Host\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void logIn_validCredentials_returns200WithSession() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("host@example.com");
        user.setDisplayName("Alex Host");

        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setToken("session-token-value");
        session.setExpiresAt(Instant.now().plusSeconds(86400));
        when(accountService.logIn(eq("host@example.com"), eq("correct horse battery"))).thenReturn(session);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"host@example.com\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value("session-token-value"));
    }

    @Test
    void logIn_wrongCredentials_returns401() throws Exception {
        when(accountService.logIn(eq("host@example.com"), eq("wrong")))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"host@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
