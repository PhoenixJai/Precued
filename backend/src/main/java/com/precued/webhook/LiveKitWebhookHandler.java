package com.precued.webhook;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareTrackRepository;
import livekit.LivekitWebhook.WebhookEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Dispatches the three LiveKit webhook events the trigger table (see
 * "VisibilityEngine — Interface Spec" in Precued_DataModel.md) reacts to.
 * Everything else LiveKit sends (room_started, egress_*, etc.) is ignored
 * here — this handler's only job is the VisibilityEngine triggers.
 */
@Component
public class LiveKitWebhookHandler {

    private static final String PARTICIPANT_JOINED = "participant_joined";
    private static final String PARTICIPANT_LEFT = "participant_left";
    private static final String TRACK_PUBLISHED = "track_published";

    private final VisibilityEngine engine;
    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final ShareRepository shareRepository;
    private final ShareTrackRepository shareTrackRepository;

    public LiveKitWebhookHandler(
            VisibilityEngine engine,
            RoomRepository roomRepository,
            RoomParticipantRepository roomParticipantRepository,
            ShareRepository shareRepository,
            ShareTrackRepository shareTrackRepository) {
        this.engine = engine;
        this.roomRepository = roomRepository;
        this.roomParticipantRepository = roomParticipantRepository;
        this.shareRepository = shareRepository;
        this.shareTrackRepository = shareTrackRepository;
    }

    public void handle(WebhookEvent event) {
        switch (event.getEvent()) {
            case PARTICIPANT_JOINED -> onParticipantJoined(event);
            case PARTICIPANT_LEFT -> onParticipantLeft(event);
            case TRACK_PUBLISHED -> onTrackPublished(event);
            default -> { /* not one of the VisibilityEngine triggers */ }
        }
    }

    private void onParticipantJoined(WebhookEvent event) {
        UUID roomId = resolveRoomId(event.getRoom().getName());
        UUID participantId = resolveParticipantId(roomId, event.getParticipant().getIdentity());

        // Every active Share in the room, for that one participant (trigger table).
        engine.recomputeAndPushForRoom(roomId);

        // Critical: if the (re)connecting participant is themselves a
        // publisher, a reliable data message sent while they were briefly
        // disconnected is simply gone — LiveKit does not buffer/retry it.
        // Re-push explicitly rather than assume the recompute above landed.
        shareRepository.findByPublisherIdAndStatus(participantId, Share.Status.ACTIVE)
                .forEach(share -> engine.recomputeAndPushForShare(share.getId()));
    }

    private void onParticipantLeft(WebhookEvent event) {
        UUID roomId = resolveRoomId(event.getRoom().getName());
        engine.recomputeAndPushForRoom(roomId);
    }

    private void onTrackPublished(WebhookEvent event) {
        shareTrackRepository.findByLivekitTrackSid(event.getTrack().getSid())
                .ifPresent(track -> engine.recomputeAndPushForShare(track.getShare().getId()));
    }

    private UUID resolveRoomId(String livekitRoomName) {
        return roomRepository.findByLivekitRoomName(livekitRoomName)
                .map(Room::getId)
                .orElseThrow(() -> new IllegalStateException("Unknown LiveKit room: " + livekitRoomName));
    }

    private UUID resolveParticipantId(UUID roomId, String livekitIdentity) {
        return roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, livekitIdentity)
                .map(RoomParticipant::getId)
                .orElseThrow(() -> new IllegalStateException("Unknown participant: " + livekitIdentity));
    }
}
