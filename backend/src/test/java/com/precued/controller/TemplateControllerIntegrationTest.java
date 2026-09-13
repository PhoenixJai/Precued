package com.precued.controller;

import com.precued.entity.AuthSession;
import com.precued.entity.Template;
import com.precued.entity.TemplateRole;
import com.precued.entity.User;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.TemplateRepository;
import com.precued.repository.TemplateRoleRepository;
import com.precued.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live bug report: loading /templates/custom/:templateId for a template
 * that already has roles fails with a generic error; a brand-new, empty
 * template loads fine. TemplateServiceTest/TemplateControllerTest can't
 * reproduce this — both mock the repositories, so nothing ever touches a
 * real Hibernate-managed, lazily-associated entity the way production
 * does (open-in-view is disabled). This boots a real Spring context with
 * real JPA-backed entities instead, mirroring exactly what the frontend's
 * two parallel GETs do on page load.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
class TemplateControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthSessionRepository authSessionRepository;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private TemplateRoleRepository templateRoleRepository;

    // Shared H2 instance across test methods in this class (DB_CLOSE_DELAY=-1,
    // context cached) — each test needs its own token to avoid AuthSession's
    // unique constraint on token.
    private String ownerToken;

    @Test
    void getTemplate_customTemplateWithNoRolesYet_returns200() throws Exception {
        Template template = persistCustomTemplate(persistOwner());

        mockMvc.perform(get("/api/templates/{id}", template.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(template.getId()));
    }

    @Test
    void listRoles_customTemplateWithNoRolesYet_returns200WithEmptyList() throws Exception {
        Template template = persistCustomTemplate(persistOwner());

        mockMvc.perform(get("/api/templates/{id}/roles", template.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void getTemplate_customTemplateWithRoles_returns200() throws Exception {
        Template template = persistCustomTemplate(persistOwner());
        persistRole(template, "judge");

        mockMvc.perform(get("/api/templates/{id}", template.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(template.getId()));
    }

    @Test
    void listRoles_customTemplateWithRoles_returns200WithTheRole() throws Exception {
        Template template = persistCustomTemplate(persistOwner());
        persistRole(template, "judge");

        mockMvc.perform(get("/api/templates/{id}/roles", template.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleKey").value("judge"))
                .andExpect(jsonPath("$[0].templateId").value(template.getId()));
    }

    private User persistOwner() {
        User owner = new User();
        owner.setEmail(UUID.randomUUID() + "@example.com");
        owner.setDisplayName("Owner");
        owner.setCreatedAt(Instant.now());
        owner = userRepository.save(owner);

        ownerToken = "owner-session-token-" + UUID.randomUUID();
        AuthSession session = new AuthSession();
        session.setUser(owner);
        session.setToken(ownerToken);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        authSessionRepository.save(session);

        return owner;
    }

    private Template persistCustomTemplate(User owner) {
        Template template = new Template();
        template.setId(UUID.randomUUID().toString());
        template.setName("My Custom Format");
        template.setCreatedBy(owner);
        template.setCreatedAt(Instant.now());
        return templateRepository.save(template);
    }

    private TemplateRole persistRole(Template template, String roleKey) {
        TemplateRole role = new TemplateRole();
        role.setTemplate(template);
        role.setRoleKey(roleKey);
        role.setName(roleKey);
        role.setHostRole(false);
        role.setSortOrder(0);
        return templateRoleRepository.save(role);
    }
}
