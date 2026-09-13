package com.precued.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.security.RoomParticipantAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Framework-level session enforcement for protected API routes. Public
 * entry points must stay aligned with WebMvcConfig's interceptor exclusions;
 * a guest can resolve an opaque Invite token before a RoomParticipant exists,
 * while host invite management under /api/rooms/{roomId}/invites remains
 * participant-authenticated.
 */
@Configuration
public class SecurityConfig {

    private final RoomParticipantAuthenticationFilter roomParticipantAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            RoomParticipantAuthenticationFilter roomParticipantAuthenticationFilter, ObjectMapper objectMapper) {
        this.roomParticipantAuthenticationFilter = roomParticipantAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/invites/*").permitAll()
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/templates/**",
                                "/api/rooms",
                                "/api/room-participants",
                                "/api/rooms/*/room-roles")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(this::sendUnauthorized))
                .addFilterBefore(roomParticipantAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Auth & Account Overhaul: password hashing for Account Holder signup/login (AccountService). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Same RFC 7807 ProblemDetail shape BearerTokenSupport.reject already uses elsewhere in this app. */
    private void sendUnauthorized(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter()
                .write(objectMapper.writeValueAsString(
                        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Missing or invalid session")));
    }
}
