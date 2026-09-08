package com.precued.controller;

import com.precued.controller.dto.LiveKitTokenResponse;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.service.LiveKitTokenService;
import com.precued.service.RoomParticipantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomParticipantController.class)
class RoomParticipantControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RoomParticipantService roomParticipantService;
    @MockBean private LiveKitTokenService liveKitTokenService;

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

    @Test
    void livekitToken_existingParticipant_returns200WithTokenAndRoomInfo() throws Exception {
        UUID participantId = UUID.randomUUID();
        LiveKitTokenResponse response =
                new LiveKitTokenResponse("signed-jwt-value", "wss://precued.livekit.cloud", "room-42", "identity-1");
        when(liveKitTokenService.issueToken(participantId)).thenReturn(response);

        mockMvc.perform(get("/api/room-participants/{id}/livekit-token", participantId))
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

        mockMvc.perform(get("/api/room-participants/{id}/livekit-token", participantId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
