package com.precued.controller.dto;

import java.util.List;
import java.util.UUID;

/** Config-time Session Flow definition for the custom-template builder. */
public record TemplateSessionFlowResponse(
        String templateId,
        boolean enabled,
        List<Stage> stages) {

    public record Stage(
            UUID id,
            String stageKey,
            String name,
            int sortOrder,
            Integer durationSeconds,
            List<UUID> templateRoleIds) {
    }
}
