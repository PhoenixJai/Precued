package com.precued.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends email via Resend's HTTP API (https://api.resend.com/emails) over
 * port 443 — not Resend's SMTP relay. Railway blocks outbound port 587
 * (confirmed via a direct TCP connectivity test from the deployed
 * container: a raw connection to smtp.resend.com:587 hung the full
 * timeout with no response, the signature of an egress firewall silently
 * dropping packets), so SMTP-based delivery cannot work from this
 * environment regardless of how correctly it's configured.
 */
@Component
public class ResendEmailClient {

    private final RestClient restClient;

    public ResendEmailClient(
            RestClient.Builder restClientBuilder,
            @Value("${precued.auth.magic-link.resend-api-key:}") String apiKey) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public void send(String fromAddress, String toEmail, String subject, String text) {
        restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", fromAddress,
                        "to", List.of(toEmail),
                        "subject", subject,
                        "text", text))
                .retrieve()
                .toBodilessEntity();
    }
}
