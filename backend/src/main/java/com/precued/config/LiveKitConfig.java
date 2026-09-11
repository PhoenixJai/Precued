package com.precued.config;

import io.livekit.server.RoomServiceClient;
import io.livekit.server.WebhookReceiver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiveKitConfig {

    /**
     * precued.livekit.host is one LiveKit project URL in the wss:// form
     * copied straight from the LiveKit Cloud dashboard — that's the only
     * value an operator should ever have to set. The frontend needs it
     * verbatim (see LiveKitTokenService — the client SDK's serverUrl really
     * does require ws(s)://). RoomServiceClient is different: it's a plain
     * Retrofit/OkHttp REST client under the hood, and Retrofit's
     * baseUrl(...) rejects anything that isn't http(s):// outright (throws
     * at bean creation, i.e. app boot fails) — confirmed against the
     * 0.9.1 bytecode, not assumed. LiveKit Cloud serves the REST/Twirp
     * server API on that same host, just over https instead of wss (this
     * is LiveKit's own documented split, not Precued-specific), so this is
     * a scheme swap, not a second piece of information — never ask an
     * operator to independently configure two URLs for one project.
     */
    @Bean
    public RoomServiceClient roomServiceClient(
            @Value("${precued.livekit.host}") String host,
            @Value("${precued.livekit.api-key}") String apiKey,
            @Value("${precued.livekit.api-secret}") String apiSecret) {
        return RoomServiceClient.createClient(toRestUrl(host), apiKey, apiSecret);
    }

    static String toRestUrl(String host) {
        if (host.startsWith("wss://")) {
            return "https://" + host.substring("wss://".length());
        }
        if (host.startsWith("ws://")) {
            return "http://" + host.substring("ws://".length());
        }
        return host; // already http(s):// (e.g. a self-hosted dev LiveKit server)
    }

    @Bean
    public WebhookReceiver liveKitWebhookReceiver(
            @Value("${precued.livekit.api-key}") String apiKey,
            @Value("${precued.livekit.api-secret}") String apiSecret) {
        return new WebhookReceiver(apiKey, apiSecret);
    }
}
