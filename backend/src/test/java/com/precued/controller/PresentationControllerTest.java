package com.precued.controller;

import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.entity.ShareSlide;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.service.PresentationUploadException;
import com.precued.service.PresentationUploadResult;
import com.precued.service.PresentationUploadService;
import com.precued.service.ShareSlideImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PresentationController.class)
class PresentationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private PresentationUploadService presentationUploadService;
    @MockBean private ShareSlideImageService shareSlideImageService;
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    // Not exercised on these paths, but WebMvcConfig wires AuthSessionInterceptor
    // regardless, so this must be mockable for the context to load (see ShareControllerTest).
    @MockBean private AuthSessionRepository authSessionRepository;

    private static final String TEST_TOKEN = "test-session-token";

    private void stubAuthenticatedParticipant(UUID participantId) {
        RoomParticipant self = new RoomParticipant();
        self.setId(participantId);
        Room room = new Room();
        room.setId(UUID.randomUUID());
        self.setRoom(room);
        when(roomParticipantRepository.findBySessionToken(TEST_TOKEN)).thenReturn(Optional.of(self));
    }

    @Test
    void upload_validPdf_returns201WithShareAndSlideImageUrls() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        stubAuthenticatedParticipant(publisherId);

        Room room = new Room();
        room.setId(roomId);
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);

        UUID shareId = UUID.randomUUID();
        Share share = new Share();
        share.setId(shareId);
        share.setRoom(room);
        share.setPublisher(publisher);
        share.setLabel("Deck");
        share.setKind(Share.Kind.PRESENTATION);
        share.setCurrentSlideIndex(0);
        share.setStartedAt(Instant.now());

        ShareSlide slide0 = new ShareSlide();
        slide0.setId(UUID.randomUUID());
        slide0.setSlideIndex(0);
        slide0.setImageUrl("shares/" + shareId + "/slides/0.png");

        when(presentationUploadService.upload(eq(roomId), eq(publisherId), anyString(), any()))
                .thenReturn(new PresentationUploadResult(share, List.of(slide0)));

        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", "pdf-bytes".getBytes());

        mockMvc.perform(multipart("/api/shares/presentations")
                        .file(file)
                        .param("roomId", roomId.toString())
                        .param("publisherParticipantId", publisherId.toString())
                        .param("label", "Deck")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareId").value(shareId.toString()))
                .andExpect(jsonPath("$.kind").value("PRESENTATION"))
                .andExpect(jsonPath("$.slides[0].slideIndex").value(0))
                .andExpect(jsonPath("$.slides[0].imageUrl")
                        .value("/api/shares/" + shareId + "/slides/0/image"));
    }

    @Test
    void upload_rejectedByPresentationUploadException_returns400WithClearMessage() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        stubAuthenticatedParticipant(publisherId);

        when(presentationUploadService.upload(eq(roomId), eq(publisherId), anyString(), any()))
                .thenThrow(new PresentationUploadException("File is not a valid PDF (magic bytes mismatch)"));

        MockMultipartFile file = new MockMultipartFile("file", "not-a-pdf.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/shares/presentations")
                        .file(file)
                        .param("roomId", roomId.toString())
                        .param("publisherParticipantId", publisherId.toString())
                        .param("label", "Deck")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("File is not a valid PDF (magic bytes mismatch)"));
    }

    @Test
    void image_authorizedRequest_returns200WithPngBytes() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        stubAuthenticatedParticipant(requesterId);
        byte[] pngBytes = "fake-png".getBytes();
        when(shareSlideImageService.fetch(shareId, 0)).thenReturn(pngBytes);

        mockMvc.perform(get("/api/shares/{shareId}/slides/{slideIndex}/image", shareId, 0)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pngBytes));
    }

    @Test
    void image_notAuthorized_returns403() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        stubAuthenticatedParticipant(requesterId);
        when(shareSlideImageService.fetch(shareId, 0))
                .thenThrow(new IllegalStateException("Not authorized to view this slide"));

        mockMvc.perform(get("/api/shares/{shareId}/slides/{slideIndex}/image", shareId, 0)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void image_unknownShare_returns404() throws Exception {
        UUID shareId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        stubAuthenticatedParticipant(requesterId);
        when(shareSlideImageService.fetch(shareId, 0))
                .thenThrow(new IllegalArgumentException("No Share with id " + shareId));

        mockMvc.perform(get("/api/shares/{shareId}/slides/{slideIndex}/image", shareId, 0)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isNotFound());
    }
}
