package com.precued.config;

import com.precued.security.AuthSessionInterceptor;
import com.precued.security.ParticipantSessionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ParticipantSessionInterceptor participantSessionInterceptor;
    private final AuthSessionInterceptor authSessionInterceptor;

    public WebMvcConfig(
            ParticipantSessionInterceptor participantSessionInterceptor,
            AuthSessionInterceptor authSessionInterceptor) {
        this.participantSessionInterceptor = participantSessionInterceptor;
        this.authSessionInterceptor = authSessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(participantSessionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/templates/**",
                        // Room creation and joining are how a session begins —
                        // neither can require a RoomParticipant session yet.
                        // Exact paths only, so sub-resources under them (e.g.
                        // /api/rooms/{roomId}/..., /api/room-participants/{id}/livekit-token)
                        // stay covered. AuthSessionInterceptor (below) covers
                        // these two instead, with User-identity semantics.
                        "/api/rooms",
                        "/api/room-participants",
                        // Called before the host/guest has joined (to resolve
                        // which RoomRole to self-assign) — not sensitive enough
                        // to justify breaking that ordering.
                        "/api/rooms/*/room-roles");

        // Exactly the two paths excluded above: createdByUserId/userId in
        // their request bodies must come from a verified AuthSession, never
        // an unverified body claim — see AuthSessionInterceptor's own
        // Javadoc for how the two paths differ (required vs. optional).
        registry.addInterceptor(authSessionInterceptor)
                .addPathPatterns("/api/rooms", "/api/room-participants");
    }
}
