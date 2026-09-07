package com.precued.controller;

import com.precued.entity.RoomRole;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.service.ShareRoleGrantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShareRoleGrantController.class)
class ShareRoleGrantControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ShareRoleGrantService shareRoleGrantService;

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

        String body = """
                {"shareId":"%s","roomRoleId":"%s"}
                """.formatted(shareId, roomRoleId);

        mockMvc.perform(post("/api/share-role-grants").contentType(MediaType.APPLICATION_JSON).content(body))
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

        String body = """
                {"shareId":"%s","roomRoleId":"%s"}
                """.formatted(shareId, roomRoleId);

        mockMvc.perform(post("/api/share-role-grants").contentType(MediaType.APPLICATION_JSON).content(body))
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

        mockMvc.perform(post("/api/share-role-grants/{id}/revoke", grantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(grantId.toString()))
                .andExpect(jsonPath("$.revokedAt").exists());
    }
}
