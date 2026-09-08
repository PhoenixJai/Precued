package com.precued.controller.dto;

import com.precued.entity.TemplatePreset;

import java.util.List;
import java.util.UUID;

public record TemplatePresetResponse(
        UUID id, String templateId, String name, int sortOrder, List<String> roleKeys) {

    public static TemplatePresetResponse from(TemplatePreset preset, List<String> roleKeys) {
        return new TemplatePresetResponse(
                preset.getId(),
                preset.getTemplate().getId(),
                preset.getName(),
                preset.getSortOrder(),
                roleKeys);
    }
}
