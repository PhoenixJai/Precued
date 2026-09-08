package com.precued.controller;

import com.precued.controller.dto.TemplatePresetResponse;
import com.precued.service.TemplatePresetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemplateController.class)
class TemplateControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private TemplatePresetService templatePresetService;

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
}
