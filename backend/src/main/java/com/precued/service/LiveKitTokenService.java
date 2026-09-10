package com.precued.service;

import com.precued.controller.dto.LiveKitTokenResponse;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRepository;
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
 *
 * canPublishData is deliberately withheld: the only legitimate data message
 * on this room's data channel is the visibility-grants push in
 * {@link com.precued.engine.VisibilityEngineImpl}, which goes over
 * RoomServiceClient's server API, not a client token. A participant token
 * that could publish data could otherwise forge that message to any other
 * client (see VisibilityEngineImpl's Javadoc for how the receiving side
 * tells a genuine push apart from a forged one).
 */
@Service
public class LiveKitTokenService {

    private final RoomParticipantRepository roomParticipantRepository;
    private final RoomRepository roomRepository;
    private final String apiKey;
    private final String apiSecret;
    private final String livekitUrl;

    public LiveKitTokenService(
            RoomParticipantRepository roomParticipantRepository,
            RoomRepository roomRepository,
            @Value("${precued.livekit.api-key}") String apiKey,
            @Value("${precued.livekit.api-secret}") String apiSecret,
            @Value("${precued.livekit.host}") String livekitUrl) {
        this.roomParticipantRepository = roomParticipantRepository;
        this.roomRepository = roomRepository;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.livekitUrl = livekitUrl;
    }

    public LiveKitTokenResponse issueToken(UUID roomParticipantId) {
        RoomParticipant participant = roomParticipantRepository.findById(roomParticipantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No RoomParticipant with id " + roomParticipantId));

        // participant.getRoom() is a lazy proxy (its association is
        // FetchType.LAZY, open-in-view is disabled, and this method runs
        // outside any transaction): .getId() on it is always safe (Hibernate
        // resolves it from the FK column, no query needed), but reading any
        // other field throws LazyInitializationException once the repository
        // call above's own transaction has closed. Fetch the Room fresh
        // instead of reading fields off that proxy.
        Room room = roomRepository.findById(participant.getRoom().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "RoomParticipant " + roomParticipantId + " references a Room that no longer exists"));

        String roomName = room.getLivekitRoomName();
        String identity = participant.getLivekitIdentity();

        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity(identity);
        token.setName(participant.getDisplayName());
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(true),
                new CanSubscribe(true),
                new CanPublishData(false));

        return new LiveKitTokenResponse(token.toJwt(), livekitUrl, roomName, identity);
    }
}
