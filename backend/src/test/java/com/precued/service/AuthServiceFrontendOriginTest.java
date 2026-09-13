package com.precued.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Regression test for a live incident: FRONTEND_ORIGIN was confirmed set
 * correctly on Railway, but magic-link emails kept linking to
 * http://localhost:5173 anyway. Root cause was in application.yml, not
 * AuthService: precued.auth.magic-link.base-url was bound to a
 * *different* env var (APP_BASE_URL, never set in production, with a
 * hardcoded localhost fallback) — FRONTEND_ORIGIN was never read for this
 * property at all, despite already being the single source of truth for
 * "the frontend's origin" everywhere else (precued.web.allowed-origin's
 * CORS config).
 *
 * A plain Mockito AuthServiceTest can't catch this class of bug: it hands
 * the base URL to AuthService's constructor directly, never exercising
 * the actual YAML property binding that was wrong. This boots a real
 * Spring context instead, so @Value("${precued.auth.magic-link.base-url}")
 * resolves exactly as it would in production.
 */
@SpringBootTest(properties = "FRONTEND_ORIGIN=" + AuthServiceFrontendOriginTest.CONFIGURED_ORIGIN)
@ActiveProfiles("integrationtest")
class AuthServiceFrontendOriginTest {

    static final String CONFIGURED_ORIGIN = "https://precued-frontend.up.railway.app";

    @Autowired private AuthService authService;
    @MockBean private ResendEmailClient resendEmailClient;

    @Test
    void generateMagicLink_usesFrontendOriginEnvVar_notTheHardcodedLocalhostFallback() {
        authService.generateMagicLink("host@example.com");

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(resendEmailClient).send(any(), any(), any(), textCaptor.capture());

        assertThat(textCaptor.getValue())
                .contains(CONFIGURED_ORIGIN)
                .doesNotContain("localhost:5173");
    }
}
