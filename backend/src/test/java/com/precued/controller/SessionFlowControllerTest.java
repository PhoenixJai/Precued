package com.precued.controller;

import com.precued.config.SecurityConfig;
import com.precued.controller.dto.SessionFlowResponse;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.service.SessionFlowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionFlowController.class)
@Import(SecurityConfig.class)
class SessionFlowControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private SessionFlowService sessionFlowService;
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    @MockBean private AuthSessionRepository authSessionRepository;

    private static final String TOKEN = "participant-session-token";

    @Test
    void get_authenticatedParticipant_returnsCurrentFlowState() throws Exception {
        UUID roomId = UUID.randomUUID();
        stubAuthenticatedParticipant(roomId);
        when(sessionFlowService.get(roomId)).thenReturn(response(roomId, SessionFlowResponse.FlowStatus.NOT_STARTED));

        mockMvc.perform(get("/api/rooms/{roomId}/session-flow", roomId)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));
    }

    @Test
    void start_authenticatedParticipant_routesToStateMachine() throws Exception {
        UUID roomId = UUID.randomUUID();
        stubAuthenticatedParticipant(roomId);
        when(sessionFlowService.start(roomId)).thenReturn(response(roomId, SessionFlowResponse.FlowStatus.IN_PROGRESS));

        mockMvc.perform(post("/api/rooms/{roomId}/session-flow/start", roomId)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(sessionFlowService).start(roomId);
    }

    @Test
    void advance_authenticatedParticipant_routesToStateMachine() throws Exception {
        UUID roomId = UUID.randomUUID();
        stubAuthenticatedParticipant(roomId);
        when(sessionFlowService.advance(roomId)).thenReturn(response(roomId, SessionFlowResponse.FlowStatus.COMPLETED));

        mockMvc.perform(post("/api/rooms/{roomId}/session-flow/advance", roomId)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(sessionFlowService).advance(roomId);
    }

    @Test
    void missingParticipantSession_returns401BeforeService() throws Exception {
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(get("/api/rooms/{roomId}/session-flow", roomId))
                .andExpect(status().isUnauthorized());

        verify(sessionFlowService, never()).get(roomId);
    }

    private void stubAuthenticatedParticipant(UUID roomId) {
        Room room = new Room();
        room.setId(roomId);
        RoomParticipant participant = new RoomParticipant();
        participant.setId(UUID.randomUUID());
        participant.setRoom(room);
        when(roomParticipantRepository.findBySessionToken(TOKEN)).thenReturn(Optional.of(participant));
    }

    private SessionFlowResponse response(UUID roomId, SessionFlowResponse.FlowStatus status) {
        return new SessionFlowResponse(roomId, true, status, null, List.of());
    }
}
