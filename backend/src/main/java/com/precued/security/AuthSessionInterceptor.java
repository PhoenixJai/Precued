package com.precued.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.entity.AuthSession;
import com.precued.repository.AuthSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

/**
 * Requires "Authorization: Bearer &lt;AuthSession token&gt;" (issued by
 * POST /api/auth/verify) resolving to a real, unexpired session, on the
 * paths this is registered for (see WebMvcConfig) — currently room creation
 * and room-participant join, the only two places a request-body field
 * (createdByUserId / userId) could otherwise claim a User identity with no
 * proof at all.
 *
 * The two paths don't behave identically: room creation always requires
 * this token — createdByUserId no longer exists as a body field at all,
 * the User comes only from here (see RoomService#create). Room-participant
 * join is different: a guest join has no User and legitimately sends no
 * token, so this interceptor can't reject a missing header there — only
 * RoomParticipantService#join knows, once it's parsed the body's userId,
 * whether a token was actually required. What this interceptor guarantees
 * on BOTH paths is narrower but still real: if a token IS present, it must
 * resolve to a real, unexpired session, or the request is rejected right
 * here — a bad token is never silently treated as "no token."
 */
@Component
public class AuthSessionInterceptor implements HandlerInterceptor {

    /** Paths where this interceptor rejects an absent token itself, not just a bad one. */
    private static final Set<String> REQUIRED_PATHS = Set.of("/api/rooms");

    private final AuthSessionRepository authSessionRepository;
    private final ObjectMapper objectMapper;

    public AuthSessionInterceptor(AuthSessionRepository authSessionRepository, ObjectMapper objectMapper) {
        this.authSessionRepository = authSessionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // A CORS preflight is a browser-generated OPTIONS request that never
        // carries an Authorization header (or any app header at all beyond
        // what the actual follow-up request will send) — rejecting it here
        // would block the real, authenticated request from ever being sent.
        // Spring's CORS handling (see WebMvcConfig) runs independently and
        // still enforces which origins/methods are allowed.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;

        String token = BearerTokenSupport.extractToken(request);
        if (token == null) {
            if (REQUIRED_PATHS.contains(request.getRequestURI())) {
                return reject(response, HttpStatus.UNAUTHORIZED, "Missing or malformed Authorization header");
            }
            return true;
        }

        AuthSession session = authSessionRepository.findByToken(token).orElse(null);
        if (session == null || session.getExpiresAt().isBefore(Instant.now())) {
            return reject(response, HttpStatus.UNAUTHORIZED, "Invalid or expired session");
        }

        CurrentUserContext.set(session.getUser());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
    }

    private boolean reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        return BearerTokenSupport.reject(response, status, detail, objectMapper);
    }
}
