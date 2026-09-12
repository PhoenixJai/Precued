package com.precued.controller;

import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.service.ShareRoleGrantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.precued.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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

@WebMvcTest(ShareRoleGrantController.class)
// Real SecurityConfig, not disabled — @WebMvcTest doesn't pick up plain
// @Configuration beans like SecurityConfig on its own.
@Import(SecurityConfig.class)
class ShareRoleGrantControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ShareRoleGrantService shareRoleGrantService;
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    // Not exercised on this path, but WebMvcConfig (which @WebMvcTest picks
    // up) wires AuthSessionInterceptor regardless, so this must be mockable
    // for the context to load.
    @MockBean private AuthSessionRepository authSessionRepository;

    private static final String TEST_TOKEN = "test-session-token";

    /** Stubs a valid session for some participant — required to grant/revoke a Share's visibility. */
    private void stubAuthenticatedParticipant() {
        RoomParticipant self = new RoomParticipant();
        self.setId(UUID.randomUUID());
        Room room = new Room();
        room.setId(UUID.randomUUID());
        self.setRoom(room);
        when(roomParticipantRepository.findBySessionToken(TEST_TOKEN)).thenReturn(Optional.of(self));
    }

    private ShareRoleGrant grantWithId(UUID id, UUID shareId, UUID roomRoleId) {
        Share share = new Share();
        share.setId(shareId);
        RoomRole role = new RoomRole();
        role.setId(roomRoleId);

        ShareRoleGrant grant = new ShareRoleGrant();
        grant.setId(id);
        grant.setShare(share);
        grant.setRoomRole(role);
        grant.setGrantedAt(Instant.now());
        return grant;
    }

    @Test
    void create_validRequest_returns201() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID roomRoleId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        when(shareRoleGrantService.grant(eq(shareId), eq(roomRoleId)))
                .thenReturn(grantWithId(grantId, shareId, roomRoleId));
        stubAuthenticatedParticipant();

        String body = """
                {"shareId":"%s","roomRoleId":"%s"}
                """.formatted(shareId, roomRoleId);

        mockMvc.perform(post("/api/share-role-grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(grantId.toString()))
                .andExpect(jsonPath("$.shareId").value(shareId.toString()))
                .andExpect(jsonPath("$.roomRoleId").value(roomRoleId.toString()))
                .andExpect(jsonPath("$.revokedAt").doesNotExist());
    }

    @Test
    void create_unknownShare_returns404() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID roomRoleId = UUID.randomUUID();
        when(shareRoleGrantService.grant(eq(shareId), eq(roomRoleId)))
                .thenThrow(new IllegalArgumentException("No Share with id " + shareId));
        stubAuthenticatedParticipant();

        String body = """
                {"shareId":"%s","roomRoleId":"%s"}
                """.formatted(shareId, roomRoleId);

        mockMvc.perform(post("/api/share-role-grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void revoke_existingGrant_returns200WithRevokedAtSet() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID roomRoleId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        ShareRoleGrant grant = grantWithId(grantId, shareId, roomRoleId);
        grant.setRevokedAt(Instant.now());
        when(shareRoleGrantService.revoke(grantId)).thenReturn(grant);
        stubAuthenticatedParticipant();

        mockMvc.perform(post("/api/share-role-grants/{id}/revoke", grantId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(grantId.toString()))
                .andExpect(jsonPath("$.revokedAt").exists());
    }
}
