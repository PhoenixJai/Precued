package com.precued.webhook;

import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook.WebhookEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal receiver: verify the LiveKit webhook signature, dispatch, 200. */
@RestController
@RequestMapping("/webhooks/livekit")
public class LiveKitWebhookController {

    private final WebhookReceiver webhookReceiver;
    private final LiveKitWebhookHandler handler;

    public LiveKitWebhookController(WebhookReceiver webhookReceiver, LiveKitWebhookHandler handler) {
        this.webhookReceiver = webhookReceiver;
        this.handler = handler;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String body, @RequestHeader("Authorization") String authHeader) {
        WebhookEvent event = webhookReceiver.receive(body, authHeader);
        handler.handle(event);
        return ResponseEntity.ok().build();
    }
}
