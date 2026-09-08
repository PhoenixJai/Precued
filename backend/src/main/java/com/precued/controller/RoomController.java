package com.precued.controller;

import com.precued.controller.dto.ActiveShareResponse;
import com.precued.controller.dto.CreateRoomRequest;
import com.precued.controller.dto.RoomParticipantWithGrantsResponse;
import com.precued.controller.dto.RoomResponse;
import com.precued.controller.dto.RoomRoleResponse;
import com.precued.service.RoomParticipantService;
import com.precued.service.RoomService;
import com.precued.service.ShareLifecycleService;
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

/**
 * TEMPORARY: no auth/session enforcement yet. createdByUserId is accepted
 * directly from the request body and trusted as-is. This will be replaced
 * once auth exists — do not treat this as the final contract for who is
 * allowed to create a Room.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final RoomParticipantService roomParticipantService;
    private final ShareLifecycleService shareLifecycleService;

    public RoomController(
            RoomService roomService,
            RoomParticipantService roomParticipantService,
            ShareLifecycleService shareLifecycleService) {
        this.roomService = roomService;
        this.roomParticipantService = roomParticipantService;
        this.shareLifecycleService = shareLifecycleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@Valid @RequestBody CreateRoomRequest request) {
        return RoomResponse.from(
                roomService.create(request.templateId(), request.createdByUserId(), request.hostDisconnectPolicy()));
    }

    @GetMapping("/{id}")
    public RoomResponse get(@PathVariable UUID id) {
        return RoomResponse.from(roomService.get(id));
    }

    @GetMapping("/{roomId}/room-roles")
    public List<RoomRoleResponse> listRoomRoles(@PathVariable UUID roomId) {
        return roomService.listRoles(roomId).stream().map(RoomRoleResponse::from).toList();
    }

    @GetMapping("/{roomId}/room-participants")
    public List<RoomParticipantWithGrantsResponse> listRoomParticipants(@PathVariable UUID roomId) {
        return roomParticipantService.listWithGrants(roomId);
    }

    @GetMapping("/{roomId}/active-shares")
    public List<ActiveShareResponse> listActiveShares(@PathVariable UUID roomId) {
        return shareLifecycleService.listActive(roomId);
    }
}
