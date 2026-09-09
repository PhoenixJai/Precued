package com.precued.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.entity.ShareTrack;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareRoleGrantRepository;
import com.precued.repository.ShareTrackRepository;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Response;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Part A: {@link #computeGrantsForShare} is pure DB compute (unchanged from
 * the original implementation) and remains Share-scoped; the publisher-wide
 * union in {@link #computeGrantsForPublisher} is what is actually safe to
 * push. Part B: push via a LiveKit data message, RELIABLE mode, targeted at
 * the publisher only — see "VisibilityEngine — Interface Spec" / transport
 * decision in Precued_DataModel.md for why (the publisher is already
 * connected to LiveKit; a second websocket channel would just be a second
 * thing to fail).
 */
@Component
public class VisibilityEngineImpl implements VisibilityEngine {

    private static final Logger log = LoggerFactory.getLogger(VisibilityEngineImpl.class);

    /** Topic tag on the data message, so publisher clients can route it. */
    static final String GRANTS_TOPIC = "precued.visibility-grants";

    private final ShareRepository shareRepository;
    private final ShareTrackRepository shareTrackRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final ParticipantRoleAssignmentRepository participantRoleAssignmentRepository;
    private final ShareRoleGrantRepository shareRoleGrantRepository;
    private final RoomServiceClient roomServiceClient;
    private final ObjectMapper objectMapper;

    public VisibilityEngineImpl(
            ShareRepository shareRepository,
            ShareTrackRepository shareTrackRepository,
            RoomParticipantRepository roomParticipantRepository,
            ParticipantRoleAssignmentRepository participantRoleAssignmentRepository,
            ShareRoleGrantRepository shareRoleGrantRepository,
            RoomServiceClient roomServiceClient,
            ObjectMapper objectMapper) {
        this.shareRepository = shareRepository;
        this.shareTrackRepository = shareTrackRepository;
        this.roomParticipantRepository = roomParticipantRepository;
        this.participantRoleAssignmentRepository = participantRoleAssignmentRepository;
        this.shareRoleGrantRepository = shareRoleGrantRepository;
        this.roomServiceClient = roomServiceClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ParticipantTrackPermission> computeGrantsForShare(UUID shareId) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("No Share with id " + shareId));

        List<String> trackSids = shareTrackRepository.findByShareId(shareId).stream()
                .filter(track -> track.getUnpublishedAt() == null)
                .map(ShareTrack::getLivekitTrackSid)
                .toList();

        return roomParticipantRepository.findByRoomId(share.getRoom().getId()).stream()
                .filter(participant -> participant.getLeftAt() == null)
                .map(participant -> toPermission(shareId, participant, trackSids))
                .toList();
    }

    private ParticipantTrackPermission toPermission(
            UUID shareId, RoomParticipant participant, List<String> trackSids) {
        Optional<ParticipantRoleAssignment> activeAssignment = participantRoleAssignmentRepository
                .findByRoomParticipantIdAndRevokedAtIsNull(participant.getId());

        boolean allowed = activeAssignment.isPresent()
                && shareRoleGrantRepository
                        .findByShareIdAndRoomRoleIdAndRevokedAtIsNull(
                                shareId, activeAssignment.get().getRoomRole().getId())
                        .isPresent();

        return new ParticipantTrackPermission(
                participant.getLivekitIdentity(),
                allowed,
                allowed ? trackSids : List.of());
    }

    @Override
    public List<ParticipantTrackPermission> computeGrantsForPublisher(UUID publisherParticipantId) {
        RoomParticipant publisher = roomParticipantRepository.findById(publisherParticipantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No RoomParticipant with id " + publisherParticipantId));

        List<Share> activeShares =
                shareRepository.findByPublisherIdAndStatus(publisherParticipantId, Share.Status.ACTIVE);

        // viewerIdentity -> union of every track sid that viewer is allowed
        // across every one of the publisher's active Shares.
        java.util.Map<String, Set<String>> unionedByViewer = new java.util.LinkedHashMap<>();
        for (Share share : activeShares) {
            for (ParticipantTrackPermission grant : computeGrantsForShare(share.getId())) {
                Set<String> tracks = unionedByViewer.computeIfAbsent(grant.livekitIdentity(), k -> new LinkedHashSet<>());
                if (grant.allowed()) {
                    tracks.addAll(grant.trackSids());
                }
            }
        }

        List<String> baseTrackSids = fetchBaseTrackSids(publisher);

        return roomParticipantRepository.findByRoomId(publisher.getRoom().getId()).stream()
                .filter(participant -> participant.getLeftAt() == null)
                .map(participant -> {
                    Set<String> tracks = new LinkedHashSet<>(
                            unionedByViewer.getOrDefault(participant.getLivekitIdentity(), Set.of()));
                    // Base camera/mic tracks are always allowed to anyone
                    // still connected to the room, regardless of Share access.
                    tracks.addAll(baseTrackSids);
                    return new ParticipantTrackPermission(
                            participant.getLivekitIdentity(), !tracks.isEmpty(), List.copyOf(tracks));
                })
                .toList();
    }

    /**
     * Nothing in the schema persists a publisher's non-Share (camera/mic)
     * tracks — see the interface javadoc. LiveKit itself is the only
     * authoritative source, so this is the one step of Part A that is not
     * DB-only. A failure here must not block the Share-scoped grants in the
     * rest of the push: log and treat as "no base tracks this cycle" rather
     * than throwing, consistent with the fail-closed-per-participant (not
     * per-room) blast-radius principle used elsewhere in this engine.
     */
    private List<String> fetchBaseTrackSids(RoomParticipant publisher) {
        String roomName = publisher.getRoom().getLivekitRoomName();
        String publisherIdentity = publisher.getLivekitIdentity();
        try {
            Response<LivekitModels.ParticipantInfo> response =
                    roomServiceClient.getParticipant(roomName, publisherIdentity).execute();
            if (!response.isSuccessful() || response.body() == null) {
                log.warn(
                        "Could not fetch LiveKit participant info for {} in room {} (base tracks omitted this"
                                + " cycle): HTTP {}",
                        publisherIdentity, roomName, response.code());
                return List.of();
            }
            return response.body().getTracksList().stream()
                    .filter(track -> track.getSource() == LivekitModels.TrackSource.CAMERA
                            || track.getSource() == LivekitModels.TrackSource.MICROPHONE)
                    .map(LivekitModels.TrackInfo::getSid)
                    .toList();
        } catch (IOException e) {
            log.warn(
                    "Failed to fetch LiveKit participant info for {} in room {} (base tracks omitted this cycle): {}",
                    publisherIdentity, roomName, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public void pushGrantsToPublisher(UUID publisherParticipantId, List<ParticipantTrackPermission> grants) {
        RoomParticipant publisher = roomParticipantRepository.findById(publisherParticipantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No RoomParticipant with id " + publisherParticipantId));

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(grants);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize grants for publisher " + publisherParticipantId, e);
        }

        String roomName = publisher.getRoom().getLivekitRoomName();
        String publisherIdentity = publisher.getLivekitIdentity();

        try {
            // RoomServiceClient.sendData(roomName, data, kind, destinationSids,
            // destinationIdentities, topic) — note destinationSids comes BEFORE
            // destinationIdentities in this SDK's parameter order (verified
            // against the 0.9.1 bytecode, not assumed). We target by identity
            // only, so destinationSids is empty.
            Response<Void> response = roomServiceClient
                    .sendData(
                            roomName,
                            payload,
                            LivekitModels.DataPacket.Kind.RELIABLE,
                            List.of(),
                            List.of(publisherIdentity),
                            GRANTS_TOPIC)
                    .execute();
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "LiveKit rejected visibility-grants push for publisher " + publisherParticipantId
                                + ": HTTP " + response.code());
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to push grants to publisher " + publisherParticipantId, e);
        }
    }

    @Override
    @Transactional
    public void recomputeAndPushForShare(UUID shareId) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("No Share with id " + shareId));
        recomputeAndPushForPublisher(share.getPublisher().getId());
    }

    @Override
    @Transactional
    public void recomputeAndPushForRoom(UUID roomId) {
        // Each publisher's push is isolated: one publisher's push failing
        // (bad LiveKit response, IO error) must not stop the others in the
        // same room from getting their recompute — same
        // fail-closed-per-participant blast-radius principle as the
        // role-assignment race handling. De-duplicated by publisher: a
        // publisher with two active Shares gets exactly one recompute+push
        // carrying their full permission set, not one per Share.
        shareRepository.findByRoomIdAndStatus(roomId, Share.Status.ACTIVE).stream()
                .map(share -> share.getPublisher().getId())
                .distinct()
                .forEach(publisherId -> {
                    try {
                        recomputeAndPushForPublisher(publisherId);
                    } catch (RuntimeException e) {
                        log.warn(
                                "Failed to recompute/push visibility grants for publisher {}: {}",
                                publisherId,
                                e.getMessage(),
                                e);
                    }
                });
    }
}
