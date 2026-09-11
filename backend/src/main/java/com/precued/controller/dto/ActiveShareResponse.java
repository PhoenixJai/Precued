package com.precued.controller.dto;

import com.precued.entity.Share;

import java.util.List;
import java.util.UUID;

/**
 * kind/currentSlideIndex added for Chunk 3 (Precued_DataModel.md
 * "Presentations Feature") so a client already polling this endpoint
 * (see CallPage.tsx's existing 1.5s refresh) picks up a host's slide
 * advance for free — no new polling loop needed.
 */
public record ActiveShareResponse(UUID id, String label, Share.Kind kind, int currentSlideIndex, List<UUID> roomRoleIds) {
}
