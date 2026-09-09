package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.RoomRole;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.repository.RoomRoleRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareRoleGrantRepository;
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
    private final VisibilityEngine engine;

    public ShareRoleGrantService(
            ShareRoleGrantRepository grantRepository,
            ShareRepository shareRepository,
            RoomRoleRepository roomRoleRepository,
            VisibilityEngine engine) {
        this.grantRepository = grantRepository;
        this.shareRepository = shareRepository;
        this.roomRoleRepository = roomRoleRepository;
        this.engine = engine;
    }

    public ShareRoleGrant grant(UUID shareId, UUID roomRoleId) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("No Share with id " + shareId));
        RoomRole role = roomRoleRepository.findById(roomRoleId)
                .orElseThrow(() -> new IllegalArgumentException("No RoomRole with id " + roomRoleId));

        if (!CurrentParticipantContext.get().getId().equals(share.getPublisher().getId())) {
            throw new IllegalStateException("Only the Share's publisher can grant visibility to it");
        }

        ShareRoleGrant grant = new ShareRoleGrant();
        grant.setShare(share);
        grant.setRoomRole(role);
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
