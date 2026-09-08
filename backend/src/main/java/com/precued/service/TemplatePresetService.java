package com.precued.service;

import com.precued.controller.dto.TemplatePresetResponse;
import com.precued.entity.TemplateRole;
import com.precued.repository.TemplatePresetRepository;
import com.precued.repository.TemplatePresetRoleRepository;
import com.precued.repository.TemplateRepository;
import com.precued.repository.TemplateRoleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TemplatePresetService {

    private final TemplateRepository templateRepository;
    private final TemplatePresetRepository templatePresetRepository;
    private final TemplatePresetRoleRepository templatePresetRoleRepository;
    private final TemplateRoleRepository templateRoleRepository;

    public TemplatePresetService(
            TemplateRepository templateRepository,
            TemplatePresetRepository templatePresetRepository,
            TemplatePresetRoleRepository templatePresetRoleRepository,
            TemplateRoleRepository templateRoleRepository) {
        this.templateRepository = templateRepository;
        this.templatePresetRepository = templatePresetRepository;
        this.templatePresetRoleRepository = templatePresetRoleRepository;
        this.templateRoleRepository = templateRoleRepository;
    }

    public List<TemplatePresetResponse> listForTemplate(String templateId) {
        if (!templateRepository.existsById(templateId)) {
            throw new IllegalArgumentException("No Template with id " + templateId);
        }

        return templatePresetRepository.findByTemplateIdOrderBySortOrder(templateId).stream()
                .map(preset -> TemplatePresetResponse.from(preset, roleKeysForPreset(preset.getId())))
                .toList();
    }

    /**
     * TemplatePresetRole associations are LAZY and open-in-view is disabled,
     * so anything beyond .getId() on templateRole/preset proxies here would
     * throw once the repository call's own transaction closes. Collect the
     * (safe) ids, then re-fetch the TemplateRoles directly to read roleKey.
     */
    private List<String> roleKeysForPreset(UUID presetId) {
        List<UUID> templateRoleIds = templatePresetRoleRepository.findByPresetId(presetId).stream()
                .map(presetRole -> presetRole.getTemplateRole().getId())
                .toList();
        if (templateRoleIds.isEmpty()) {
            return List.of();
        }
        return templateRoleRepository.findAllById(templateRoleIds).stream()
                .map(TemplateRole::getRoleKey)
                .toList();
    }
}
