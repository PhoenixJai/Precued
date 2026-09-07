package com.precued.engine;

import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.entity.ShareTrack;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareRoleGrantRepository;
import com.precued.repository.ShareTrackRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Part A only — see VisibilityEngine for the full Part A/B split. Part B
 * (the push channel to the publisher's client) has no transport built yet,
 * so {@link #pushGrantsToPublisher} and {@link #recomputeAndPushForRoom}
 * intentionally throw until that channel exists.
 */
@Component
public class VisibilityEngineImpl implements VisibilityEngine {

    private final ShareRepository shareRepository;
    private final ShareTrackRepository shareTrackRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final ParticipantRoleAssignmentRepository participantRoleAssignmentRepository;
    private final ShareRoleGrantRepository shareRoleGrantRepository;

    public VisibilityEngineImpl(
            ShareRepository shareRepository,
            ShareTrackRepository shareTrackRepository,
            RoomParticipantRepository roomParticipantRepository,
            ParticipantRoleAssignmentRepository participantRoleAssignmentRepository,
            ShareRoleGrantRepository shareRoleGrantRepository) {
        this.shareRepository = shareRepository;
        this.shareTrackRepository = shareTrackRepository;
        this.roomParticipantRepository = roomParticipantRepository;
        this.participantRoleAssignmentRepository = participantRoleAssignmentRepository;
        this.shareRoleGrantRepository = shareRoleGrantRepository;
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
        throw new UnsupportedOperationException(
                "Part B push channel (data message / websocket to the publisher's client) not yet built");
    }

    @Override
    public void recomputeAndPushForRoom(UUID roomId) {
        throw new UnsupportedOperationException("Room-wide recompute orchestration not yet built");
    }
}
