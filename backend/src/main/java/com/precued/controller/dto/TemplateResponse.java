package com.precued.controller.dto;

import com.precued.entity.Template;

import java.time.Instant;

public record TemplateResponse(String id, String name, boolean isCustom, Instant createdAt) {

    public static TemplateResponse from(Template template) {
        return new TemplateResponse(
                template.getId(), template.getName(), template.getCreatedBy() != null, template.getCreatedAt());
    }
}
