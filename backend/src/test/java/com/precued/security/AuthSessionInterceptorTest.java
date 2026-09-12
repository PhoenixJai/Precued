package com.precued.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.entity.AuthSession;
import com.precued.entity.User;
import com.precued.repository.AuthSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers the fix for the userId-spoofing vulnerability's auth layer: a
 * request-body claim of User identity (createdByUserId, join's userId) now
 * needs a real AuthSession bearer token behind it. Room creation requires
 * one outright; room-participant join only requires one to be VALID if
 * present at all (a guest join sends none) — see the interceptor's own
 * Javadoc for why that split exists.
 */
@ExtendWith(MockitoExtension.class)
class AuthSessionInterceptorTest {

    @Mock private AuthSessionRepository authSessionRepository;

    private AuthSessionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthSessionInterceptor(authSessionRepository, new ObjectMapper());
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void preHandle_optionsPreflight_allowsThroughWithNoAuthRequired() throws Exception {
        // A CORS preflight is an OPTIONS request the browser sends with no
        // Authorization header by design — even on a required path, this
        // must never reject it, or the browser never gets to send the real
        // (authenticated) request at all.
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/rooms");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void preHandle_requiredPath_missingHeader_rejects401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rooms");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void preHandle_optionalPath_missingHeader_allowsThroughWithNoContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/room-participants");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(CurrentUserContext.getIfPresent()).isEmpty();
    }

    @Test
    void preHandle_requiredPath_unknownToken_rejects401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rooms");
        request.addHeader("Authorization", "Bearer unknown-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void preHandle_optionalPath_unknownToken_stillRejects401() throws Exception {
        // A token that IS present is never silently ignored, even on a path
        // where its absence would have been fine.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/room-participants");
        request.addHeader("Authorization", "Bearer unknown-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void preHandle_expiredSession_rejects401() throws Exception {
        AuthSession expired = new AuthSession();
        expired.setUser(new User());
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rooms");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void preHandle_validToken_allowsAndSetsContext() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rooms");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionRepository.findByToken("valid-token")).thenReturn(Optional.of(session));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(CurrentUserContext.get()).isSameAs(user);
    }

    @Test
    void afterCompletion_clearsContext() {
        CurrentUserContext.set(new User());

        interceptor.afterCompletion(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThat(CurrentUserContext.getIfPresent()).isEmpty();
    }
}
