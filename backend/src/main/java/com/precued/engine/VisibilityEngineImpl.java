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
import org.springframework.stereotype.Component;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Part A: pure DB compute (unchanged from the original implementation).
 * Part B: push via a LiveKit data message, RELIABLE mode, targeted at the
 * Share's publisher_participant_id only — see "VisibilityEngine — Interface
 * Spec" / transport decision in Precued_DataModel.md for why (the publisher
 * is already connected to LiveKit; a second websocket channel would just be
 * a second thing to fail).
 */
@Component
public class VisibilityEngineImpl implements VisibilityEngine {

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
    public void pushGrantsToPublisher(UUID shareId, List<ParticipantTrackPermission> grants) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("No Share with id " + shareId));

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(grants);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize grants for share " + shareId, e);
        }

        String roomName = share.getRoom().getLivekitRoomName();
        String publisherIdentity = share.getPublisher().getLivekitIdentity();

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
                        "LiveKit rejected visibility-grants push for share " + shareId
                                + ": HTTP " + response.code());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to push grants to publisher for share " + shareId, e);
        }
    }

    @Override
    public void recomputeAndPushForRoom(UUID roomId) {
        shareRepository.findByRoomIdAndStatus(roomId, Share.Status.ACTIVE)
                .forEach(share -> recomputeAndPushForShare(share.getId()));
    }
}
