package com.precued.config;

import io.livekit.server.RoomServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the LIVEKIT_HOST scheme fix: RoomServiceClient is a Retrofit/OkHttp
 * REST client under the hood, and Retrofit's baseUrl(...) rejects anything
 * that isn't http(s):// — passing the documented wss:// value straight
 * through used to fail at bean creation (app boot). LiveKit Cloud serves
 * the REST API on the same host over https, so this is a scheme swap, not
 * a second configuration value.
 */
class LiveKitConfigTest {

    @Test
    void toRestUrl_wssHost_becomesHttps() {
        assertThat(LiveKitConfig.toRestUrl("wss://my-project.livekit.cloud"))
                .isEqualTo("https://my-project.livekit.cloud");
    }

    @Test
    void toRestUrl_wsHost_becomesHttp() {
        assertThat(LiveKitConfig.toRestUrl("ws://localhost:7880"))
                .isEqualTo("http://localhost:7880");
    }

    @Test
    void toRestUrl_alreadyHttps_isUnchanged() {
        assertThat(LiveKitConfig.toRestUrl("https://my-project.livekit.cloud"))
                .isEqualTo("https://my-project.livekit.cloud");
    }

    @Test
    void toRestUrl_alreadyHttp_isUnchanged() {
        assertThat(LiveKitConfig.toRestUrl("http://localhost:7880"))
                .isEqualTo("http://localhost:7880");
    }

    /**
     * The actual regression: RoomServiceClient.createClient's underlying
     * Retrofit.Builder.baseUrl(...) throws IllegalArgumentException for a
     * non-http(s) scheme — that's an app-boot failure, not a runtime error,
     * once LIVEKIT_HOST is ever set to the documented wss:// value. No DB
     * is needed to prove this: this loads only LiveKitConfig, with a
     * real-shaped LiveKit Cloud URL, and asserts the bean is actually
     * constructed rather than throwing during context refresh.
     */
    @Test
    void contextLoads_withRealLiveKitCloudShapedWssHost() {
        new ApplicationContextRunner()
                .withUserConfiguration(LiveKitConfig.class)
                .withPropertyValues(
                        "precued.livekit.host=wss://my-project.livekit.cloud",
                        "precued.livekit.api-key=APIabc123",
                        "precued.livekit.api-secret=secretvaluesecretvaluesecretvalue")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RoomServiceClient.class);
                });
    }
}
