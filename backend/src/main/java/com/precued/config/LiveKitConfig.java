package com.precued.config;

import io.livekit.server.RoomServiceClient;
import io.livekit.server.WebhookReceiver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiveKitConfig {

    @Bean
    public RoomServiceClient roomServiceClient(
            @Value("${precued.livekit.host}") String host,
            @Value("${precued.livekit.api-key}") String apiKey,
            @Value("${precued.livekit.api-secret}") String apiSecret) {
        return RoomServiceClient.createClient(host, apiKey, apiSecret);
    }

    @Bean
    public WebhookReceiver liveKitWebhookReceiver(
            @Value("${precued.livekit.api-key}") String apiKey,
            @Value("${precued.livekit.api-secret}") String apiSecret) {
        return new WebhookReceiver(apiKey, apiSecret);
    }
}
