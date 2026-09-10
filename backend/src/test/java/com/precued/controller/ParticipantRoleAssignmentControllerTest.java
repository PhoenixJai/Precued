package com.precued.controller;

import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.service.ParticipantRoleAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParticipantRoleAssignmentController.class)
class ParticipantRoleAssignmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ParticipantRoleAssignmentService assignmentService;
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    // Not exercised on this path, but WebMvcConfig (which @WebMvcTest picks
    // up) wires AuthSessionInterceptor regardless, so this must be mockable
    // for the context to load.
    @MockBean private AuthSessionRepository authSessionRepository;

    private static final String TEST_TOKEN = "test-session-token";

    /** Stubs a valid session for exactly this participant — required to assign/revoke your own role. */
    private void stubAuthenticatedParticipant(UUID participantId) {
        RoomParticipant self = new RoomParticipant();
        self.setId(participantId);
        Room room = new Room();
        room.setId(UUID.randomUUID());
        self.setRoom(room);
        when(roomParticipantRepository.findBySessionToken(TEST_TOKEN)).thenReturn(Optional.of(self));
    }

    private ParticipantRoleAssignment assignmentWithId(UUID id, UUID participantId, UUID roomRoleId) {
        RoomParticipant participant = new RoomParticipant();
        participant.setId(participantId);
        RoomRole role = new RoomRole();
        role.setId(roomRoleId);

        ParticipantRoleAssignment assignment = new ParticipantRoleAssignment();
        assignment.setId(id);
        assignment.setRoomParticipant(participant);
        assignment.setRoomRole(role);
        assignment.setAssignedAt(Instant.now());
        return assignment;
    }

    @Test
    void assign_validRequest_returns201() throws Exception {
        UUID participantId = UUID.randomUUID();
        UUID roomRoleId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        when(assignmentService.assign(eq(participantId), eq(roomRoleId)))
                .thenReturn(assignmentWithId(assignmentId, participantId, roomRoleId));
        stubAuthenticatedParticipant(participantId);

        String body = """
                {"roomParticipantId":"%s","roomRoleId":"%s"}
                """.formatted(participantId, roomRoleId);

        mockMvc.perform(post("/api/participant-role-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.roomParticipantId").value(participantId.toString()))
                .andExpect(jsonPath("$.roomRoleId").value(roomRoleId.toString()));
    }

    @Test
    void assign_unknownParticipant_returns404() throws Exception {
        UUID participantId = UUID.randomUUID();
        UUID roomRoleId = UUID.randomUUID();
        when(assignmentService.assign(eq(participantId), eq(roomRoleId)))
                .thenThrow(new IllegalArgumentException("No RoomParticipant with id " + participantId));
        stubAuthenticatedParticipant(participantId);

        String body = """
                {"roomParticipantId":"%s","roomRoleId":"%s"}
                """.formatted(participantId, roomRoleId);

        mockMvc.perform(post("/api/participant-role-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void revoke_existingAssignment_returns200WithRevokedAtSet() throws Exception {
        UUID participantId = UUID.randomUUID();
        UUID roomRoleId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        ParticipantRoleAssignment assignment = assignmentWithId(assignmentId, participantId, roomRoleId);
        assignment.setRevokedAt(Instant.now());
        when(assignmentService.revoke(assignmentId)).thenReturn(assignment);
        stubAuthenticatedParticipant(participantId);

        mockMvc.perform(post("/api/participant-role-assignments/{id}/revoke", assignmentId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.revokedAt").exists());
    }
}
