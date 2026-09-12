package com.precued.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.entity.RoomParticipant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Path-based ownership checks for the RoomParticipant Spring Security's
 * RoomParticipantAuthenticationFilter already authenticated (see that
 * filter's Javadoc for the split — this interceptor no longer resolves the
 * token or rejects a missing/invalid one itself; SecurityConfig's
 * authorizeHttpRequests is the 401 gate now). For a path carrying a
 * {roomId} variable, requires the resolved participant to belong to that
 * room. For a /api/room-participants/{id}/... path, requires the resolved
 * participant's own id to match {id} — you may only act as yourself there.
 *
 * A write whose target resource isn't named in the path (e.g. POST
 * /api/shares, where the Share's room/publisher are in the JSON body) can't
 * be checked here — the body isn't parsed yet at preHandle time. Those
 * checks happen in the relevant service, reading
 * {@link CurrentParticipantContext#get()}.
 */
@Component
public class ParticipantSessionInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public ParticipantSessionInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // Same reasoning as SecurityConfig's OPTIONS permitAll rule: a CORS
        // preflight never carries a session, so CurrentParticipantContext
        // won't be populated for one — this must not be treated as "no
        // ownership" on a protected path.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;

        // Guaranteed non-null: SecurityConfig's authorizeHttpRequests only
        // lets a request reach this interceptor's protected paths once
        // RoomParticipantAuthenticationFilter has already authenticated it.
        RoomParticipant participant = CurrentParticipantContext.get();

        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables != null) {
            String roomIdVar = pathVariables.get("roomId");
            if (roomIdVar != null && !participant.getRoom().getId().equals(UUID.fromString(roomIdVar))) {
                return reject(response, HttpStatus.FORBIDDEN, "Not a participant in that room");
            }

            if (request.getRequestURI().contains("/api/room-participants/")) {
                String idVar = pathVariables.get("id");
                if (idVar != null && !participant.getId().equals(UUID.fromString(idVar))) {
                    return reject(response, HttpStatus.FORBIDDEN, "Cannot act on another participant");
                }
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentParticipantContext.clear();
    }

    private boolean reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        return BearerTokenSupport.reject(response, status, detail, objectMapper);
    }
}
