package com.precued.controller;

import com.precued.controller.dto.LogInRequest;
import com.precued.controller.dto.MagicLinkRequest;
import com.precued.controller.dto.MagicLinkResponse;
import com.precued.controller.dto.SessionResponse;
import com.precued.controller.dto.SignUpRequest;
import com.precued.controller.dto.VerifyTokenRequest;
import com.precued.entity.AuthSession;
import com.precued.entity.MagicLinkToken;
import com.precued.service.AccountService;
import com.precued.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two parallel auth paths under one URL namespace (Auth & Account
 * Overhaul, supersedes Issue 1's hybrid magic-link decision for hosts):
 * signup/login (AccountService) issue a persistent AuthSession for an
 * Account Holder; magic-link/verify (AuthService) stays as-is for now,
 * being scoped down to guest-room-join only in a later chunk. Both issue
 * the same AuthSession type and return the same SessionResponse shape.
 *
 * AuthService#generateMagicLink emails the link (Resend's HTTP API, see
 * .env.example). The token itself is never returned from /magic-link: the
 * caller only proves they received it by successfully calling /verify with
 * it, which is the whole point of a magic link (whoever submitted the email
 * is not necessarily its owner).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountService accountService;

    public AuthController(AuthService authService, AccountService accountService) {
        this.authService = authService;
        this.accountService = accountService;
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
        return toSessionResponse(session);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse signUp(@Valid @RequestBody SignUpRequest request) {
        AuthSession session = accountService.signUp(request.email(), request.password(), request.displayName());
        return toSessionResponse(session);
    }

    @PostMapping("/login")
    public SessionResponse logIn(@Valid @RequestBody LogInRequest request) {
        AuthSession session = accountService.logIn(request.email(), request.password());
        return toSessionResponse(session);
    }

    private SessionResponse toSessionResponse(AuthSession session) {
        return new SessionResponse(
                session.getToken(),
                session.getUser().getId(),
                session.getUser().getEmail(),
                session.getUser().getDisplayName(),
                session.getExpiresAt());
    }
}
