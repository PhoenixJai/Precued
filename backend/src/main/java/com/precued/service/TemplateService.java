package com.precued.service;

import com.precued.entity.Template;
import com.precued.entity.TemplateRole;
import com.precued.entity.User;
import com.precued.repository.TemplateRepository;
import com.precued.repository.TemplateRoleRepository;
import com.precued.security.AuthenticationRequiredException;
import com.precued.security.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Precued_Issues_Update_3.md, M-Templates: templates owned by User
 * (createdBy), private-by-default. A built-in seeded template (createdBy
 * null) stays public and read-only — nothing here ever mutates one. A
 * custom template is visible to, and mutable only by, its creator; every
 * other caller (including anonymous) gets the same "not found" a genuinely
 * missing template would, so a private template's existence is never
 * confirmed to anyone but its owner.
 *
 * Visibility permission matrix (which roles can see a share) is explicitly
 * out of scope here — this only covers the template/role definitions
 * themselves, the "custom role builder" AC.
 */
@Service
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateRoleRepository templateRoleRepository;

    public TemplateService(TemplateRepository templateRepository, TemplateRoleRepository templateRoleRepository) {
        this.templateRepository = templateRepository;
        this.templateRoleRepository = templateRoleRepository;
    }

    @Transactional
    public Template createCustomTemplate(String name) {
        User owner = requireAuthenticatedUser("create a custom template");

        Template template = new Template();
        template.setId(UUID.randomUUID().toString());
        template.setName(name);
        template.setCreatedBy(owner);
        template.setCreatedAt(Instant.now());
        return templateRepository.save(template);
    }

    /** The caller's own custom templates only — never another user's, per private-by-default. */
    public List<Template> listMine() {
        User owner = requireAuthenticatedUser("list your custom templates");
        return templateRepository.findByCreatedByIdOrderByCreatedAtDesc(owner.getId());
    }

    public Template get(String templateId) {
        return requireVisible(templateId);
    }

    public List<TemplateRole> listRoles(String templateId) {
        requireVisible(templateId);
        return templateRoleRepository.findByTemplateIdOrderBySortOrder(templateId);
    }

    /**
     * Appends at the end (sortOrder = current count) — no reordering support
     * yet, a deliberately deferred follow-up. Rejects a second host role and
     * a duplicate roleKey: both are silently trusted, hand-seeded data for
     * the three built-in templates, but user-entered here, so this is the
     * one place enforcing the invariants RoomService#create and the
     * role-assignment screen already assume hold (exactly one host role
     * findable, roleKey unique enough to key a preset's roleKeys list by).
     */
    @Transactional
    public TemplateRole addRole(
            String templateId, String roleKey, String name, boolean isHostRole, boolean isGuestRole,
            Integer maxMembers) {
        Template template = requireOwnedCustomTemplate(templateId);
        if (maxMembers != null && maxMembers < 1) {
            throw new IllegalArgumentException("maxMembers must be at least 1 if provided");
        }

        List<TemplateRole> existing = templateRoleRepository.findByTemplateIdOrderBySortOrder(templateId);
        if (existing.stream().anyMatch(role -> role.getRoleKey().equals(roleKey))) {
            throw new IllegalStateException("Template already has a role with key " + roleKey);
        }
        if (isHostRole && existing.stream().anyMatch(TemplateRole::isHostRole)) {
            throw new IllegalStateException("Template already has a host role — only one is allowed");
        }

        TemplateRole role = new TemplateRole();
        role.setTemplate(template);
        role.setRoleKey(roleKey);
        role.setName(name);
        role.setHostRole(isHostRole);
        role.setGuestRole(isGuestRole);
        role.setMaxMembers(maxMembers);
        role.setSortOrder(existing.size());
        return templateRoleRepository.save(role);
    }

    @Transactional
    public void removeRole(String templateId, UUID roleId) {
        requireOwnedCustomTemplate(templateId);
        TemplateRole role = templateRoleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("No TemplateRole with id " + roleId));
        if (!role.getTemplate().getId().equals(templateId)) {
            throw new IllegalArgumentException("TemplateRole " + roleId + " does not belong to template " + templateId);
        }
        templateRoleRepository.delete(role);
    }

    /** Built-in (createdBy null) is visible to anyone; a custom template only to its creator. */
    private Template requireVisible(String templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("No Template with id " + templateId));
        if (template.getCreatedBy() == null) {
            return template;
        }
        return CurrentUserContext.getIfPresent()
                .filter(user -> user.getId().equals(template.getCreatedBy().getId()))
                .map(user -> template)
                .orElseThrow(() -> new IllegalArgumentException("No Template with id " + templateId));
    }

    /**
     * Same rejection whether templateId is a read-only built-in or a custom
     * template privately owned by someone else — never distinguishes the
     * two, so a stranger can't tell a private template id apart from one
     * that simply doesn't exist.
     */
    private Template requireOwnedCustomTemplate(String templateId) {
        User caller = requireAuthenticatedUser("modify a custom template");
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("No Template with id " + templateId));
        boolean ownedByCaller = template.getCreatedBy() != null
                && template.getCreatedBy().getId().equals(caller.getId());
        if (!ownedByCaller) {
            throw new IllegalArgumentException("No Template with id " + templateId);
        }
        return template;
    }

    private User requireAuthenticatedUser(String action) {
        return CurrentUserContext.getIfPresent()
                .orElseThrow(() -> new AuthenticationRequiredException("Must be signed in to " + action));
    }
}
