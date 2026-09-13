package com.precued.config;

import com.precued.security.AuthSessionInterceptor;
import com.precued.security.ParticipantSessionInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ParticipantSessionInterceptor participantSessionInterceptor;
    private final AuthSessionInterceptor authSessionInterceptor;
    private final String allowedOrigin;

    public WebMvcConfig(
            ParticipantSessionInterceptor participantSessionInterceptor,
            AuthSessionInterceptor authSessionInterceptor,
            @Value("${precued.web.allowed-origin}") String allowedOrigin) {
        this.participantSessionInterceptor = participantSessionInterceptor;
        this.authSessionInterceptor = authSessionInterceptor;
        this.allowedOrigin = allowedOrigin;
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

        // /api/rooms, /api/room-participants: createdByUserId/userId in
        // their request bodies must come from a verified AuthSession, never
        // an unverified body claim — see AuthSessionInterceptor's own
        // Javadoc for how the two paths differ (required vs. optional).
        // /api/templates/**: M-Templates' custom template ownership — a
        // *different* kind of optional-unless-the-service-needs-it path,
        // same reasoning as room-participants: GET .../presets and reading
        // a built-in template need no token at all, so this only resolves
        // one *if* present; TemplateService is what actually requires one
        // for create/add-role/list-mine, exactly like RoomParticipantService
        // does for a non-guest join.
        registry.addInterceptor(authSessionInterceptor)
                .addPathPatterns("/api/rooms", "/api/room-participants", "/api/templates/**");
    }

    // Bearer tokens are sent in an Authorization header, never a cookie, so
    // this deliberately doesn't allowCredentials — nothing here needs it.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigin.isBlank()) return;
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST")
                .allowedHeaders("Authorization", "Content-Type");
    }
}
