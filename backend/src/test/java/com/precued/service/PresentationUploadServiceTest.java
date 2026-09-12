package com.precued.service;

import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.entity.ShareSlide;
import com.precued.presentation.PdfSlideRenderer;
import com.precued.repository.ShareSlideRepository;
import com.precued.storage.SlideImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresentationUploadServiceTest {

    private static final long MAX_FILE_SIZE_BYTES = 20;

    @Mock private ShareLifecycleService shareLifecycleService;
    @Mock private ShareSlideRepository shareSlideRepository;
    @Mock private PdfSlideRenderer pdfSlideRenderer;
    @Mock private SlideImageStorage slideImageStorage;

    private PresentationUploadService service;

    private final UUID roomId = UUID.randomUUID();
    private final UUID publisherId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PresentationUploadService(
                shareLifecycleService, shareSlideRepository, pdfSlideRenderer, slideImageStorage, MAX_FILE_SIZE_BYTES);
    }

    private RoomParticipant publisher() {
        Room room = new Room();
        room.setId(roomId);
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);
        publisher.setRoom(room);
        return publisher;
    }

    @Test
    void emptyFile_isRejected_beforeAnyAuthOrRenderCall() {
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.upload(roomId, publisherId, "Deck", file))
                .isInstanceOf(PresentationUploadException.class)
                .hasMessageContaining("empty");

        verifyNoInteractions(pdfSlideRenderer, slideImageStorage, shareSlideRepository);
        verify(shareLifecycleService, never()).requireHostPublisher(any(), any());
        verify(shareLifecycleService, never()).startPresentation(any(), anyString());
    }

    @Test
    void oversizedFile_isRejected_beforeAnyAuthOrRenderCall() {
        byte[] tooLarge = new byte[(int) MAX_FILE_SIZE_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", tooLarge);

        assertThatThrownBy(() -> service.upload(roomId, publisherId, "Deck", file))
                .isInstanceOf(PresentationUploadException.class)
                .hasMessageContaining("exceeds");

        verifyNoInteractions(pdfSlideRenderer, slideImageStorage, shareSlideRepository);
        verify(shareLifecycleService, never()).requireHostPublisher(any(), any());
    }

    @Test
    void happyPath_rendersUploadsEachPageAndSavesOrderedShareSlideRows() {
        byte[] pdfBytes = "small-fake-pdf".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", pdfBytes);

        RoomParticipant publisher = publisher();
        when(shareLifecycleService.requireHostPublisher(roomId, publisherId)).thenReturn(publisher);

        byte[] page0 = "png-bytes-0".getBytes(StandardCharsets.UTF_8);
        byte[] page1 = "png-bytes-1".getBytes(StandardCharsets.UTF_8);
        when(pdfSlideRenderer.render(pdfBytes)).thenReturn(List.of(page0, page1));

        UUID shareId = UUID.randomUUID();
        Share share = new Share();
        share.setId(shareId);
        share.setKind(Share.Kind.PRESENTATION);
        when(shareLifecycleService.startPresentation(publisher, "Deck")).thenReturn(share);

        PresentationUploadResult result = service.upload(roomId, publisherId, "Deck", file);

        assertThat(result.share()).isSameAs(share);
        assertThat(result.slides()).hasSize(2);

        verify(slideImageStorage).upload(eq("shares/" + shareId + "/slides/0.png"), eq(page0), eq("image/png"));
        verify(slideImageStorage).upload(eq("shares/" + shareId + "/slides/1.png"), eq(page1), eq("image/png"));

        ArgumentCaptor<ShareSlide> slideCaptor = ArgumentCaptor.forClass(ShareSlide.class);
        verify(shareSlideRepository, org.mockito.Mockito.times(2)).save(slideCaptor.capture());
        List<ShareSlide> savedSlides = slideCaptor.getAllValues();
        assertThat(savedSlides.get(0).getSlideIndex()).isEqualTo(0);
        assertThat(savedSlides.get(0).getImageUrl()).isEqualTo("shares/" + shareId + "/slides/0.png");
        assertThat(savedSlides.get(0).getShare()).isSameAs(share);
        assertThat(savedSlides.get(1).getSlideIndex()).isEqualTo(1);
        assertThat(savedSlides.get(1).getImageUrl()).isEqualTo("shares/" + shareId + "/slides/1.png");
    }

    @Test
    void rendererRejectsThePdf_propagatesAndCreatesNoShareOrUploads() {
        byte[] pdfBytes = "small-fake-pdf".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", pdfBytes);

        RoomParticipant publisher = publisher();
        when(shareLifecycleService.requireHostPublisher(roomId, publisherId)).thenReturn(publisher);
        when(pdfSlideRenderer.render(pdfBytes))
                .thenThrow(new PresentationUploadException("PDF has no pages"));

        assertThatThrownBy(() -> service.upload(roomId, publisherId, "Deck", file))
                .isInstanceOf(PresentationUploadException.class)
                .hasMessageContaining("no pages");

        verify(shareLifecycleService, never()).startPresentation(any(), anyString());
        verifyNoInteractions(slideImageStorage, shareSlideRepository);
    }

    @Test
    void pageWriteDoesNotVerify_abortsWholeUploadRatherThanLeavingAnOrphanedShareSlideRow() {
        byte[] pdfBytes = "small-fake-pdf".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", pdfBytes);

        RoomParticipant publisher = publisher();
        when(shareLifecycleService.requireHostPublisher(roomId, publisherId)).thenReturn(publisher);

        byte[] page0 = "png-bytes-0".getBytes(StandardCharsets.UTF_8);
        byte[] page1 = "png-bytes-1".getBytes(StandardCharsets.UTF_8);
        when(pdfSlideRenderer.render(pdfBytes)).thenReturn(List.of(page0, page1));

        UUID shareId = UUID.randomUUID();
        Share share = new Share();
        share.setId(shareId);
        when(shareLifecycleService.startPresentation(publisher, "Deck")).thenReturn(share);

        // Page 0's write verifies fine. Page 1's write silently didn't land in
        // storage, exactly like the live NoSuchKeyException incident.
        String firstKey = "shares/" + shareId + "/slides/0.png";
        String secondKey = "shares/" + shareId + "/slides/1.png";
        when(slideImageStorage.download(firstKey)).thenReturn(page0);
        when(slideImageStorage.download(secondKey))
                .thenThrow(new IllegalArgumentException("Slide image not found in storage: " + secondKey));

        assertThatThrownBy(() -> service.upload(roomId, publisherId, "Deck", file))
                .isInstanceOf(PresentationUploadException.class)
                .hasMessageContaining("slide 2")
                .hasMessageContaining("try uploading again");

        // The loop must stop at the first unverified page — it never reaches
        // page 1's save() call. (The @Transactional on upload() rolls back page
        // 0's save() too, at the real DB layer — not exercised by this unit test.)
        verify(shareSlideRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void blankLabel_fallsBackToOriginalFilename() {
        byte[] pdfBytes = "small-fake-pdf".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "quarterly-deck.pdf", "application/pdf", pdfBytes);

        RoomParticipant publisher = publisher();
        when(shareLifecycleService.requireHostPublisher(roomId, publisherId)).thenReturn(publisher);
        when(pdfSlideRenderer.render(pdfBytes)).thenReturn(List.of());

        UUID shareId = UUID.randomUUID();
        Share share = new Share();
        share.setId(shareId);
        when(shareLifecycleService.startPresentation(publisher, "quarterly-deck.pdf")).thenReturn(share);

        service.upload(roomId, publisherId, "  ", file);

        verify(shareLifecycleService).startPresentation(publisher, "quarterly-deck.pdf");
    }
}
