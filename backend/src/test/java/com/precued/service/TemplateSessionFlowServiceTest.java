package com.precued.service;

import com.precued.controller.dto.SaveTemplateSessionFlowRequest;
import com.precued.controller.dto.TemplateSessionFlowResponse;
import com.precued.entity.Template;
import com.precued.entity.TemplateRole;
import com.precued.entity.TemplateStage;
import com.precued.entity.TemplateStageRole;
import com.precued.entity.User;
import com.precued.repository.TemplateRepository;
import com.precued.repository.TemplateRoleRepository;
import com.precued.repository.TemplateStageRepository;
import com.precued.repository.TemplateStageRoleRepository;
import com.precued.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateSessionFlowServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateRoleRepository templateRoleRepository;
    @Mock private TemplateStageRepository templateStageRepository;
    @Mock private TemplateStageRoleRepository templateStageRoleRepository;

    private TemplateSessionFlowService service;
    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TemplateSessionFlowService(
                templateRepository,
                templateRoleRepository,
                templateStageRepository,
                templateStageRoleRepository);

        lenient().when(templateStageRepository.save(any(TemplateStage.class))).thenAnswer(invocation -> {
            TemplateStage stage = invocation.getArgument(0);
            if (stage.getId() == null) stage.setId(UUID.randomUUID());
            return stage;
        });
        lenient().when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void save_ownerCanEnableOrderedStagesWithActiveRolesAndOptionalTimer() {
        CurrentUserContext.set(user(ownerId));
        Template template = customTemplate(ownerId);
        TemplateRole interviewer = role(template, "interviewer", true, 0);
        TemplateRole candidate = role(template, "candidate", false, 1);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(template.getId()))
                .thenReturn(List.of(interviewer, candidate));
        when(templateStageRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of());
        when(templateStageRoleRepository.findByTemplateStageId(any())).thenReturn(List.of());

        SaveTemplateSessionFlowRequest request = new SaveTemplateSessionFlowRequest(true, List.of(
                new SaveTemplateSessionFlowRequest.Stage(null, "intro", "Introductions", null, List.of(interviewer.getId(), candidate.getId())),
                new SaveTemplateSessionFlowRequest.Stage(null, "questions", "Interview Questions", 600, List.of(candidate.getId()))));

        TemplateSessionFlowResponse result = service.save(template.getId(), request);

        assertThat(template.isSessionFlowEnabled()).isTrue();
        assertThat(result.enabled()).isTrue();
        assertThat(result.stages()).extracting(TemplateSessionFlowResponse.Stage::stageKey)
                .containsExactly("intro", "questions");
        assertThat(result.stages()).extracting(TemplateSessionFlowResponse.Stage::sortOrder)
                .containsExactly(0, 1);
        assertThat(result.stages().get(0).durationSeconds()).isNull();
        assertThat(result.stages().get(1).durationSeconds()).isEqualTo(600);
        assertThat(result.stages().get(0).templateRoleIds())
                .containsExactly(interviewer.getId(), candidate.getId());
        verify(templateRepository).save(template);
    }

    @Test
    void save_disablingFlowDoesNotDeleteSavedStages() {
        CurrentUserContext.set(user(ownerId));
        Template template = customTemplate(ownerId);
        template.setSessionFlowEnabled(true);
        TemplateRole candidate = role(template, "candidate", false, 0);
        TemplateStage existing = stage(template, "questions", "Questions", 0, 300);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of(candidate));
        when(templateStageRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of(existing));
        when(templateStageRoleRepository.findByTemplateStageId(existing.getId()))
                .thenReturn(List.of(stageRole(existing, candidate)));

        SaveTemplateSessionFlowRequest request = new SaveTemplateSessionFlowRequest(false, List.of(
                new SaveTemplateSessionFlowRequest.Stage(existing.getId(), "questions", "Questions", 300, List.of(candidate.getId()))));

        TemplateSessionFlowResponse result = service.save(template.getId(), request);

        assertThat(result.enabled()).isFalse();
        assertThat(result.stages()).hasSize(1);
        assertThat(result.stages().get(0).id()).isEqualTo(existing.getId());
        verify(templateStageRepository, never()).delete(existing);
    }

    @Test
    void save_reordersExistingStagesWithoutChangingTheirIds() {
        CurrentUserContext.set(user(ownerId));
        Template template = customTemplate(ownerId);
        TemplateRole role = role(template, "candidate", false, 0);
        TemplateStage first = stage(template, "first", "First", 0, null);
        TemplateStage second = stage(template, "second", "Second", 1, null);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of(role));
        when(templateStageRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of(first, second));
        when(templateStageRoleRepository.findByTemplateStageId(first.getId())).thenReturn(List.of(stageRole(first, role)));
        when(templateStageRoleRepository.findByTemplateStageId(second.getId())).thenReturn(List.of(stageRole(second, role)));

        TemplateSessionFlowResponse result = service.save(template.getId(), new SaveTemplateSessionFlowRequest(true, List.of(
                new SaveTemplateSessionFlowRequest.Stage(second.getId(), "second", "Second", null, List.of(role.getId())),
                new SaveTemplateSessionFlowRequest.Stage(first.getId(), "first", "First", null, List.of(role.getId())))));

        assertThat(result.stages()).extracting(TemplateSessionFlowResponse.Stage::id)
                .containsExactly(second.getId(), first.getId());
        assertThat(result.stages()).extracting(TemplateSessionFlowResponse.Stage::sortOrder)
                .containsExactly(0, 1);
    }

    @Test
    void save_rejectsRoleThatDoesNotBelongToTemplate() {
        CurrentUserContext.set(user(ownerId));
        Template template = customTemplate(ownerId);
        TemplateRole ownedRole = role(template, "candidate", false, 0);
        UUID foreignRoleId = UUID.randomUUID();
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of(ownedRole));
        when(templateStageRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of());

        SaveTemplateSessionFlowRequest request = new SaveTemplateSessionFlowRequest(true, List.of(
                new SaveTemplateSessionFlowRequest.Stage(null, "questions", "Questions", 120, List.of(foreignRoleId))));

        assertThatThrownBy(() -> service.save(template.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("role");
        verify(templateStageRepository, never()).save(any());
    }

    @Test
    void save_someoneElsesPrivateTemplateIsRejectedAsNotFound() {
        CurrentUserContext.set(user(otherUserId));
        Template template = customTemplate(ownerId);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.save(template.getId(), new SaveTemplateSessionFlowRequest(false, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No Template with id " + template.getId());
        verify(templateStageRepository, never()).save(any());
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail(id + "@example.com");
        user.setDisplayName("Test User");
        return user;
    }

    private Template customTemplate(UUID ownerId) {
        Template template = new Template();
        template.setId(UUID.randomUUID().toString());
        template.setName("Panel Interview");
        template.setCreatedBy(user(ownerId));
        template.setCreatedAt(Instant.now());
        return template;
    }

    private TemplateRole role(Template template, String key, boolean host, int sortOrder) {
        TemplateRole role = new TemplateRole();
        role.setId(UUID.randomUUID());
        role.setTemplate(template);
        role.setRoleKey(key);
        role.setName(key);
        role.setHostRole(host);
        role.setSortOrder(sortOrder);
        return role;
    }

    private TemplateStage stage(Template template, String key, String name, int sortOrder, Integer durationSeconds) {
        TemplateStage stage = new TemplateStage();
        stage.setId(UUID.randomUUID());
        stage.setTemplate(template);
        stage.setStageKey(key);
        stage.setName(name);
        stage.setSortOrder(sortOrder);
        stage.setDurationSeconds(durationSeconds);
        return stage;
    }

    private TemplateStageRole stageRole(TemplateStage stage, TemplateRole role) {
        TemplateStageRole link = new TemplateStageRole();
        link.setTemplateStage(stage);
        link.setTemplateRole(role);
        return link;
    }
}
