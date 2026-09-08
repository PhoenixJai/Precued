package com.precued.service;

import com.precued.controller.dto.TemplatePresetResponse;
import com.precued.entity.Template;
import com.precued.entity.TemplatePreset;
import com.precued.entity.TemplatePresetRole;
import com.precued.entity.TemplateRole;
import com.precued.repository.TemplatePresetRepository;
import com.precued.repository.TemplatePresetRoleRepository;
import com.precued.repository.TemplateRepository;
import com.precued.repository.TemplateRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers TemplatePresetService#listForTemplate: presets for a template,
 * each resolved with the role_key values of its associated TemplateRoles
 * (via the TemplatePresetRole join table) — needed so the frontend can
 * render preset pickers without a second round trip per preset.
 */
@ExtendWith(MockitoExtension.class)
class TemplatePresetServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private TemplatePresetRepository templatePresetRepository;
    @Mock private TemplatePresetRoleRepository templatePresetRoleRepository;
    @Mock private TemplateRoleRepository templateRoleRepository;

    private TemplatePresetService service;

    private final String templateId = "mock_trial";

    @BeforeEach
    void setUp() {
        service = new TemplatePresetService(
                templateRepository, templatePresetRepository, templatePresetRoleRepository, templateRoleRepository);
    }

    @Test
    void listForTemplate_existingTemplate_returnsPresetsWithRoleKeys() {
        when(templateRepository.existsById(templateId)).thenReturn(true);

        Template template = new Template();
        template.setId(templateId);

        TemplatePreset preset = new TemplatePreset();
        preset.setId(UUID.randomUUID());
        preset.setTemplate(template);
        preset.setName("Judge + Jury Only");
        preset.setSortOrder(1);
        when(templatePresetRepository.findByTemplateIdOrderBySortOrder(templateId)).thenReturn(List.of(preset));

        UUID judgeRoleId = UUID.randomUUID();
        UUID juryRoleId = UUID.randomUUID();
        TemplatePresetRole judgeLink = presetRoleLink(judgeRoleId);
        TemplatePresetRole juryLink = presetRoleLink(juryRoleId);
        when(templatePresetRoleRepository.findByPresetId(preset.getId())).thenReturn(List.of(judgeLink, juryLink));

        TemplateRole judge = roleWithKey(judgeRoleId, "judge");
        TemplateRole jury = roleWithKey(juryRoleId, "jury");
        when(templateRoleRepository.findAllById(List.of(judgeRoleId, juryRoleId)))
                .thenReturn(List.of(judge, jury));

        List<TemplatePresetResponse> result = service.listForTemplate(templateId);

        assertThat(result).hasSize(1);
        TemplatePresetResponse response = result.get(0);
        assertThat(response.id()).isEqualTo(preset.getId());
        assertThat(response.templateId()).isEqualTo(templateId);
        assertThat(response.name()).isEqualTo("Judge + Jury Only");
        assertThat(response.sortOrder()).isEqualTo(1);
        assertThat(response.roleKeys()).containsExactly("judge", "jury");
    }

    @Test
    void listForTemplate_presetWithNoRoles_returnsEmptyRoleKeys() {
        when(templateRepository.existsById(templateId)).thenReturn(true);

        Template template = new Template();
        template.setId(templateId);
        TemplatePreset preset = new TemplatePreset();
        preset.setId(UUID.randomUUID());
        preset.setTemplate(template);
        preset.setName("Bare");
        preset.setSortOrder(0);
        when(templatePresetRepository.findByTemplateIdOrderBySortOrder(templateId)).thenReturn(List.of(preset));
        when(templatePresetRoleRepository.findByPresetId(preset.getId())).thenReturn(List.of());

        List<TemplatePresetResponse> result = service.listForTemplate(templateId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roleKeys()).isEmpty();
    }

    @Test
    void listForTemplate_unknownTemplate_throwsIllegalArgumentException() {
        when(templateRepository.existsById("nope")).thenReturn(false);

        assertThatThrownBy(() -> service.listForTemplate("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    private TemplatePresetRole presetRoleLink(UUID templateRoleId) {
        TemplateRole role = new TemplateRole();
        role.setId(templateRoleId);
        TemplatePresetRole link = new TemplatePresetRole();
        link.setTemplateRole(role);
        return link;
    }

    private TemplateRole roleWithKey(UUID id, String roleKey) {
        TemplateRole role = new TemplateRole();
        role.setId(id);
        role.setRoleKey(roleKey);
        return role;
    }
}
