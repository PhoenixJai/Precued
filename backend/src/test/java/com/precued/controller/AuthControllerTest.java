package com.precued.controller;

import com.precued.entity.AuthSession;
import com.precued.entity.MagicLinkToken;
import com.precued.entity.User;
import com.precued.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AuthService authService;

    @Test
    void requestMagicLink_validEmail_returns201WithToken() throws Exception {
        MagicLinkToken token = new MagicLinkToken();
        token.setEmail("host@example.com");
        token.setToken("opaque-token-value");
        token.setExpiresAt(Instant.now().plusSeconds(900));
        when(authService.generateMagicLink(eq("host@example.com"))).thenReturn(token);

        mockMvc.perform(post("/api/auth/magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"host@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("opaque-token-value"))
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
                .andExpect(jsonPath("$.email").value("host@example.com"));
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
}
