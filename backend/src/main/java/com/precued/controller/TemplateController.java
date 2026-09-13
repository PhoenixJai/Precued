package com.precued.controller;

import com.precued.controller.dto.CreateTemplateRequest;
import com.precued.controller.dto.CreateTemplateRoleRequest;
import com.precued.controller.dto.TemplatePresetResponse;
import com.precued.controller.dto.TemplateResponse;
import com.precued.controller.dto.TemplateRoleResponse;
import com.precued.service.TemplatePresetService;
import com.precued.service.TemplateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplatePresetService templatePresetService;
    private final TemplateService templateService;

    public TemplateController(TemplatePresetService templatePresetService, TemplateService templateService) {
        this.templatePresetService = templatePresetService;
        this.templateService = templateService;
    }

    @GetMapping("/{templateId}/presets")
    public List<TemplatePresetResponse> listPresets(@PathVariable String templateId) {
        return templatePresetService.listForTemplate(templateId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(@Valid @RequestBody CreateTemplateRequest request) {
        return TemplateResponse.from(templateService.createCustomTemplate(request.name()));
    }

    @GetMapping("/mine")
    public List<TemplateResponse> listMine() {
        return templateService.listMine().stream().map(TemplateResponse::from).toList();
    }

    @GetMapping("/{templateId}")
    public TemplateResponse get(@PathVariable String templateId) {
        return TemplateResponse.from(templateService.get(templateId));
    }

    @GetMapping("/{templateId}/roles")
    public List<TemplateRoleResponse> listRoles(@PathVariable String templateId) {
        return templateService.listRoles(templateId).stream().map(TemplateRoleResponse::from).toList();
    }

    @PostMapping("/{templateId}/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateRoleResponse addRole(
            @PathVariable String templateId, @Valid @RequestBody CreateTemplateRoleRequest request) {
        return TemplateRoleResponse.from(templateService.addRole(
                templateId, request.roleKey(), request.name(), request.isHostRole(), request.isGuestRole(),
                request.maxMembers()));
    }

    @DeleteMapping("/{templateId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(@PathVariable String templateId, @PathVariable UUID roleId) {
        templateService.removeRole(templateId, roleId);
    }
}
