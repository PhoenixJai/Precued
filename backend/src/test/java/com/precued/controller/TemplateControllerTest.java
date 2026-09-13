package com.precued.controller;

import com.precued.controller.dto.TemplatePresetResponse;
import com.precued.entity.AuthSession;
import com.precued.entity.Template;
import com.precued.entity.TemplateRole;
import com.precued.entity.User;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.security.AuthenticationRequiredException;
import com.precued.service.TemplatePresetService;
import com.precued.service.TemplateService;
import org.junit.jupiter.api.Test;
import com.precued.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemplateController.class)
// Real SecurityConfig, not disabled — @WebMvcTest doesn't pick up plain
// @Configuration beans like SecurityConfig on its own.
@Import(SecurityConfig.class)
class TemplateControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private TemplatePresetService templatePresetService;
    @MockBean private TemplateService templateService;
    // WebMvcConfig (which @WebMvcTest picks up as a WebMvcConfigurer) wires
    // both interceptor beans regardless of whether this controller's own
    // paths need them, so both repositories must be mockable for the
    // context to load. AuthSessionInterceptor now also runs on
    // /api/templates/** (M-Templates) — see WebMvcConfig's registration.
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    @MockBean private AuthSessionRepository authSessionRepository;

    private static final String TEST_AUTH_TOKEN = "test-auth-session-token";

    /** Stubs a valid AuthSession — required by AuthSessionInterceptor whenever a token is sent. */
    private User stubAuthenticatedUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(authSessionRepository.findByToken(TEST_AUTH_TOKEN)).thenReturn(Optional.of(session));
        return user;
    }

    @Test
    void listPresets_existingTemplate_returns200WithPresetsAndRoleKeys() throws Exception {
        UUID presetId = UUID.randomUUID();
        TemplatePresetResponse response =
                new TemplatePresetResponse(presetId, "mock_trial", "Judge + Jury Only", 1, List.of("judge", "jury"));
        when(templatePresetService.listForTemplate("mock_trial")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/templates/{templateId}/presets", "mock_trial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(presetId.toString()))
                .andExpect(jsonPath("$[0].templateId").value("mock_trial"))
                .andExpect(jsonPath("$[0].name").value("Judge + Jury Only"))
                .andExpect(jsonPath("$[0].sortOrder").value(1))
                .andExpect(jsonPath("$[0].roleKeys[0]").value("judge"))
                .andExpect(jsonPath("$[0].roleKeys[1]").value("jury"));
    }

    @Test
    void listPresets_unknownTemplate_returns404() throws Exception {
        when(templatePresetService.listForTemplate("nope"))
                .thenThrow(new IllegalArgumentException("No Template with id nope"));

        mockMvc.perform(get("/api/templates/{templateId}/presets", "nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void create_validRequest_returns201() throws Exception {
        stubAuthenticatedUser();
        Template template = customTemplate();
        when(templateService.createCustomTemplate("My Custom Format")).thenReturn(template);

        mockMvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_AUTH_TOKEN)
                        .content("{\"name\":\"My Custom Format\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(template.getId()))
                .andExpect(jsonPath("$.name").value("My Custom Format"))
                .andExpect(jsonPath("$.isCustom").value(true));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_AUTH_TOKEN)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(templateService, never()).createCustomTemplate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void create_noAuthenticatedUser_returns401() throws Exception {
        when(templateService.createCustomTemplate("My Custom Format"))
                .thenThrow(new AuthenticationRequiredException("Must be signed in to create a custom template"));

        mockMvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Custom Format\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listMine_returns200WithTheCallersTemplates() throws Exception {
        stubAuthenticatedUser();
        Template template = customTemplate();
        when(templateService.listMine()).thenReturn(List.of(template));

        mockMvc.perform(get("/api/templates/mine").header("Authorization", "Bearer " + TEST_AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(template.getId()))
                .andExpect(jsonPath("$[0].isCustom").value(true));
    }

    @Test
    void get_existingTemplate_returns200() throws Exception {
        Template template = customTemplate();
        when(templateService.get(template.getId())).thenReturn(template);

        mockMvc.perform(get("/api/templates/{templateId}", template.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(template.getId()));
    }

    @Test
    void get_privateTemplateNotVisible_returns404() throws Exception {
        when(templateService.get("someone-elses"))
                .thenThrow(new IllegalArgumentException("No Template with id someone-elses"));

        mockMvc.perform(get("/api/templates/{templateId}", "someone-elses"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listRoles_existingTemplate_returns200() throws Exception {
        Template template = customTemplate();
        TemplateRole role = templateRole(template, "judge", "Judge", true);
        when(templateService.listRoles(template.getId())).thenReturn(List.of(role));

        mockMvc.perform(get("/api/templates/{templateId}/roles", template.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(role.getId().toString()))
                .andExpect(jsonPath("$[0].templateId").value(template.getId()))
                .andExpect(jsonPath("$[0].roleKey").value("judge"))
                .andExpect(jsonPath("$[0].isHostRole").value(true));
    }

    @Test
    void addRole_validRequest_returns201() throws Exception {
        stubAuthenticatedUser();
        Template template = customTemplate();
        TemplateRole role = templateRole(template, "jury", "Jury", false);
        when(templateService.addRole(eq(template.getId()), eq("jury"), eq("Jury"), eq(false), eq(false), eq(null)))
                .thenReturn(role);

        mockMvc.perform(post("/api/templates/{templateId}/roles", template.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_AUTH_TOKEN)
                        .content("{\"roleKey\":\"jury\",\"name\":\"Jury\",\"isHostRole\":false,\"isGuestRole\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleKey").value("jury"))
                .andExpect(jsonPath("$.name").value("Jury"));
    }

    @Test
    void addRole_blankRoleKey_returns400() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(post("/api/templates/{templateId}/roles", "some-template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_AUTH_TOKEN)
                        .content("{\"roleKey\":\"\",\"name\":\"Jury\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addRole_notTheOwner_returns404() throws Exception {
        stubAuthenticatedUser();
        when(templateService.addRole(eq("someone-elses"), eq("jury"), eq("Jury"), eq(false), eq(false), eq(null)))
                .thenThrow(new IllegalArgumentException("No Template with id someone-elses"));

        mockMvc.perform(post("/api/templates/{templateId}/roles", "someone-elses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TEST_AUTH_TOKEN)
                        .content("{\"roleKey\":\"jury\",\"name\":\"Jury\",\"isHostRole\":false,\"isGuestRole\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeRole_existingRole_returns204() throws Exception {
        stubAuthenticatedUser();
        UUID roleId = UUID.randomUUID();

        mockMvc.perform(delete("/api/templates/{templateId}/roles/{roleId}", "some-template", roleId)
                        .header("Authorization", "Bearer " + TEST_AUTH_TOKEN))
                .andExpect(status().isNoContent());

        verify(templateService).removeRole("some-template", roleId);
    }

    private Template customTemplate() {
        Template template = new Template();
        template.setId(UUID.randomUUID().toString());
        template.setName("My Custom Format");
        User createdBy = new User();
        createdBy.setId(UUID.randomUUID());
        template.setCreatedBy(createdBy);
        template.setCreatedAt(Instant.now());
        return template;
    }

    private TemplateRole templateRole(Template template, String roleKey, String name, boolean isHostRole) {
        TemplateRole role = new TemplateRole();
        role.setId(UUID.randomUUID());
        role.setTemplate(template);
        role.setRoleKey(roleKey);
        role.setName(name);
        role.setHostRole(isHostRole);
        return role;
    }
}
