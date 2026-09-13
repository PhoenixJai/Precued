package com.precued.controller;

import com.precued.controller.dto.JoinRoomRequest;
import com.precued.controller.dto.LiveKitTokenResponse;
import com.precued.controller.dto.RoomParticipantResponse;
import com.precued.security.PublicEndpointRateLimiter;
import com.precued.service.InviteJoinService;
import com.precued.service.LiveKitTokenService;
import com.precued.service.RoomParticipantService;
import jakarta.servlet.http.HttpServletRequest;
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

@RestController
@RequestMapping("/api/room-participants")
public class RoomParticipantController {

    private final RoomParticipantService roomParticipantService;
    private final InviteJoinService inviteJoinService;
    private final LiveKitTokenService liveKitTokenService;
    private final PublicEndpointRateLimiter rateLimiter;

    public RoomParticipantController(
            RoomParticipantService roomParticipantService,
            InviteJoinService inviteJoinService,
            LiveKitTokenService liveKitTokenService,
            PublicEndpointRateLimiter rateLimiter) {
        this.roomParticipantService = roomParticipantService;
        this.inviteJoinService = inviteJoinService;
        this.liveKitTokenService = liveKitTokenService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomParticipantResponse join(
            @Valid @RequestBody JoinRoomRequest request,
            HttpServletRequest httpRequest) {
        rateLimiter.checkRoomJoin(request.roomId(), httpRequest.getRemoteAddr());

        if (request.userId() == null) {
            return RoomParticipantResponse.from(
                    inviteJoinService.join(request.roomId(), request.inviteToken(), request.displayName()));
        }

        return RoomParticipantResponse.from(
                roomParticipantService.join(request.roomId(), request.userId(), request.displayName()));
    }

    @GetMapping("/{id}/livekit-token")
    public LiveKitTokenResponse livekitToken(@PathVariable UUID id) {
        return liveKitTokenService.issueToken(id);
    }
}
