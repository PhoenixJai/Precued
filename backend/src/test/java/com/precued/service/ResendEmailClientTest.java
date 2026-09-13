package com.precued.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Railway blocks outbound SMTP (confirmed via a direct TCP connectivity
 * test from the deployed container: a raw connection to smtp.resend.com:587
 * hung the full timeout with no response — the signature of an egress
 * firewall silently dropping packets, not a fast refusal) — this replaces
 * AuthService's prior JavaMailSender/SMTP path with Resend's HTTP API,
 * which goes out over 443. MockRestServiceServer verifies the actual
 * request this builds, without a real network call.
 */
class ResendEmailClientTest {

    @Test
    void send_postsToResendEmailsEndpointWithBearerAuthAndJsonBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailClient client = new ResendEmailClient(builder, "test-api-key");

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("Precued <noreply@example.com>"))
                .andExpect(jsonPath("$.to[0]").value("host@example.com"))
                .andExpect(jsonPath("$.subject").value("Sign in to Precued"))
                .andExpect(jsonPath("$.text", containsString("http://localhost:5173?token=abc")))
                .andRespond(withSuccess("{\"id\":\"abc123\"}", MediaType.APPLICATION_JSON));

        client.send(
                "Precued <noreply@example.com>",
                "host@example.com",
                "Sign in to Precued",
                "Click the link: http://localhost:5173?token=abc");

        server.verify();
    }
}
