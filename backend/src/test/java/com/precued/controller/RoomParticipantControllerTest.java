package com.precued.controller;

import com.precued.config.SecurityConfig;
import com.precued.controller.dto.LiveKitTokenResponse;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.security.PublicEndpointRateLimiter;
import com.precued.service.LiveKitTokenService;
import com.precued.service.RoomParticipantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomParticipantController.class)
// Real SecurityConfig, not disabled — @WebMvcTest doesn't pick up plain
// @Configuration beans like SecurityConfig on its own.
@Import(SecurityConfig.class)
class RoomParticipantControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RoomParticipantService roomParticipantService;
    @MockBean private LiveKitTokenService liveKitTokenService;
    @MockBean private PublicEndpointRateLimiter rateLimiter;
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    @MockBean private AuthSessionRepository authSessionRepository;

    private static final String TEST_TOKEN = "test-session-token";

    /** Stubs a valid session for exactly this participant — required to act on /livekit-token as yourself. */
    private void stubAuthenticatedParticipant(UUID participantId) {
        RoomParticipant self = new RoomParticipant();
        self.setId(participantId);
        Room room = new Room();
        room.setId(UUID.randomUUID());
        self.setRoom(room);
        when(roomParticipantRepository.findBySessionToken(TEST_TOKEN)).thenReturn(Optional.of(self));
    }

    @Test
    void join_guestNoUserId_returns201() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        Room room = new Room();
        room.setId(roomId);
        String livekitIdentity = UUID.randomUUID().toString();
        RoomParticipant participant = new RoomParticipant();
        participant.setId(participantId);
        participant.setRoom(room);
        participant.setLivekitIdentity(livekitIdentity);
        participant.setDisplayName("Guest Client");
        participant.setJoinedAt(Instant.now());

        when(roomParticipantService.join(eq(roomId), isNull(), eq("Guest Client"))).thenReturn(participant);

        String body = """
                {"roomId":"%s","displayName":"Guest Client"}
                """.formatted(roomId);

        mockMvc.perform(post("/api/room-participants").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(participantId.toString()))
                .andExpect(jsonPath("$.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.accessLevel").value("MEMBER"))
                .andExpect(jsonPath("$.livekitIdentity").value(livekitIdentity))
                .andExpect(jsonPath("$.livekitIdentity").isNotEmpty());
    }

    @Test
    void join_missingDisplayName_returns400() throws Exception {
        String body = """
                {"roomId":"%s"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/room-participants").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void join_unknownRoom_returns404() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(roomParticipantService.join(eq(roomId), isNull(), eq("Guest")))
                .thenThrow(new IllegalArgumentException("No Room with id " + roomId));

        String body = """
                {"roomId":"%s","displayName":"Guest"}
                """.formatted(roomId);

        mockMvc.perform(post("/api/room-participants").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    /**
     * AuthSessionInterceptor can't require a token on this path (a guest
     * join legitimately sends none), but a token that IS present must still
     * resolve — this is the one auth-session behavior on this endpoint
     * that's actually observable at the controller layer, since whether a
     * userId claim required one at all is decided inside the (here, mocked)
     * service — see RoomParticipantServiceTest for that.
     */
    @Test
    void join_invalidAuthorizationToken_returns401AndNeverCallsService() throws Exception {
        when(authSessionRepository.findByToken("bogus-token")).thenReturn(Optional.empty());
        String body = """
                {"roomId":"%s","displayName":"Guest"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/room-participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer bogus-token")
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(roomParticipantService, never()).join(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void livekitToken_existingParticipant_returns200WithTokenAndRoomInfo() throws Exception {
        UUID participantId = UUID.randomUUID();
        LiveKitTokenResponse response =
                new LiveKitTokenResponse("signed-jwt-value", "wss://precued.livekit.cloud", "room-42", "identity-1");
        when(liveKitTokenService.issueToken(participantId)).thenReturn(response);
        stubAuthenticatedParticipant(participantId);

        mockMvc.perform(get("/api/room-participants/{id}/livekit-token", participantId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed-jwt-value"))
                .andExpect(jsonPath("$.livekitUrl").value("wss://precued.livekit.cloud"))
                .andExpect(jsonPath("$.roomName").value("room-42"))
                .andExpect(jsonPath("$.identity").value("identity-1"));
    }

    @Test
    void livekitToken_unknownParticipant_returns404() throws Exception {
        UUID participantId = UUID.randomUUID();
        when(liveKitTokenService.issueToken(participantId))
                .thenThrow(new IllegalArgumentException("No RoomParticipant with id " + participantId));
        stubAuthenticatedParticipant(participantId);

        mockMvc.perform(get("/api/room-participants/{id}/livekit-token", participantId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
