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
import com.precued.security.AuthenticationRequiredException;
import com.precued.security.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authoring service for config-time Session Flow on a custom Template.
 * Runtime Rooms still snapshot these rows in RoomService; editing a Template
 * never mutates an already-created Room.
 */
@Service
public class TemplateSessionFlowService {

    private final TemplateRepository templateRepository;
    private final TemplateRoleRepository templateRoleRepository;
    private final TemplateStageRepository templateStageRepository;
    private final TemplateStageRoleRepository templateStageRoleRepository;

    public TemplateSessionFlowService(
            TemplateRepository templateRepository,
            TemplateRoleRepository templateRoleRepository,
            TemplateStageRepository templateStageRepository,
            TemplateStageRoleRepository templateStageRoleRepository) {
        this.templateRepository = templateRepository;
        this.templateRoleRepository = templateRoleRepository;
        this.templateStageRepository = templateStageRepository;
        this.templateStageRoleRepository = templateStageRoleRepository;
    }

    @Transactional(readOnly = true)
    public TemplateSessionFlowResponse get(String templateId) {
        Template template = requireVisible(templateId);
        List<TemplateStage> stages = templateStageRepository.findByTemplateIdOrderBySortOrder(templateId);
        return toResponse(template, stages);
    }

    /**
     * Saves the entire ordered definition in one transaction. Existing stage
     * ids remain stable when supplied, so simple edits/reorders preserve the
     * optional RoomStage.source_template_stage_id lineage. A removed stage is
     * genuinely deleted; existing Rooms are safe because that FK is ON DELETE
     * SET NULL and their runtime snapshots remain unchanged.
     */
    @Transactional
    public TemplateSessionFlowResponse save(String templateId, SaveTemplateSessionFlowRequest request) {
        Template template = requireOwnedCustomTemplate(templateId);
        List<SaveTemplateSessionFlowRequest.Stage> requestedStages =
                request.stages() == null ? List.of() : request.stages();

        List<TemplateRole> templateRoles = templateRoleRepository.findByTemplateIdOrderBySortOrder(templateId);
        Map<UUID, TemplateRole> roleById = new HashMap<>();
        for (TemplateRole role : templateRoles) roleById.put(role.getId(), role);

        List<TemplateStage> existingStages = templateStageRepository.findByTemplateIdOrderBySortOrder(templateId);
        Map<UUID, TemplateStage> existingById = new HashMap<>();
        for (TemplateStage stage : existingStages) existingById.put(stage.getId(), stage);

        validateRequest(requestedStages, existingById, roleById);

        Set<UUID> retainedIds = new HashSet<>();
        for (SaveTemplateSessionFlowRequest.Stage stage : requestedStages) {
            if (stage.id() != null) retainedIds.add(stage.id());
        }

        // Delete removed stages first and flush so a newly-added stage may
        // legitimately reuse the deleted stage's key in the same save.
        for (TemplateStage existing : existingStages) {
            if (!retainedIds.contains(existing.getId())) {
                templateStageRepository.delete(existing);
            }
        }
        templateStageRepository.flush();

        // Move surviving rows to unique temporary positions before assigning
        // the final order. This avoids violating uq_template_stage_order when
        // two existing stages swap places.
        int temporaryOrder = -1;
        for (TemplateStage existing : existingStages) {
            if (retainedIds.contains(existing.getId())) {
                existing.setSortOrder(temporaryOrder--);
                templateStageRepository.save(existing);
            }
        }
        templateStageRepository.flush();

        template.setSessionFlowEnabled(request.enabled());
        templateRepository.save(template);

        List<TemplateSessionFlowResponse.Stage> responseStages = new ArrayList<>();
        for (int index = 0; index < requestedStages.size(); index++) {
            SaveTemplateSessionFlowRequest.Stage requested = requestedStages.get(index);
            TemplateStage stage;
            if (requested.id() == null) {
                stage = new TemplateStage();
                stage.setTemplate(template);
                stage.setStageKey(requested.stageKey().trim());
            } else {
                stage = existingById.get(requested.id());
            }

            stage.setName(requested.name().trim());
            stage.setSortOrder(index);
            stage.setDurationSeconds(requested.durationSeconds());
            TemplateStage saved = templateStageRepository.save(stage);

            List<TemplateStageRole> priorLinks = templateStageRoleRepository.findByTemplateStageId(saved.getId());
            if (!priorLinks.isEmpty()) {
                templateStageRoleRepository.deleteAll(priorLinks);
                templateStageRoleRepository.flush();
            }

            List<TemplateStageRole> nextLinks = new ArrayList<>();
            for (UUID roleId : requested.templateRoleIds()) {
                TemplateStageRole link = new TemplateStageRole();
                link.setTemplateStage(saved);
                link.setTemplateRole(roleById.get(roleId));
                nextLinks.add(link);
            }
            templateStageRoleRepository.saveAll(nextLinks);

            responseStages.add(new TemplateSessionFlowResponse.Stage(
                    saved.getId(),
                    saved.getStageKey(),
                    saved.getName(),
                    saved.getSortOrder(),
                    saved.getDurationSeconds(),
                    List.copyOf(requested.templateRoleIds())));
        }
        templateStageRepository.flush();

        return new TemplateSessionFlowResponse(templateId, template.isSessionFlowEnabled(), responseStages);
    }

    private void validateRequest(
            List<SaveTemplateSessionFlowRequest.Stage> stages,
            Map<UUID, TemplateStage> existingById,
            Map<UUID, TemplateRole> roleById) {
        Set<UUID> seenIds = new HashSet<>();
        Set<String> seenKeys = new HashSet<>();

        for (SaveTemplateSessionFlowRequest.Stage requested : stages) {
            if (requested == null) {
                throw new IllegalArgumentException("Session Flow stages cannot contain null items");
            }
            String key = requested.stageKey() == null ? "" : requested.stageKey().trim();
            String name = requested.name() == null ? "" : requested.name().trim();
            if (key.isBlank()) throw new IllegalArgumentException("stageKey is required");
            if (name.isBlank()) throw new IllegalArgumentException("Stage name is required");
            if (!seenKeys.add(key)) throw new IllegalArgumentException("Duplicate Session Flow stage key " + key);
            if (requested.durationSeconds() != null && requested.durationSeconds() <= 0) {
                throw new IllegalArgumentException("Stage timer must be greater than zero seconds");
            }

            if (requested.id() != null) {
                if (!seenIds.add(requested.id())) {
                    throw new IllegalArgumentException("Duplicate Session Flow stage id " + requested.id());
                }
                TemplateStage existing = existingById.get(requested.id());
                if (existing == null) {
                    throw new IllegalArgumentException("No TemplateStage with id " + requested.id());
                }
                if (!existing.getStageKey().equals(key)) {
                    throw new IllegalArgumentException("A Session Flow stage key cannot be changed after creation");
                }
            }

            if (requested.templateRoleIds() == null || requested.templateRoleIds().isEmpty()) {
                throw new IllegalArgumentException("Each Session Flow stage must have at least one active role");
            }
            Set<UUID> uniqueRoleIds = new LinkedHashSet<>(requested.templateRoleIds());
            if (uniqueRoleIds.size() != requested.templateRoleIds().size()) {
                throw new IllegalArgumentException("A Session Flow stage cannot contain the same role twice");
            }
            for (UUID roleId : uniqueRoleIds) {
                if (!roleById.containsKey(roleId)) {
                    throw new IllegalArgumentException("Template role " + roleId + " does not belong to this template");
                }
            }
        }
    }

    private TemplateSessionFlowResponse toResponse(Template template, List<TemplateStage> stages) {
        List<TemplateSessionFlowResponse.Stage> responses = stages.stream()
                .map(stage -> new TemplateSessionFlowResponse.Stage(
                        stage.getId(),
                        stage.getStageKey(),
                        stage.getName(),
                        stage.getSortOrder(),
                        stage.getDurationSeconds(),
                        templateStageRoleRepository.findByTemplateStageId(stage.getId()).stream()
                                .map(link -> link.getTemplateRole().getId())
                                .toList()))
                .toList();
        return new TemplateSessionFlowResponse(template.getId(), template.isSessionFlowEnabled(), responses);
    }

    /** Built-ins are readable; custom definitions are visible only to their owner. */
    private Template requireVisible(String templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("No Template with id " + templateId));
        if (template.getCreatedBy() == null) return template;
        return CurrentUserContext.getIfPresent()
                .filter(user -> user.getId().equals(template.getCreatedBy().getId()))
                .map(user -> template)
                .orElseThrow(() -> new IllegalArgumentException("No Template with id " + templateId));
    }

    /** Built-ins are read-only; a custom definition is mutable only by its creator. */
    private Template requireOwnedCustomTemplate(String templateId) {
        User caller = CurrentUserContext.getIfPresent()
                .orElseThrow(() -> new AuthenticationRequiredException("Must be signed in to modify a custom template"));
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("No Template with id " + templateId));
        if (template.getCreatedBy() == null || !template.getCreatedBy().getId().equals(caller.getId())) {
            throw new IllegalArgumentException("No Template with id " + templateId);
        }
        return template;
    }
}
