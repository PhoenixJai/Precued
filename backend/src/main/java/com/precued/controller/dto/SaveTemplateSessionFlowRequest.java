package com.precued.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * Whole-definition save contract for the custom Session Flow builder.
 * List order is authoritative for sort_order. Existing stage ids are kept
 * stable across edits/reorders; a null id creates a new TemplateStage.
 */
public record SaveTemplateSessionFlowRequest(
        boolean enabled,
        @NotNull List<@Valid Stage> stages) {

    public record Stage(
            UUID id,
            @NotBlank String stageKey,
            @NotBlank String name,
            @Positive Integer durationSeconds,
            @NotEmpty List<@NotNull UUID> templateRoleIds) {
    }
}
