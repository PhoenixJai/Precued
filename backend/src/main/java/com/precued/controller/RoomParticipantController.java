package com.precued.controller;

import com.precued.controller.dto.JoinRoomRequest;
import com.precued.controller.dto.RoomParticipantResponse;
import com.precued.service.RoomParticipantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    public RoomParticipantController(RoomParticipantService roomParticipantService) {
        this.roomParticipantService = roomParticipantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomParticipantResponse join(@Valid @RequestBody JoinRoomRequest request) {
        return RoomParticipantResponse.from(
                roomParticipantService.join(request.roomId(), request.userId(), request.displayName()));
    }
}
