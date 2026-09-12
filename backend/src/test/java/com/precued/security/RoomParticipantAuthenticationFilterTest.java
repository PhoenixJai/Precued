package com.precued.security;

import com.precued.entity.RoomParticipant;
import com.precued.repository.RoomParticipantRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the M-Auth authentication step that now runs ahead of
 * ParticipantSessionInterceptor: resolving the RoomParticipant bearer token
 * and populating both CurrentParticipantContext (unchanged consumer-facing
 * contract) and Spring Security's SecurityContextHolder (what
 * SecurityConfig's authorizeHttpRequests actually checks).
 */
@ExtendWith(MockitoExtension.class)
class RoomParticipantAuthenticationFilterTest {

    @Mock private RoomParticipantRepository roomParticipantRepository;

    private RoomParticipantAuthenticationFilter filter;

    private void setUp() {
        filter = new RoomParticipantAuthenticationFilter(roomParticipantRepository);
    }

    @AfterEach
    void clearContexts() {
        CurrentParticipantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_contextsArePopulatedWhileChainRuns() throws Exception {
        // Must assert from inside the FilterChain itself, not after
        // doFilterInternal returns: the finally block clears
        // CurrentParticipantContext once the chain completes.
        setUp();
        RoomParticipant participant = new RoomParticipant();
        participant.setId(UUID.randomUUID());
        when(roomParticipantRepository.findBySessionToken("valid-token")).thenReturn(Optional.of(participant));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + UUID.randomUUID());
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertThat(CurrentParticipantContext.get()).isSameAs(participant);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(participant);
            assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        };

        filter.doFilterInternal(request, response, chain);
    }

    @Test
    void missingToken_proceedsAnonymously_leavesAuthorizationDecisionToSecurityConfig() throws Exception {
        setUp();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + UUID.randomUUID());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unknownToken_proceedsAnonymously() throws Exception {
        setUp();
        when(roomParticipantRepository.findBySessionToken("unknown-token")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + UUID.randomUUID());
        request.addHeader("Authorization", "Bearer unknown-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void participantHasLeft_proceedsAnonymously() throws Exception {
        setUp();
        RoomParticipant left = new RoomParticipant();
        left.setId(UUID.randomUUID());
        left.setLeftAt(Instant.now());
        when(roomParticipantRepository.findBySessionToken("stale-token")).thenReturn(Optional.of(left));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + UUID.randomUUID());
        request.addHeader("Authorization", "Bearer stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void optionsPreflight_neverAttemptsTokenResolution() throws Exception {
        setUp();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/rooms/" + UUID.randomUUID());
        request.addHeader("Authorization", "Bearer would-otherwise-resolve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(roomParticipantRepository, never()).findBySessionToken(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void afterChainCompletes_currentParticipantContextIsCleared() throws Exception {
        // SecurityContextHolder itself isn't cleared here — that's Spring
        // Security's own SecurityContextHolderFilter's job in the real
        // chain this filter is registered into (addFilterBefore), not
        // something to duplicate. CurrentParticipantContext is this
        // project's own pre-existing mechanism, with no such filter of its
        // own, so this filter clears it directly (see the Javadoc on why:
        // this filter runs on paths ParticipantSessionInterceptor's
        // afterCompletion never reaches).
        setUp();
        RoomParticipant participant = new RoomParticipant();
        participant.setId(UUID.randomUUID());
        when(roomParticipantRepository.findBySessionToken("valid-token")).thenReturn(Optional.of(participant));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + UUID.randomUUID());
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(CurrentParticipantContext::get))
                .isInstanceOf(IllegalStateException.class);
    }
}
