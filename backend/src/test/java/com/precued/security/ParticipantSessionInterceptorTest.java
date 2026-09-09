package com.precued.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.repository.RoomParticipantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers the session/auth enforcement this interceptor exists for: every
 * protected request must map to an active RoomParticipant, and one whose
 * path names a roomId or a room-participant id must actually own that
 * resource. Before this, any request could hit any endpoint with no such
 * check at all.
 */
@ExtendWith(MockitoExtension.class)
class ParticipantSessionInterceptorTest {

    @Mock private RoomParticipantRepository roomParticipantRepository;

    private ParticipantSessionInterceptor interceptor;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        interceptor = new ParticipantSessionInterceptor(roomParticipantRepository, new ObjectMapper());
    }

    @AfterEach
    void clearContext() {
        CurrentParticipantContext.clear();
    }

    @Test
    void preHandle_missingAuthorizationHeader_rejects401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + UUID.randomUUID());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void preHandle_tokenNotFound_rejects401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + UUID.randomUUID());
        request.addHeader("Authorization", "Bearer unknown-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(roomParticipantRepository.findBySessionToken("unknown-token")).thenReturn(Optional.empty());

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void preHandle_participantHasLeft_rejects401() throws Exception {
        RoomParticipant left = new RoomParticipant();
        left.setId(UUID.randomUUID());
        left.setLeftAt(Instant.now());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + UUID.randomUUID());
        request.addHeader("Authorization", "Bearer stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(roomParticipantRepository.findBySessionToken("stale-token")).thenReturn(Optional.of(left));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void preHandle_roomIdInPath_participantInDifferentRoom_rejects403() throws Exception {
        UUID otherRoomId = UUID.randomUUID();
        UUID pathRoomId = UUID.randomUUID();
        RoomParticipant participant = participantInRoom(otherRoomId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + pathRoomId + "/active-shares");
        request.addHeader("Authorization", "Bearer valid-token");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("roomId", pathRoomId.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(roomParticipantRepository.findBySessionToken("valid-token")).thenReturn(Optional.of(participant));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void preHandle_roomIdInPath_matchingRoom_allowsAndSetsContext() throws Exception {
        UUID roomId = UUID.randomUUID();
        RoomParticipant participant = participantInRoom(roomId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + roomId + "/active-shares");
        request.addHeader("Authorization", "Bearer valid-token");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("roomId", roomId.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(roomParticipantRepository.findBySessionToken("valid-token")).thenReturn(Optional.of(participant));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(CurrentParticipantContext.get()).isSameAs(participant);
    }

    @Test
    void preHandle_livekitTokenPath_differentParticipantId_rejects403() throws Exception {
        UUID selfId = UUID.randomUUID();
        UUID otherParticipantId = UUID.randomUUID();
        RoomParticipant self = participantInRoom(UUID.randomUUID());
        self.setId(selfId);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/room-participants/" + otherParticipantId + "/livekit-token");
        request.addHeader("Authorization", "Bearer valid-token");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", otherParticipantId.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(roomParticipantRepository.findBySessionToken("valid-token")).thenReturn(Optional.of(self));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void preHandle_livekitTokenPath_ownParticipantId_allows() throws Exception {
        UUID selfId = UUID.randomUUID();
        RoomParticipant self = participantInRoom(UUID.randomUUID());
        self.setId(selfId);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/room-participants/" + selfId + "/livekit-token");
        request.addHeader("Authorization", "Bearer valid-token");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", selfId.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(roomParticipantRepository.findBySessionToken("valid-token")).thenReturn(Optional.of(self));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void afterCompletion_clearsContext() {
        RoomParticipant participant = participantInRoom(UUID.randomUUID());
        CurrentParticipantContext.set(participant);

        interceptor.afterCompletion(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, CurrentParticipantContext::get);
    }

    private RoomParticipant participantInRoom(UUID roomId) {
        Room room = new Room();
        room.setId(roomId);
        RoomParticipant participant = new RoomParticipant();
        participant.setId(UUID.randomUUID());
        participant.setRoom(room);
        return participant;
    }
}
