package com.precued.controller;

import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.service.ShareLifecycleService;
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
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    // Not exercised on this path, but WebMvcConfig (which @WebMvcTest picks
    // up) wires AuthSessionInterceptor regardless, so this must be mockable
    // for the context to load.
    @MockBean private AuthSessionRepository authSessionRepository;

    private static final String TEST_TOKEN = "test-session-token";

    /** Stubs a valid session for exactly this participant — required to start/end a Share as yourself. */
    private void stubAuthenticatedParticipant(UUID participantId) {
        RoomParticipant self = new RoomParticipant();
        self.setId(participantId);
        Room room = new Room();
        room.setId(UUID.randomUUID());
        self.setRoom(room);
        when(roomParticipantRepository.findBySessionToken(TEST_TOKEN)).thenReturn(Optional.of(self));
    }

    @Test
    void startShare_publisherWithoutHostRole_returns403WithClearErrorBody() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        String message = "RoomParticipant " + publisherId + " does not hold a host role and cannot start a Share";
        when(shareLifecycleService.start(eq(roomId), eq(publisherId), isNull(), eq("Exhibit A")))
                .thenThrow(new IllegalStateException(message));
        stubAuthenticatedParticipant(publisherId);

        String body = """
                {"roomId":"%s","publisherParticipantId":"%s","appliedPresetId":null,"label":"Exhibit A"}
                """.formatted(roomId, publisherId);

        mockMvc.perform(post("/api/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
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
        stubAuthenticatedParticipant(publisherId);

        String body = """
                {"roomId":"%s","publisherParticipantId":"%s","appliedPresetId":null,"label":"Exhibit A"}
                """.formatted(roomId, publisherId);

        mockMvc.perform(post("/api/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(shareId.toString()))
                .andExpect(jsonPath("$.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.publisherParticipantId").value(publisherId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void startShare_missingLabel_returns400() throws Exception {
        UUID publisherId = UUID.randomUUID();
        stubAuthenticatedParticipant(publisherId);
        String body = """
                {"roomId":"%s","publisherParticipantId":"%s","appliedPresetId":null}
                """.formatted(UUID.randomUUID(), publisherId);

        mockMvc.perform(post("/api/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ===== current-slide — Chunk 3, Precued_DataModel.md "Presentations Feature" =====

    @Test
    void changeSlide_validRequest_returns200WithUpdatedShare() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        Room room = new Room();
        room.setId(roomId);
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);

        Share share = new Share();
        share.setId(shareId);
        share.setRoom(room);
        share.setPublisher(publisher);
        share.setLabel("Deck");
        share.setKind(Share.Kind.PRESENTATION);
        share.setCurrentSlideIndex(2);
        share.setStatus(Share.Status.ACTIVE);
        share.setStartedAt(Instant.now());

        when(shareLifecycleService.changeSlide(shareId, 2)).thenReturn(share);
        stubAuthenticatedParticipant(publisherId);

        mockMvc.perform(post("/api/shares/{id}/current-slide", shareId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .content("""
                                {"slideIndex":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shareId.toString()))
                .andExpect(jsonPath("$.kind").value("PRESENTATION"))
                .andExpect(jsonPath("$.currentSlideIndex").value(2));
    }

    @Test
    void changeSlide_byNonPublisher_returns403() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        String message = "Only the Share's publisher can change its slide";
        when(shareLifecycleService.changeSlide(shareId, 1)).thenThrow(new IllegalStateException(message));
        stubAuthenticatedParticipant(publisherId);

        mockMvc.perform(post("/api/shares/{id}/current-slide", shareId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .content("""
                                {"slideIndex":1}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(message));
    }

    @Test
    void changeSlide_missingSlideIndex_returns400() throws Exception {
        UUID publisherId = UUID.randomUUID();
        stubAuthenticatedParticipant(publisherId);

        mockMvc.perform(post("/api/shares/{id}/current-slide", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeSlide_negativeSlideIndex_returns400() throws Exception {
        UUID publisherId = UUID.randomUUID();
        stubAuthenticatedParticipant(publisherId);

        mockMvc.perform(post("/api/shares/{id}/current-slide", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .content("""
                                {"slideIndex":-1}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ===== GET slides — Chunk 3 =====

    @Test
    void listSlides_returnsOrderedSlidesWithProxyImageUrls() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        stubAuthenticatedParticipant(publisherId);

        com.precued.entity.ShareSlide slide0 = new com.precued.entity.ShareSlide();
        slide0.setId(UUID.randomUUID());
        slide0.setSlideIndex(0);
        slide0.setImageUrl("shares/" + shareId + "/slides/0.png");
        com.precued.entity.ShareSlide slide1 = new com.precued.entity.ShareSlide();
        slide1.setId(UUID.randomUUID());
        slide1.setSlideIndex(1);
        slide1.setImageUrl("shares/" + shareId + "/slides/1.png");

        when(shareLifecycleService.listSlides(shareId)).thenReturn(java.util.List.of(
                com.precued.controller.dto.SlideResponse.from(slide0, shareId),
                com.precued.controller.dto.SlideResponse.from(slide1, shareId)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/shares/{id}/slides", shareId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slideIndex").value(0))
                .andExpect(jsonPath("$[0].imageUrl").value("/api/shares/" + shareId + "/slides/0/image"))
                .andExpect(jsonPath("$[1].slideIndex").value(1))
                .andExpect(jsonPath("$[1].imageUrl").value("/api/shares/" + shareId + "/slides/1/image"));
    }

    @Test
    void listSlides_unknownShare_returns404() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        stubAuthenticatedParticipant(publisherId);
        when(shareLifecycleService.listSlides(shareId))
                .thenThrow(new IllegalArgumentException("No Share with id " + shareId));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/shares/{id}/slides", shareId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isNotFound());
    }

    // ===== GET grants — Chunk 3, presenter's per-slide visibility matrix =====

    @Test
    void listGrants_returnsActiveGrantsWithShareSlideId() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID slideId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        stubAuthenticatedParticipant(publisherId);

        when(shareLifecycleService.listGrants(shareId)).thenReturn(java.util.List.of(
                new com.precued.controller.dto.ShareRoleGrantResponse(
                        grantId, shareId, roleId, slideId, Instant.now(), null)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/shares/{id}/grants", shareId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(grantId.toString()))
                .andExpect(jsonPath("$[0].roomRoleId").value(roleId.toString()))
                .andExpect(jsonPath("$[0].shareSlideId").value(slideId.toString()));
    }

    @Test
    void listGrants_unknownShare_returns404() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        stubAuthenticatedParticipant(publisherId);
        when(shareLifecycleService.listGrants(shareId))
                .thenThrow(new IllegalArgumentException("No Share with id " + shareId));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/shares/{id}/grants", shareId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isNotFound());
    }
}
