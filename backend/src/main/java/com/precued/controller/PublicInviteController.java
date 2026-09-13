package com.precued.controller;

import com.precued.controller.dto.InvitePreviewResponse;
import com.precued.service.InviteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invites")
public class PublicInviteController {

    private final InviteService inviteService;

    public PublicInviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping("/{token}")
    public InvitePreviewResponse resolve(@PathVariable String token) {
        return InvitePreviewResponse.from(inviteService.resolve(token));
    }
}
