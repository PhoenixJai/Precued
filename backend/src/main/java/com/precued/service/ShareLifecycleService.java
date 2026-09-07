package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.Share;
import com.precued.repository.ShareRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Minimal write + VisibilityEngine trigger wiring for the "Share started"
 * row of the trigger table. Takes an already-populated, unsaved Share
 * (room, publisher, label) — choosing those fields, applying a preset's
 * default grants, and validating the publisher holds a host role belong
 * to a future Share-creation feature, not this trigger-wiring pass.
 */
@Service
public class ShareLifecycleService {

    private final ShareRepository shareRepository;
    private final VisibilityEngine engine;

    public ShareLifecycleService(ShareRepository shareRepository, VisibilityEngine engine) {
        this.shareRepository = shareRepository;
        this.engine = engine;
    }

    /** Persists the Share (status ACTIVE, startedAt now) and runs a fresh compute — no prior state. */
    public Share start(Share share) {
        share.setStatus(Share.Status.ACTIVE);
        share.setStartedAt(Instant.now());
        Share saved = shareRepository.save(share);

        engine.recomputeAndPushForShare(saved.getId());
        return saved;
    }
}
