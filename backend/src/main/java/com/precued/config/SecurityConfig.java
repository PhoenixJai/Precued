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
 * Precued_Issues_Update_3.md, M-Auth item 2: "no valid session -> 401" as a
 * framework-level guarantee on every write endpoint, rather than something
 * that only holds if every controller happens to be covered by an
 * interceptor someone remembered to register. See
 * RoomParticipantAuthenticationFilter's Javadoc for how this coexists with
 * (not replaces) ParticipantSessionInterceptor's ownership checks and
 * AuthSessionInterceptor's separate User/AuthSession token space, both
 * unchanged.
 *
 * The permitAll list below is ported verbatim from WebMvcConfig's existing
 * addInterceptors().excludePathPatterns — same 5 patterns, same reasons
 * documented there. This list must never drift from that one: anything
 * missed here silently 401s a path that has always been public; anything
 * added here that isn't also excluded there would be redundant, not wrong,
 * but the two should be read side by side, not maintained independently.
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
                // WebMvcConfig.addCorsMappings already owns CORS entirely at
                // the MVC layer (PR #82) — deliberately not touching it here.
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // A CORS preflight carries no Authorization header by
                        // design — this is the exact PR #83 incident
                        // (interceptor-level 401 on OPTIONS) one layer down;
                        // this rule exists specifically to not repeat it.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
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
