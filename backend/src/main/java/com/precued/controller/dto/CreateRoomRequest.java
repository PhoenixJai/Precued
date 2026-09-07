package com.precued.controller.dto;

import com.precued.entity.Room;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * hostDisconnectPolicy is optional — defaults to Room.HostDisconnectPolicy.END_CALL
 * (Precued_DataModel.md's documented default) when omitted.
 */
public record CreateRoomRequest(
        @NotBlank String templateId,
        @NotNull UUID createdByUserId,
        Room.HostDisconnectPolicy hostDisconnectPolicy) {
}
