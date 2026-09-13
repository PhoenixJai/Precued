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
                        // Room creation and participant creation are how a
                        // session begins; a RoomParticipant token cannot exist
                        // before these requests complete.
                        "/api/rooms",
                        "/api/room-participants",
                        // A guest must resolve their opaque Invite before a
                        // RoomParticipant/session token exists.
                        "/api/invites/*",
                        // Host bootstrap resolves the snapshotted host role
                        // before that host has a RoomParticipant session.
                        "/api/rooms/*/room-roles");

        // Account Holder identity is resolved independently from the
        // RoomParticipant token space. Room creation requires it; participant
        // creation uses it when a userId is claimed; custom Template ownership
        // uses it when the service requires an Account Holder.
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
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Authorization", "Content-Type");
    }
}
