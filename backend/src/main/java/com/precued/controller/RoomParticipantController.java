package com.precued.controller;

import com.precued.controller.dto.JoinRoomRequest;
import com.precued.controller.dto.LiveKitTokenResponse;
import com.precued.controller.dto.RoomParticipantResponse;
import com.precued.service.LiveKitTokenService;
import com.precued.service.RoomParticipantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * TEMPORARY: no auth/session enforcement yet, and no invite-token
 * consumption (separate scope) — this just creates a RoomParticipant
 * directly from the request body's room_id/user_id. Will be replaced once
 * auth and invites exist.
 */
@RestController
@RequestMapping("/api/room-participants")
public class RoomParticipantController {

    private final RoomParticipantService roomParticipantService;
    private final LiveKitTokenService liveKitTokenService;

    public RoomParticipantController(
            RoomParticipantService roomParticipantService, LiveKitTokenService liveKitTokenService) {
        this.roomParticipantService = roomParticipantService;
        this.liveKitTokenService = liveKitTokenService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomParticipantResponse join(@Valid @RequestBody JoinRoomRequest request) {
        return RoomParticipantResponse.from(
                roomParticipantService.join(request.roomId(), request.userId(), request.displayName()));
    }

    @GetMapping("/{id}/livekit-token")
    public LiveKitTokenResponse livekitToken(@PathVariable UUID id) {
        return liveKitTokenService.issueToken(id);
    }
}
