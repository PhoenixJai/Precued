package com.precued.controller.dto;

import com.precued.entity.Room;
import jakarta.validation.constraints.NotBlank;

/**
 * hostDisconnectPolicy is optional — defaults to Room.HostDisconnectPolicy.END_CALL
 * (Precued_DataModel.md's documented default) when omitted.
 *
 * No createdByUserId field: who's creating the room comes only from the
 * AuthSession bearer token (see AuthSessionInterceptor, RoomService#create)
 * — a body field here was the original vulnerability (any caller could
 * claim to be any existing User) and there's no legitimate reason to
 * accept it from the body at all, so it isn't just ignored, it's gone.
 */
public record CreateRoomRequest(
        @NotBlank String templateId,
        Room.HostDisconnectPolicy hostDisconnectPolicy) {
}
