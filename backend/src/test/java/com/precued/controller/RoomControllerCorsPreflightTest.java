package com.precued.controller;

import com.precued.repository.AuthSessionRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.service.RoomParticipantService;
import com.precued.service.RoomService;
import com.precued.service.ShareLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.precued.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end regression coverage for the exact browser-reported failure: a
 * preflight OPTIONS request to /api/rooms — a path AuthSessionInterceptor
 * requires a token on for the real POST — getting rejected with 401 before
 * CORS headers were ever attached. WebMvcConfigCorsTest alone didn't catch
 * this because it targets TemplateController, whose paths are excluded from
 * both auth interceptors; this targets RoomController, which is not.
 */
@WebMvcTest(RoomController.class)
// Real SecurityConfig, not disabled — this slice now also genuinely
// exercises the M-Auth OPTIONS-permitAll rule, the same PR #83 concern one
// layer down (@WebMvcTest doesn't pick up plain @Configuration beans like
// SecurityConfig on its own).
@Import(SecurityConfig.class)
@TestPropertySource(properties = "precued.web.allowed-origin=https://gregarious-wonder-production-f37b.up.railway.app")
class RoomControllerCorsPreflightTest {

    private static final String ALLOWED_ORIGIN = "https://gregarious-wonder-production-f37b.up.railway.app";

    @Autowired private MockMvc mockMvc;
    @MockBean private RoomService roomService;
    @MockBean private RoomParticipantService roomParticipantService;
    @MockBean private ShareLifecycleService shareLifecycleService;
    @MockBean private RoomParticipantRepository roomParticipantRepository;
    @MockBean private AuthSessionRepository authSessionRepository;

    @Test
    void preflight_toRooms_succeedsWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/rooms")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }
}
