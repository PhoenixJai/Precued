package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.entity.TemplatePreset;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.TemplatePresetRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates a Share when a host starts sharing — the "Share started" row of
 * the trigger table (Precued_DataModel.md § "VisibilityEngine — Interface
 * Spec"). Never trusts the caller on the publisher's host-role requirement
 * ("must hold a host role" per the Share entity's own doc comment): it is
 * checked here against the publisher's currently active
 * ParticipantRoleAssignment, not assumed from whoever calls this.
 */
@Service
public class ShareLifecycleService {

    private final ShareRepository shareRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final ParticipantRoleAssignmentRepository assignmentRepository;
    private final TemplatePresetRepository templatePresetRepository;
    private final VisibilityEngine engine;

    public ShareLifecycleService(
            ShareRepository shareRepository,
            RoomParticipantRepository roomParticipantRepository,
            ParticipantRoleAssignmentRepository assignmentRepository,
            TemplatePresetRepository templatePresetRepository,
            VisibilityEngine engine) {
        this.shareRepository = shareRepository;
        this.roomParticipantRepository = roomParticipantRepository;
        this.assignmentRepository = assignmentRepository;
        this.templatePresetRepository = templatePresetRepository;
        this.engine = engine;
    }

    /**
     * Creates and starts a Share (status ACTIVE, startedAt now), then runs
     * a fresh compute+push for it — no prior state, per the trigger table.
     * Rejects a publisher who does not currently hold a host role.
     */
    public Share start(UUID roomId, UUID publisherParticipantId, UUID appliedPresetId, String label) {
        RoomParticipant publisher = roomParticipantRepository.findById(publisherParticipantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No RoomParticipant with id " + publisherParticipantId));

        if (!publisher.getRoom().getId().equals(roomId)) {
            throw new IllegalArgumentException(
                    "RoomParticipant " + publisherParticipantId + " does not belong to room " + roomId);
        }

        ParticipantRoleAssignment activeAssignment = assignmentRepository
                .findByRoomParticipantIdAndRevokedAtIsNull(publisherParticipantId)
                .orElseThrow(() -> new IllegalStateException(
                        "RoomParticipant " + publisherParticipantId + " has no active role assignment"
                                + " and cannot start a Share"));

        if (!activeAssignment.getRoomRole().isHostRole()) {
            throw new IllegalStateException(
                    "RoomParticipant " + publisherParticipantId
                            + " does not hold a host role and cannot start a Share");
        }

        TemplatePreset appliedPreset = appliedPresetId == null
                ? null
                : templatePresetRepository.findById(appliedPresetId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No TemplatePreset with id " + appliedPresetId));

        Share share = new Share();
        share.setRoom(publisher.getRoom());
        share.setPublisher(publisher);
        share.setAppliedPreset(appliedPreset);
        share.setLabel(label);
        share.setStatus(Share.Status.ACTIVE);
        share.setStartedAt(Instant.now());

        Share saved = shareRepository.save(share);
        engine.recomputeAndPushForShare(saved.getId());
        return saved;
    }
}
