package com.precued.controller;

import com.precued.config.SecurityConfig;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.service.InviteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomInviteController.class)
@Import(SecurityConfig.class)
class RoomInviteControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private InviteService inviteService;
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    @MockBean private AuthSessionRepository authSessionRepository;

    @Test
    void list_withoutParticipantSession_returns401() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/invites", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withParticipantSession_reachesController() throws Exception {
        UUID roomId = UUID.randomUUID();
        String token = "participant-token";

        Room room = new Room();
        room.setId(roomId);
        RoomParticipant participant = new RoomParticipant();
        participant.setId(UUID.randomUUID());
        participant.setRoom(room);
        when(roomParticipantRepository.findBySessionToken(token)).thenReturn(Optional.of(participant));
        when(inviteService.listForRoom(roomId)).thenReturn(List.of());

        mockMvc.perform(get("/api/rooms/{roomId}/invites", roomId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
