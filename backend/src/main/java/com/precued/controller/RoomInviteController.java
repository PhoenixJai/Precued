package com.precued.controller;

import com.precued.controller.dto.CreateInviteRequest;
import com.precued.controller.dto.InviteResponse;
import com.precued.service.InviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/invites")
public class RoomInviteController {

    private final InviteService inviteService;

    public RoomInviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping
    public List<InviteResponse> list(@PathVariable UUID roomId) {
        return inviteService.listForRoom(roomId).stream().map(InviteResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse create(
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateInviteRequest request) {
        return InviteResponse.from(inviteService.create(
                roomId,
                request.roomRoleId(),
                request.mode(),
                request.inviteeEmail(),
                request.maxUses(),
                request.expiresAt()));
    }
}
