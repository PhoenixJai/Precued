package com.precued.service;

import com.precued.controller.dto.LiveKitTokenResponse;
import com.precued.entity.RoomParticipant;
import com.precued.repository.RoomParticipantRepository;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Issues a LiveKit client access token for a RoomParticipant, scoped to
 * their livekit_identity and the room's livekit_room_name. This is a coarse,
 * room-level grant (join / publish / subscribe) — the same for every
 * participant regardless of RoomRole. Fine-grained, per-viewer track
 * visibility is a separate concern the VisibilityEngine enforces afterward,
 * client-side, via setTrackSubscriptionPermissions; it is not expressed in
 * this token.
 */
@Service
public class LiveKitTokenService {

    private final RoomParticipantRepository roomParticipantRepository;
    private final String apiKey;
    private final String apiSecret;
    private final String livekitUrl;

    public LiveKitTokenService(
            RoomParticipantRepository roomParticipantRepository,
            @Value("${precued.livekit.api-key}") String apiKey,
            @Value("${precued.livekit.api-secret}") String apiSecret,
            @Value("${precued.livekit.host}") String livekitUrl) {
        this.roomParticipantRepository = roomParticipantRepository;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.livekitUrl = livekitUrl;
    }

    public LiveKitTokenResponse issueToken(UUID roomParticipantId) {
        RoomParticipant participant = roomParticipantRepository.findById(roomParticipantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No RoomParticipant with id " + roomParticipantId));

        String roomName = participant.getRoom().getLivekitRoomName();
        String identity = participant.getLivekitIdentity();

        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity(identity);
        token.setName(participant.getDisplayName());
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(true),
                new CanSubscribe(true),
                new CanPublishData(true));

        return new LiveKitTokenResponse(token.toJwt(), livekitUrl, roomName, identity);
    }
}
