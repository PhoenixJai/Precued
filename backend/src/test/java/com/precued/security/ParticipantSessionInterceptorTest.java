package com.precued.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers this interceptor's remaining job after M-Auth's Spring Security
 * migration: path-based ownership checks (roomId/{id} in the path must
 * belong to the caller) on a RoomParticipant already authenticated by
 * RoomParticipantAuthenticationFilter — token resolution and the
 * missing/invalid-token 401 case moved there (see
 * RoomParticipantAuthenticationFilterTest); this interceptor no longer has
 * a RoomParticipantRepository dependency at all.
 */
class ParticipantSessionInterceptorTest {

    private ParticipantSessionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ParticipantSessionInterceptor(new ObjectMapper());
    }

    @AfterEach
    void clearContext() {
        CurrentParticipantContext.clear();
    }

    @Test
    void preHandle_optionsPreflight_allowsThroughWithNoAuthRequired() throws Exception {
        // Same reasoning as SecurityConfig's OPTIONS permitAll rule: a CORS
        // preflight never carries a session, so CurrentParticipantContext
        // won't be populated for one.
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/rooms/" + UUID.randomUUID());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void preHandle_roomIdInPath_participantInDifferentRoom_rejects403() throws Exception {
        UUID otherRoomId = UUID.randomUUID();
        UUID pathRoomId = UUID.randomUUID();
        CurrentParticipantContext.set(participantInRoom(otherRoomId));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + pathRoomId + "/active-shares");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("roomId", pathRoomId.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void preHandle_roomIdInPath_matchingRoom_allows() throws Exception {
        UUID roomId = UUID.randomUUID();
        RoomParticipant participant = participantInRoom(roomId);
        CurrentParticipantContext.set(participant);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms/" + roomId + "/active-shares");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("roomId", roomId.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(CurrentParticipantContext.get()).isSameAs(participant);
    }

    @Test
    void preHandle_livekitTokenPath_differentParticipantId_rejects403() throws Exception {
        UUID otherParticipantId = UUID.randomUUID();
        RoomParticipant self = participantInRoom(UUID.randomUUID());
        CurrentParticipantContext.set(self);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/room-participants/" + otherParticipantId + "/livekit-token");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", otherParticipantId.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void preHandle_livekitTokenPath_ownParticipantId_allows() throws Exception {
        UUID selfId = UUID.randomUUID();
        RoomParticipant self = participantInRoom(UUID.randomUUID());
        self.setId(selfId);
        CurrentParticipantContext.set(self);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/room-participants/" + selfId + "/livekit-token");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", selfId.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void afterCompletion_clearsContext() {
        CurrentParticipantContext.set(participantInRoom(UUID.randomUUID()));

        interceptor.afterCompletion(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThrows(IllegalStateException.class, CurrentParticipantContext::get);
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
