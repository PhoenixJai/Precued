package com.precued.controller;

import com.precued.controller.dto.CreateRoomRequest;
import com.precued.controller.dto.RoomResponse;
import com.precued.service.RoomService;
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
 * TEMPORARY: no auth/session enforcement yet. createdByUserId is accepted
 * directly from the request body and trusted as-is. This will be replaced
 * once auth exists — do not treat this as the final contract for who is
 * allowed to create a Room.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
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
}
