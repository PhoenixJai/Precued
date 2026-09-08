package com.precued.controller;

import com.precued.controller.dto.TemplatePresetResponse;
import com.precued.service.TemplatePresetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplatePresetService templatePresetService;

    public TemplateController(TemplatePresetService templatePresetService) {
        this.templatePresetService = templatePresetService;
    }

    @GetMapping("/{templateId}/presets")
    public List<TemplatePresetResponse> listPresets(@PathVariable String templateId) {
        return templatePresetService.listForTemplate(templateId);
    }
}
