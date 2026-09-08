package com.precued.controller.dto;

/**
 * Everything a client needs to connect to LiveKit as one RoomParticipant:
 * the signed JWT plus the server URL and identity/room it was scoped to
 * (so the frontend doesn't have to separately know or re-derive them).
 */
public record LiveKitTokenResponse(String token, String livekitUrl, String roomName, String identity) {
}
