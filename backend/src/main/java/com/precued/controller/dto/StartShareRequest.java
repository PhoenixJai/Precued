package com.precued.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** appliedPresetId is optional — Share.applied_preset_id is nullable per Precued_DataModel.md. */
public record StartShareRequest(
        @NotNull UUID roomId,
        @NotNull UUID publisherParticipantId,
        UUID appliedPresetId,
        @NotBlank String label) {
}
