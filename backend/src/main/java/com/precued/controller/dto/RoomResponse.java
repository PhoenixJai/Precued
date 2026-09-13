package com.precued.controller.dto;

import com.precued.entity.Room;

import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String templateId,
        String templateName,
        UUID createdByUserId,
        String livekitRoomName,
        Room.Status status,
        Room.HostDisconnectPolicy hostDisconnectPolicy,
        Instant createdAt) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getTemplate().getId(),
                room.getTemplate().getName(),
                room.getCreatedBy().getId(),
                room.getLivekitRoomName(),
                room.getStatus(),
                room.getHostDisconnectPolicy(),
                room.getCreatedAt());
    }
}
