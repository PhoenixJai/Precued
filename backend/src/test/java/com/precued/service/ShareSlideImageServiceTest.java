package com.precued.service;

import com.precued.engine.ParticipantTrackPermission;
import com.precued.engine.VisibilityEngine;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.entity.ShareSlide;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareSlideRepository;
import com.precued.security.CurrentParticipantContext;
import com.precued.storage.SlideImageStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers the access decision documented for Chunk 2: slide images are
 * proxied through the backend, never served from a public/presigned R2
 * URL, so every fetch goes through the same live authorization check
 * (VisibilityEngine's Runtime Rule) as everything else — including its
 * immediate-revocation guarantee, which a signed URL with any TTL would
 * regress. Two-tier access: the Share's own publisher can always preview
 * any slide (their own content, for navigation); anyone else may only
 * fetch the slide that is CURRENTLY live, and only if their role has a
 * qualifying grant per the Runtime Rule.
 */
@ExtendWith(MockitoExtension.class)
class ShareSlideImageServiceTest {

    @Mock private ShareRepository shareRepository;
    @Mock private ShareSlideRepository shareSlideRepository;
    @Mock private SlideImageStorage slideImageStorage;
    @Mock private VisibilityEngine engine;

    private ShareSlideImageService service;

    private final UUID shareId = UUID.randomUUID();
    private final UUID publisherId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ShareSlideImageService(shareRepository, shareSlideRepository, slideImageStorage, engine);
    }

    @AfterEach
    void clearContext() {
        CurrentParticipantContext.clear();
    }

    private Share presentationShare(int currentSlideIndex) {
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);

        Share share = new Share();
        share.setId(shareId);
        share.setPublisher(publisher);
        share.setKind(Share.Kind.PRESENTATION);
        share.setCurrentSlideIndex(currentSlideIndex);
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        return share;
    }

    private void actingAs(UUID participantId, String livekitIdentity) {
        RoomParticipant self = new RoomParticipant();
        self.setId(participantId);
        self.setLivekitIdentity(livekitIdentity);
        CurrentParticipantContext.set(self);
    }

    private ShareSlide slideAt(int index, byte[] bytes) {
        ShareSlide slide = new ShareSlide();
        slide.setId(UUID.randomUUID());
        slide.setSlideIndex(index);
        slide.setImageUrl("shares/" + shareId + "/slides/" + index + ".png");
        when(shareSlideRepository.findByShareIdAndSlideIndex(shareId, index)).thenReturn(Optional.of(slide));
        when(slideImageStorage.download(slide.getImageUrl())).thenReturn(bytes);
        return slide;
    }

    @Test
    void publisher_canFetchTheCurrentSlide() {
        presentationShare(0);
        actingAs(publisherId, "host-1");
        byte[] bytes = "png-0".getBytes();
        slideAt(0, bytes);

        byte[] result = service.fetch(shareId, 0);

        assertThat(result).isEqualTo(bytes);
    }

    @Test
    void publisher_canFetchANonCurrentSlide_forPreviewNavigation() {
        presentationShare(0); // current is 0, but publisher asks for slide 2
        actingAs(publisherId, "host-1");
        byte[] bytes = "png-2".getBytes();
        slideAt(2, bytes);

        byte[] result = service.fetch(shareId, 2);

        assertThat(result).isEqualTo(bytes);
    }

    @Test
    void nonPublisher_withQualifyingGrant_canFetchOnlyTheCurrentSlide() {
        presentationShare(1);
        UUID viewerId = UUID.randomUUID();
        actingAs(viewerId, "viewer-1");
        byte[] bytes = "png-1".getBytes();
        slideAt(1, bytes);
        when(engine.computeGrantsForShare(shareId))
                .thenReturn(List.of(new ParticipantTrackPermission("viewer-1", true, List.of())));

        byte[] result = service.fetch(shareId, 1);

        assertThat(result).isEqualTo(bytes);
    }

    @Test
    void nonPublisher_requestingANonCurrentSlide_isDenied_evenIfEngineWouldAllowCurrent() {
        presentationShare(1); // current slide is 1
        UUID viewerId = UUID.randomUUID();
        actingAs(viewerId, "viewer-1");
        // Deliberately no stub of engine.computeGrantsForShare: it must never
        // even be consulted once the non-current-slide rule already denies.

        assertThatThrownBy(() -> service.fetch(shareId, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nonPublisher_notAllowedByEngine_isDenied() {
        presentationShare(1);
        UUID viewerId = UUID.randomUUID();
        actingAs(viewerId, "viewer-1");
        when(engine.computeGrantsForShare(shareId))
                .thenReturn(List.of(new ParticipantTrackPermission("viewer-1", false, List.of())));

        assertThatThrownBy(() -> service.fetch(shareId, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nonPublisher_notInComputedGrantsAtAll_isDenied() {
        presentationShare(1);
        UUID viewerId = UUID.randomUUID();
        actingAs(viewerId, "viewer-1");
        when(engine.computeGrantsForShare(shareId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.fetch(shareId, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownShare_throwsNotFound() {
        when(shareRepository.findById(shareId)).thenReturn(Optional.empty());
        actingAs(UUID.randomUUID(), "someone");

        assertThatThrownBy(() -> service.fetch(shareId, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void screenKindShare_isNeverServeable_evenAtIndexZero() {
        Share share = new Share();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);
        share.setId(shareId);
        share.setPublisher(publisher);
        // kind defaults to SCREEN
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        UUID viewerId = UUID.randomUUID();
        actingAs(viewerId, "viewer-1");

        assertThatThrownBy(() -> service.fetch(shareId, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownSlideIndex_forAnAuthorizedRequester_throwsNotFound() {
        presentationShare(0);
        actingAs(publisherId, "host-1"); // publisher, so always authorized
        when(shareSlideRepository.findByShareIdAndSlideIndex(shareId, 7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetch(shareId, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
