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
 * AuthService#generateMagicLink emails the link (Resend's SMTP relay, see
 * .env.example). The token itself is never returned from /magic-link: the
 * caller only proves they received it by successfully calling /verify with
 * it, which is the whole point of a magic link (whoever submitted the email
 * is not necessarily its owner).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/magic-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MagicLinkResponse requestMagicLink(@Valid @RequestBody MagicLinkRequest request) {
        MagicLinkToken magicLink = authService.generateMagicLink(request.email());
        return new MagicLinkResponse(magicLink.getExpiresAt());
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
