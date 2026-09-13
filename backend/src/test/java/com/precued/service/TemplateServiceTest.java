package com.precued.service;

import com.precued.entity.Template;
import com.precued.entity.TemplateRole;
import com.precued.entity.User;
import com.precued.repository.TemplateRepository;
import com.precued.repository.TemplateRoleRepository;
import com.precued.security.AuthenticationRequiredException;
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

/**
 * Precued_Issues_Update_3.md, M-Templates: templates owned by User
 * (created_by_user_id), private-by-default — a built-in seeded template
 * (createdBy null) stays public and read-only; a custom one is visible
 * and mutable only by its creator. Covers the custom role builder's
 * persistence layer: create a template, add/list/remove its roles.
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateRoleRepository templateRoleRepository;

    private TemplateService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TemplateService(templateRepository, templateRoleRepository);

        lenient().when(templateRepository.save(any(Template.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(templateRoleRepository.save(any(TemplateRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void createCustomTemplate_setsCreatedByFromCurrentUserContext_notATrustedParameter() {
        CurrentUserContext.set(user(ownerId));

        Template template = service.createCustomTemplate("My Custom Format");

        assertThat(template.getName()).isEqualTo("My Custom Format");
        assertThat(template.getCreatedBy().getId()).isEqualTo(ownerId);
        assertThat(template.getId()).isNotBlank();
        assertThat(template.getCreatedAt()).isNotNull();
    }

    @Test
    void createCustomTemplate_noAuthenticatedUser_throwsAuthenticationRequired() {
        assertThatThrownBy(() -> service.createCustomTemplate("My Custom Format"))
                .isInstanceOf(AuthenticationRequiredException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    void addRole_ownerAddsARole_persistsWithAppendedSortOrder() {
        CurrentUserContext.set(user(ownerId));
        Template template = customTemplate(ownerId);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(template.getId()))
                .thenReturn(List.of(templateRole(template, "judge", false, 0)));

        TemplateRole created = service.addRole(template.getId(), "jury", "Jury", false, false, null);

        assertThat(created.getRoleKey()).isEqualTo("jury");
        assertThat(created.getName()).isEqualTo("Jury");
        assertThat(created.getSortOrder()).isEqualTo(1);
        assertThat(created.getTemplate()).isEqualTo(template);
    }

    @Test
    void addRole_someoneElsesTemplate_rejectedAsNotFound_neverConfirmsItExists() {
        CurrentUserContext.set(user(otherUserId));
        Template template = customTemplate(ownerId);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.addRole(template.getId(), "jury", "Jury", false, false, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(templateRoleRepository, never()).save(any());
    }

    @Test
    void addRole_builtInTemplate_rejected_readOnlyRegardlessOfCaller() {
        CurrentUserContext.set(user(ownerId));
        Template builtIn = new Template();
        builtIn.setId("sales_call");
        builtIn.setName("Sales Call");
        builtIn.setCreatedAt(Instant.now());
        when(templateRepository.findById("sales_call")).thenReturn(Optional.of(builtIn));

        assertThatThrownBy(() -> service.addRole("sales_call", "extra", "Extra", false, false, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(templateRoleRepository, never()).save(any());
    }

    @Test
    void addRole_duplicateRoleKey_rejected() {
        CurrentUserContext.set(user(ownerId));
        Template template = customTemplate(ownerId);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(template.getId()))
                .thenReturn(List.of(templateRole(template, "judge", false, 0)));

        assertThatThrownBy(() -> service.addRole(template.getId(), "judge", "Second Judge", false, false, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("judge");
        verify(templateRoleRepository, never()).save(any());
    }

    @Test
    void addRole_secondHostRole_rejected_atMostOneAllowed() {
        CurrentUserContext.set(user(ownerId));
        Template template = customTemplate(ownerId);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(template.getId()))
                .thenReturn(List.of(templateRole(template, "judge", true, 0)));

        assertThatThrownBy(() -> service.addRole(template.getId(), "bailiff", "Bailiff", true, false, null))
                .isInstanceOf(IllegalStateException.class);
        verify(templateRoleRepository, never()).save(any());
    }

    @Test
    void addRole_nonPositiveMaxMembers_rejected() {
        CurrentUserContext.set(user(ownerId));
        Template template = customTemplate(ownerId);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.addRole(template.getId(), "jury", "Jury", false, false, 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(templateRoleRepository, never()).save(any());
    }

    @Test
    void removeRole_owner_deletesIt() {
        CurrentUserContext.set(user(ownerId));
        Template template = customTemplate(ownerId);
        TemplateRole role = templateRole(template, "judge", false, 0);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));

        service.removeRole(template.getId(), role.getId());

        verify(templateRoleRepository).delete(role);
    }

    @Test
    void listRoles_builtInTemplate_visibleToAnyone_evenAnonymous() {
        Template builtIn = new Template();
        builtIn.setId("sales_call");
        builtIn.setName("Sales Call");
        builtIn.setCreatedAt(Instant.now());
        when(templateRepository.findById("sales_call")).thenReturn(Optional.of(builtIn));
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder("sales_call"))
                .thenReturn(List.of(templateRole(builtIn, "sales_rep", true, 0)));

        List<TemplateRole> roles = service.listRoles("sales_call");

        assertThat(roles).hasSize(1);
    }

    @Test
    void listRoles_someoneElsesPrivateTemplate_rejectedAsNotFound() {
        CurrentUserContext.set(user(otherUserId));
        Template template = customTemplate(ownerId);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.listRoles(template.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listMine_returnsOnlyTheCallersOwnCustomTemplates() {
        CurrentUserContext.set(user(ownerId));
        Template mine = customTemplate(ownerId);
        when(templateRepository.findByCreatedByIdOrderByCreatedAtDesc(ownerId)).thenReturn(List.of(mine));

        List<Template> result = service.listMine();

        assertThat(result).containsExactly(mine);
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
        template.setName("Custom Mock Trial");
        template.setCreatedBy(user(ownerId));
        template.setCreatedAt(Instant.now());
        return template;
    }

    private TemplateRole templateRole(Template template, String roleKey, boolean isHostRole, int sortOrder) {
        TemplateRole role = new TemplateRole();
        role.setId(UUID.randomUUID());
        role.setTemplate(template);
        role.setRoleKey(roleKey);
        role.setName(roleKey);
        role.setHostRole(isHostRole);
        role.setSortOrder(sortOrder);
        return role;
    }
}
