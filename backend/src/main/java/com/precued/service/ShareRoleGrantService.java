package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.RoomRole;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.entity.ShareSlide;
import com.precued.repository.RoomRoleRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareRoleGrantRepository;
import com.precued.repository.ShareSlideRepository;
import com.precued.security.CurrentParticipantContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal write + VisibilityEngine trigger wiring for the ShareRoleGrant
 * row of the trigger table: a grant change is scoped to just that one
 * Share, not the whole room.
 */
@Service
public class ShareRoleGrantService {

    private final ShareRoleGrantRepository grantRepository;
    private final ShareRepository shareRepository;
    private final RoomRoleRepository roomRoleRepository;
    private final ShareSlideRepository shareSlideRepository;
    private final VisibilityEngine engine;

    public ShareRoleGrantService(
            ShareRoleGrantRepository grantRepository,
            ShareRepository shareRepository,
            RoomRoleRepository roomRoleRepository,
            ShareSlideRepository shareSlideRepository,
            VisibilityEngine engine) {
        this.grantRepository = grantRepository;
        this.shareRepository = shareRepository;
        this.roomRoleRepository = roomRoleRepository;
        this.shareSlideRepository = shareSlideRepository;
        this.engine = engine;
    }

    /** Whole-share grant (share_slide_id NULL) — unchanged since before Chunk 3. */
    public ShareRoleGrant grant(UUID shareId, UUID roomRoleId) {
        return grant(shareId, roomRoleId, null);
    }

    /**
     * Chunk 3 (Precued_DataModel.md "Presentations Feature" — per-slide
     * visibility controls): shareSlideId null means the existing whole-share
     * grant behavior, unaffected; a non-null value scopes the grant to only
     * that slide, per the Runtime Rule's Chunk 1 extension. The slide must
     * belong to this same Share.
     */
    public ShareRoleGrant grant(UUID shareId, UUID roomRoleId, UUID shareSlideId) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("No Share with id " + shareId));
        RoomRole role = roomRoleRepository.findById(roomRoleId)
                .orElseThrow(() -> new IllegalArgumentException("No RoomRole with id " + roomRoleId));

        if (!CurrentParticipantContext.get().getId().equals(share.getPublisher().getId())) {
            throw new IllegalStateException("Only the Share's publisher can grant visibility to it");
        }

        ShareSlide shareSlide = null;
        if (shareSlideId != null) {
            shareSlide = shareSlideRepository.findById(shareSlideId)
                    .orElseThrow(() -> new IllegalArgumentException("No ShareSlide with id " + shareSlideId));
            if (!shareSlide.getShare().getId().equals(shareId)) {
                throw new IllegalArgumentException(
                        "ShareSlide " + shareSlideId + " does not belong to Share " + shareId);
            }
        }

        ShareRoleGrant grant = new ShareRoleGrant();
        grant.setShare(share);
        grant.setRoomRole(role);
        grant.setShareSlide(shareSlide);
        grant.setGrantedAt(Instant.now());
        ShareRoleGrant saved = grantRepository.save(grant);

        engine.recomputeAndPushForShare(shareId);
        return saved;
    }

    public ShareRoleGrant revoke(UUID grantId) {
        ShareRoleGrant grant = grantRepository.findById(grantId)
                .orElseThrow(() -> new IllegalArgumentException("No ShareRoleGrant with id " + grantId));

        // grant.getShare() is a lazy proxy: .getId() alone is safe, but
        // .getPublisher() would force Hibernate to initialize it, which
        // needs a session this (non-transactional) method doesn't hold.
        // Re-fetch the Share directly instead of reading through the proxy.
        Share share = shareRepository.findById(grant.getShare().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "ShareRoleGrant " + grantId + " references a Share that no longer exists"));
        if (!CurrentParticipantContext.get().getId().equals(share.getPublisher().getId())) {
            throw new IllegalStateException("Only the Share's publisher can revoke visibility on it");
        }

        grant.setRevokedAt(Instant.now());
        ShareRoleGrant saved = grantRepository.save(grant);

        engine.recomputeAndPushForShare(saved.getShare().getId());
        return saved;
    }
}
