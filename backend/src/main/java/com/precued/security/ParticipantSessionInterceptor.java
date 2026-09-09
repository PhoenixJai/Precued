package com.precued.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.entity.RoomParticipant;
import com.precued.repository.RoomParticipantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Requires "Authorization: Bearer <sessionToken>" resolving to a
 * RoomParticipant that hasn't left, on every request this is applied to
 * (see WebMvcConfig for path patterns/exclusions). For a path carrying a
 * {roomId} variable, also requires the resolved participant to belong to
 * that room. For a /api/room-participants/{id}/... path, also requires the
 * resolved participant's own id to match {id} — you may only act as
 * yourself there.
 *
 * A write whose target resource isn't named in the path (e.g. POST
 * /api/shares, where the Share's room/publisher are in the JSON body) can't
 * be checked here — the body isn't parsed yet at preHandle time. Those
 * checks happen in the relevant service, reading
 * {@link CurrentParticipantContext#get()}.
 */
@Component
public class ParticipantSessionInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final RoomParticipantRepository roomParticipantRepository;
    private final ObjectMapper objectMapper;

    public ParticipantSessionInterceptor(
            RoomParticipantRepository roomParticipantRepository, ObjectMapper objectMapper) {
        this.roomParticipantRepository = roomParticipantRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String token = extractToken(request);
        if (token == null) {
            return reject(response, HttpStatus.UNAUTHORIZED, "Missing or malformed Authorization header");
        }

        RoomParticipant participant = roomParticipantRepository.findBySessionToken(token).orElse(null);
        if (participant == null || participant.getLeftAt() != null) {
            return reject(response, HttpStatus.UNAUTHORIZED, "Invalid or expired session");
        }

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

        CurrentParticipantContext.set(participant);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentParticipantContext.clear();
    }

    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private boolean reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ProblemDetail.forStatusAndDetail(status, detail)));
        return false;
    }
}
