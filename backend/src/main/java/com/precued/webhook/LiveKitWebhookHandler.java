package com.precued.webhook;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.entity.ShareTrack;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareTrackRepository;
import livekit.LivekitModels;
import livekit.LivekitWebhook.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Dispatches the three LiveKit webhook events the trigger table (see
 * "VisibilityEngine — Interface Spec" in Precued_DataModel.md) reacts to.
 * Everything else LiveKit sends (room_started, egress_*, etc.) is ignored
 * here — this handler's only job is the VisibilityEngine triggers.
 */
@Component
public class LiveKitWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookHandler.class);

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
        String trackSid = event.getTrack().getSid();

        // Already recorded (e.g. a redelivered webhook for the same track) —
        // don't try to insert a second row over the unique track_sid, just
        // recompute using the Share it's already attached to.
        Optional<ShareTrack> existing = shareTrackRepository.findByLivekitTrackSid(trackSid);
        if (existing.isPresent()) {
            engine.recomputeAndPushForShare(existing.get().getShare().getId());
            return;
        }

        ShareTrack.Kind kind = mapTrackKind(event.getTrack().getType());
        if (kind == null) {
            log.warn("track_published for {} has unsupported track type {}, no-op", trackSid, event.getTrack().getType());
            return;
        }

        UUID roomId = resolveRoomId(event.getRoom().getName());
        RoomParticipant publisher = resolveParticipant(roomId, event.getParticipant().getIdentity());

        // LiveKit queues webhook events per resource (track, participant, room,
        // ...) specifically so one resource's events never block another's —
        // there is no cross-resource ordering guarantee. A track_published for
        // this participant's track can arrive after we've already processed
        // their participant_left and set leftAt. Treat that as stale, not as
        // a valid publish.
        if (publisher.getLeftAt() != null) {
            log.warn(
                    "track_published for {} arrived after publisher {} had already left (leftAt set), no-op",
                    trackSid, publisher.getId());
            return;
        }
        UUID publisherId = publisher.getId();

        // The publisher's client tags the track's `name` as "<shareId>:<label>"
        // at publish time (client-side contract — see ShareLifecycleService).
        // This replaces counting the publisher's active Shares: a publisher
        // can legitimately have more than one active Share at once (trigger
        // table's "every active Share that participant publishes"), so
        // "exactly one active Share" is not a valid way to identify which
        // Share a track belongs to. The label half of the tag is redundant
        // with Share.label already stored at creation and isn't needed here.
        UUID shareId = parseShareIdTag(event.getTrack().getName());
        if (shareId == null) {
            log.warn("track_published for {} has an unparseable name tag '{}', no-op",
                    trackSid, event.getTrack().getName());
            return;
        }

        Optional<Share> taggedShare = shareRepository.findById(shareId);
        if (taggedShare.isEmpty() || taggedShare.get().getStatus() != Share.Status.ACTIVE) {
            log.warn("track_published for {} tagged Share {} that is missing or not active, no-op",
                    trackSid, shareId);
            return;
        }

        Share share = taggedShare.get();
        if (!share.getPublisher().getId().equals(publisherId)) {
            // Defense against a stale or forged tag: never trust it blindly.
            log.warn(
                    "track_published for {} tagged Share {} whose publisher does not match publishing identity"
                            + " '{}' (participant {}), no-op",
                    trackSid, shareId, event.getParticipant().getIdentity(), publisherId);
            return;
        }

        ShareTrack track = new ShareTrack();
        track.setShare(share);
        track.setLivekitTrackSid(trackSid);
        track.setKind(kind);
        track.setPublishedAt(Instant.now());
        shareTrackRepository.save(track);

        engine.recomputeAndPushForShare(share.getId());
    }

    private static ShareTrack.Kind mapTrackKind(LivekitModels.TrackType type) {
        return switch (type) {
            case AUDIO -> ShareTrack.Kind.AUDIO;
            case VIDEO -> ShareTrack.Kind.VIDEO;
            default -> null;
        };
    }

    /** Parses the "<shareId>:<label>" track-name tag; null if missing or malformed. */
    private static UUID parseShareIdTag(String trackName) {
        if (trackName == null) {
            return null;
        }
        int delimiterIndex = trackName.indexOf(':');
        if (delimiterIndex < 0) {
            return null;
        }
        try {
            return UUID.fromString(trackName.substring(0, delimiterIndex));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID resolveRoomId(String livekitRoomName) {
        return roomRepository.findByLivekitRoomName(livekitRoomName)
                .map(Room::getId)
                .orElseThrow(() -> new IllegalStateException("Unknown LiveKit room: " + livekitRoomName));
    }

    private UUID resolveParticipantId(UUID roomId, String livekitIdentity) {
        return resolveParticipant(roomId, livekitIdentity).getId();
    }

    private RoomParticipant resolveParticipant(UUID roomId, String livekitIdentity) {
        return roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, livekitIdentity)
                .orElseThrow(() -> new IllegalStateException("Unknown participant: " + livekitIdentity));
    }
}
