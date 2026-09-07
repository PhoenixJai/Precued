package com.precued.engine;

import java.util.List;

/**
 * One connected RoomParticipant's subscription permission for a single Share,
 * per Part A's output shape (Precued_DataModel.md § VisibilityEngine —
 * Interface Spec). {@code trackSids} is empty when {@code allowed} is false.
 */
public record ParticipantTrackPermission(
        String livekitIdentity,
        boolean allowed,
        List<String> trackSids) {
}
