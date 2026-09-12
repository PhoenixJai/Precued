package com.precued.config;

import com.precued.controller.TemplateController;
import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.service.TemplatePresetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The frontend and backend are separate Railway services (different
 * origins) — every browser fetch to /api/** is cross-origin, so without
 * this config every request fails in the browser with no server-side error
 * to point at (curl doesn't enforce CORS, which is why this went unnoticed
 * until a real browser was involved). precued.web.allowed-origin is the one
 * origin allowed, read from FRONTEND_ORIGIN so it's never hardcoded — same
 * env-var-backed pattern as R2's account id/keys in StorageConfig.
 */
@WebMvcTest(TemplateController.class)
@TestPropertySource(properties = "precued.web.allowed-origin=https://gregarious-wonder-production-f37b.up.railway.app")
class WebMvcConfigCorsTest {

    private static final String ALLOWED_ORIGIN = "https://gregarious-wonder-production-f37b.up.railway.app";

    @Autowired private MockMvc mockMvc;
    @MockBean private TemplatePresetService templatePresetService;
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    @MockBean private AuthSessionRepository authSessionRepository;

    @Test
    void preflight_fromAllowedFrontendOrigin_returnsMatchingCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/templates/{templateId}/presets", "sales_call")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST"))
                .andExpect(header().string("Access-Control-Allow-Headers", "Authorization"));
    }

    @Test
    void preflight_fromUnrecognizedOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/templates/{templateId}/presets", "sales_call")
                        .header("Origin", "https://not-the-real-frontend.example.com")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isForbidden());
    }
}
