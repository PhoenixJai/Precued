package com.precued.controller;

import com.precued.controller.dto.MagicLinkRequest;
import com.precued.controller.dto.MagicLinkResponse;
import com.precued.controller.dto.SessionResponse;
import com.precued.controller.dto.VerifyTokenRequest;
import com.precued.entity.AuthSession;
import com.precued.entity.MagicLinkToken;
import com.precued.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMPORARY: no real email delivery yet — /magic-link returns the token
 * directly (see MagicLinkResponse). No middleware consumes the session
 * token returned by /verify yet either; wiring host-only endpoints to
 * require a valid session is separate scope.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/magic-link")
    @ResponseStatus(HttpStatus.CREATED)
    public MagicLinkResponse requestMagicLink(@Valid @RequestBody MagicLinkRequest request) {
        MagicLinkToken magicLink = authService.generateMagicLink(request.email());
        return new MagicLinkResponse(magicLink.getToken(), magicLink.getExpiresAt());
    }

    @PostMapping("/verify")
    public SessionResponse verify(@Valid @RequestBody VerifyTokenRequest request) {
        AuthSession session = authService.verifyMagicLink(request.token());
        return new SessionResponse(
                session.getToken(),
                session.getUser().getId(),
                session.getUser().getEmail(),
                session.getExpiresAt());
    }
}
