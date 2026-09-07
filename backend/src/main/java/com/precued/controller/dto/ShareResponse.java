package com.precued.controller.dto;

import com.precued.entity.Share;

import java.time.Instant;
import java.util.UUID;

public record ShareResponse(
        UUID id,
        UUID roomId,
        UUID publisherParticipantId,
        UUID appliedPresetId,
        String label,
        Share.Status status,
        Instant startedAt,
        Instant endedAt) {

    public static ShareResponse from(Share share) {
        return new ShareResponse(
                share.getId(),
                share.getRoom().getId(),
                share.getPublisher().getId(),
                share.getAppliedPreset() == null ? null : share.getAppliedPreset().getId(),
                share.getLabel(),
                share.getStatus(),
                share.getStartedAt(),
                share.getEndedAt());
    }
}
