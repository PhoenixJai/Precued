package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.Share;
import com.precued.entity.ShareSlide;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareSlideRepository;
import com.precued.security.CurrentParticipantContext;
import com.precued.storage.SlideImageStorage;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Authorized read path for slide images (Chunk 2, Precued_DataModel.md
 * "Presentations Feature"). Judgment call, flagged rather than decided
 * silently: images are proxied through this backend, never served from a
 * public or presigned R2 URL. A presigned URL would create a
 * revocation-lag window — a URL issued while a grant was active stays
 * valid for its whole TTL even if the grant is revoked a second later,
 * which directly regresses Decision #4's "no grant row = never subscribed"
 * immediacy guarantee. Proxying means every fetch re-runs the same
 * VisibilityEngine check live, so revocation takes effect on the very next
 * request — no separate signed-URL security model to reason about.
 *
 * Two-tier access, since a presenter legitimately needs to see slides that
 * aren't current yet (navigation) while a viewer must not:
 * - The Share's own publisher may fetch ANY slide index.
 * - Anyone else may fetch ONLY the slide that is currently live
 *   (Share.currentSlideIndex — Decision #5's "one live presenter position"),
 *   and only if their active role has a qualifying grant per the Runtime
 *   Rule, evaluated via the same VisibilityEngine.computeGrantsForShare
 *   used to compute LiveKit permissions — no separate/duplicated rule.
 */
@Service
public class ShareSlideImageService {

    private final ShareRepository shareRepository;
    private final ShareSlideRepository shareSlideRepository;
    private final SlideImageStorage slideImageStorage;
    private final VisibilityEngine engine;

    public ShareSlideImageService(
            ShareRepository shareRepository,
            ShareSlideRepository shareSlideRepository,
            SlideImageStorage slideImageStorage,
            VisibilityEngine engine) {
        this.shareRepository = shareRepository;
        this.shareSlideRepository = shareSlideRepository;
        this.slideImageStorage = slideImageStorage;
        this.engine = engine;
    }

    public byte[] fetch(UUID shareId, int slideIndex) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("No Share with id " + shareId));

        var requester = CurrentParticipantContext.get();
        boolean isPublisher = share.getPublisher().getId().equals(requester.getId());

        if (!isPublisher) {
            if (share.getKind() != Share.Kind.PRESENTATION || slideIndex != share.getCurrentSlideIndex()) {
                throw new IllegalStateException("Not authorized to view this slide");
            }
            boolean allowed = engine.computeGrantsForShare(shareId).stream()
                    .anyMatch(grant -> grant.livekitIdentity().equals(requester.getLivekitIdentity())
                            && grant.allowed());
            if (!allowed) {
                throw new IllegalStateException("Not authorized to view this slide");
            }
        }

        ShareSlide slide = shareSlideRepository.findByShareIdAndSlideIndex(shareId, slideIndex)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No slide at index " + slideIndex + " for Share " + shareId));

        return slideImageStorage.download(slide.getImageUrl());
    }
}
