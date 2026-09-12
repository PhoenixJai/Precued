package com.precued.security;

import com.precued.entity.RoomParticipant;
import com.precued.repository.RoomParticipantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates the RoomParticipant bearer token for Spring Security's
 * filter chain — the framework-level "is there any valid session at all"
 * gate (see SecurityConfig.authorizeHttpRequests) that used to live inside
 * ParticipantSessionInterceptor's preHandle. That interceptor still runs
 * afterward in the MVC handler chain, but now only for its other job:
 * path-based ownership checks (does {roomId}/{id} in the path match the
 * caller), reading the RoomParticipant this filter already resolved from
 * CurrentParticipantContext — it can no longer do token resolution itself,
 * since by the time an interceptor runs, Spring Security has already decided
 * allow/deny, and it needs a resolved identity to decide with.
 *
 * Deliberately does not reject an invalid/missing token itself: an absent
 * Authentication here just means the request proceeds as anonymous, and
 * SecurityConfig's authorizeHttpRequests rules — the exact permitAll list
 * this project has always had — decide whether that's acceptable for the
 * requested path. AuthSessionInterceptor's own token resolution (a
 * different, unrelated token space — User/AuthSession, not RoomParticipant)
 * is untouched and keeps running independently on its two paths.
 */
@Component
public class RoomParticipantAuthenticationFilter extends OncePerRequestFilter {

    private final RoomParticipantRepository roomParticipantRepository;

    public RoomParticipantAuthenticationFilter(RoomParticipantRepository roomParticipantRepository) {
        this.roomParticipantRepository = roomParticipantRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // A CORS preflight never carries this header by design — nothing to
        // resolve, and SecurityConfig permits OPTIONS outright regardless.
        if (!HttpMethod.OPTIONS.matches(request.getMethod())) {
            String token = BearerTokenSupport.extractToken(request);
            if (token != null) {
                roomParticipantRepository.findBySessionToken(token)
                        .filter(participant -> participant.getLeftAt() == null)
                        .ifPresent(this::authenticate);
            }
        }

        // Cleared here, not just in ParticipantSessionInterceptor's
        // afterCompletion: this filter runs on EVERY request, including
        // paths the interceptor is excluded from (e.g. /api/auth/**) — if a
        // token happened to be sent there and resolved, only this finally
        // block would ever clear it on a pooled thread.
        try {
            filterChain.doFilter(request, response);
        } finally {
            CurrentParticipantContext.clear();
        }
    }

    private void authenticate(RoomParticipant participant) {
        CurrentParticipantContext.set(participant);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(participant, null, List.of()));
    }
}
