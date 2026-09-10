package com.precued.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

/**
 * The mechanical half of "Authorization: Bearer &lt;token&gt;" handling that
 * every session-checking interceptor needs verbatim: extracting the token
 * and writing an RFC 7807 rejection body. Shared by
 * {@link ParticipantSessionInterceptor} and {@link AuthSessionInterceptor}
 * so the two don't quietly drift — what differs between them (which
 * repository resolves the token, what "invalid" means, which paths require
 * one at all) stays in each interceptor, since RoomParticipant sessions and
 * AuthSession sessions are unrelated token spaces with different resolution
 * rules, not something worth forcing into one shared class.
 */
final class BearerTokenSupport {

    private static final String BEARER_PREFIX = "Bearer ";

    private BearerTokenSupport() {}

    static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    static boolean reject(
            HttpServletResponse response, HttpStatus status, String detail, ObjectMapper objectMapper)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ProblemDetail.forStatusAndDetail(status, detail)));
        return false;
    }
}
