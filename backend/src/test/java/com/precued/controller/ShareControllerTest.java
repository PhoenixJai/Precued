package com.precued.controller;

import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.service.ShareLifecycleService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one endpoint in this pass with a real design decision behind it: how
 * a service-layer rejection (ShareLifecycleService throws IllegalStateException
 * when the publisher doesn't hold a host role) surfaces over HTTP. Decided
 * here, via this test, before GlobalExceptionHandler existed to rationalize
 * around: 403 Forbidden — the request is well-formed, but the server refuses
 * to authorize it, which is exactly what "you don't hold a host role" means.
 */
@WebMvcTest(ShareController.class)
class ShareControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ShareLifecycleService shareLifecycleService;

    @Test
    void startShare_publisherWithoutHostRole_returns403WithClearErrorBody() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        String message = "RoomParticipant " + publisherId + " does not hold a host role and cannot start a Share";
        when(shareLifecycleService.start(eq(roomId), eq(publisherId), isNull(), eq("Exhibit A")))
                .thenThrow(new IllegalStateException(message));

        String body = """
                {"roomId":"%s","publisherParticipantId":"%s","appliedPresetId":null,"label":"Exhibit A"}
                """.formatted(roomId, publisherId);

        mockMvc.perform(post("/api/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(message));
    }

    @Test
    void startShare_validRequest_returns201WithShare() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();

        Room room = new Room();
        room.setId(roomId);
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);

        UUID shareId = UUID.randomUUID();
        Share share = new Share();
        share.setId(shareId);
        share.setRoom(room);
        share.setPublisher(publisher);
        share.setLabel("Exhibit A");
        share.setStatus(Share.Status.ACTIVE);
        share.setStartedAt(Instant.now());

        when(shareLifecycleService.start(eq(roomId), eq(publisherId), isNull(), eq("Exhibit A")))
                .thenReturn(share);

        String body = """
                {"roomId":"%s","publisherParticipantId":"%s","appliedPresetId":null,"label":"Exhibit A"}
                """.formatted(roomId, publisherId);

        mockMvc.perform(post("/api/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(shareId.toString()))
                .andExpect(jsonPath("$.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.publisherParticipantId").value(publisherId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void startShare_missingLabel_returns400() throws Exception {
        String body = """
                {"roomId":"%s","publisherParticipantId":"%s","appliedPresetId":null}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
