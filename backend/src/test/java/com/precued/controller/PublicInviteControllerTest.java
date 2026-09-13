package com.precued.controller;

import com.precued.config.SecurityConfig;
import com.precued.entity.Invite;
import com.precued.entity.Room;
import com.precued.entity.RoomRole;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.service.InviteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicInviteController.class)
@Import(SecurityConfig.class)
class PublicInviteControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private InviteService inviteService;
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    @MockBean private AuthSessionRepository authSessionRepository;

    @Test
    void resolve_validToken_needsNoParticipantSession() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        Room room = new Room();
        room.setId(roomId);
        RoomRole role = new RoomRole();
        role.setId(roleId);
        role.setRoom(room);
        role.setRoleKey("candidate");
        role.setName("Candidate");

        Invite invite = new Invite();
        invite.setRoomRole(role);
        invite.setMode(Invite.Mode.NAMED);
        invite.setExpiresAt(Instant.parse("2026-09-20T12:00:00Z"));
        when(inviteService.resolve("opaque-token")).thenReturn(invite);

        mockMvc.perform(get("/api/invites/opaque-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.roomRoleId").value(roleId.toString()))
                .andExpect(jsonPath("$.roleKey").value("candidate"))
                .andExpect(jsonPath("$.roleName").value("Candidate"))
                .andExpect(jsonPath("$.mode").value("NAMED"));
    }
}
