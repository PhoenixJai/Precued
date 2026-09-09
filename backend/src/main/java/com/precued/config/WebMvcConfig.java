package com.precued.config;

import com.precued.security.ParticipantSessionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ParticipantSessionInterceptor participantSessionInterceptor;

    public WebMvcConfig(ParticipantSessionInterceptor participantSessionInterceptor) {
        this.participantSessionInterceptor = participantSessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(participantSessionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/templates/**",
                        // Room creation and joining are how a session begins —
                        // neither can require one. Exact paths only, so
                        // sub-resources under them (e.g. /api/rooms/{roomId}/...,
                        // /api/room-participants/{id}/livekit-token) stay covered.
                        "/api/rooms",
                        "/api/room-participants",
                        // Called before the host/guest has joined (to resolve
                        // which RoomRole to self-assign) — not sensitive enough
                        // to justify breaking that ordering.
                        "/api/rooms/*/room-roles");
    }
}
