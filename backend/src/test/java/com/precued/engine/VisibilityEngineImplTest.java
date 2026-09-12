package com.precued.engine;

import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareRoleGrantRepository;
import com.precued.repository.ShareSlideRepository;
import com.precued.repository.ShareTrackRepository;
import io.livekit.server.RoomServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers the Runtime Rule join in VisibilityEngineImpl#computeGrantsForShare,
 * with particular attention to the fail-closed case (Precued_DataModel.md §
 * "Failure / race handling"): a participant with no currently active
 * ParticipantRoleAssignment must be denied for every Share, not deferred to
 * a stale role or treated as an error.
 */
@ExtendWith(MockitoExtension.class)
class VisibilityEngineImplTest {

    @Mock private ShareRepository shareRepository;
    @Mock private ShareTrackRepository shareTrackRepository;
    @Mock private RoomParticipantRepository roomParticipantRepository;
    @Mock private ParticipantRoleAssignmentRepository participantRoleAssignmentRepository;
    @Mock private ShareRoleGrantRepository shareRoleGrantRepository;
    @Mock private ShareSlideRepository shareSlideRepository;
    @Mock private RoomServiceClient roomServiceClient;
    @Mock private ObjectMapper objectMapper;

    private VisibilityEngineImpl engine;

    private final UUID roomId = UUID.randomUUID();
    private final UUID shareId = UUID.randomUUID();
    private final UUID roomRoleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new VisibilityEngineImpl(
                shareRepository,
                shareTrackRepository,
                roomParticipantRepository,
                participantRoleAssignmentRepository,
                shareRoleGrantRepository,
                shareSlideRepository,
                roomServiceClient,
                objectMapper);

        Room room = new Room();
        room.setId(roomId);

        Share share = new Share();
        share.setId(shareId);
        share.setRoom(room);

        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        when(shareTrackRepository.findByShareId(shareId)).thenReturn(List.of());
    }

    @Test
    void activeAssignmentWithActiveGrant_isAllowed() {
        RoomParticipant participant = connectedParticipant("viewer-1");
        RoomRole role = roleWithId(roomRoleId);
        givenActiveAssignment(participant, role);
        when(shareRoleGrantRepository.findByShareIdAndRoomRoleIdAndRevokedAtIsNull(shareId, roomRoleId))
                .thenReturn(Optional.of(new ShareRoleGrant()));
        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(participant));

        List<ParticipantTrackPermission> grants = engine.computeGrantsForShare(shareId);

        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).livekitIdentity()).isEqualTo("viewer-1");
        assertThat(grants.get(0).allowed()).isTrue();
    }

    @Test
    void activeAssignmentWithNoGrant_isNotAllowed() {
        RoomParticipant participant = connectedParticipant("viewer-2");
        RoomRole role = roleWithId(roomRoleId);
        givenActiveAssignment(participant, role);
        when(shareRoleGrantRepository.findByShareIdAndRoomRoleIdAndRevokedAtIsNull(shareId, roomRoleId))
                .thenReturn(Optional.empty());
        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(participant));

        List<ParticipantTrackPermission> grants = engine.computeGrantsForShare(shareId);

        assertThat(grants).hasSize(1);
        ParticipantTrackPermission grant = grants.get(0);
        assertThat(grant.allowed()).isFalse();
        assertThat(grant.trackSids()).isEmpty();
    }

    @Test
    void noActiveAssignment_isNotAllowed_evenIfSomeGrantExists() {
        RoomParticipant participant = connectedParticipant("viewer-3");
        when(participantRoleAssignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(participant.getId()))
                .thenReturn(Optional.empty());
        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(participant));

        List<ParticipantTrackPermission> grants = engine.computeGrantsForShare(shareId);

        assertThat(grants).hasSize(1);
        ParticipantTrackPermission grant = grants.get(0);
        assertThat(grant.livekitIdentity()).isEqualTo("viewer-3");
        assertThat(grant.allowed()).isFalse();
        assertThat(grant.trackSids()).isEmpty();
        // The gap must never fall back to checking a grant for some role —
        // there is no active role to check against.
        verifyNoGrantLookupHappened();
    }

    private void verifyNoGrantLookupHappened() {
        org.mockito.Mockito.verify(shareRoleGrantRepository, org.mockito.Mockito.never())
                .findByShareIdAndRoomRoleIdAndRevokedAtIsNull(any(), any());
    }

    private RoomParticipant connectedParticipant(String livekitIdentity) {
        RoomParticipant participant = new RoomParticipant();
        participant.setId(UUID.randomUUID());
        participant.setLivekitIdentity(livekitIdentity);
        participant.setLeftAt(null);
        return participant;
    }

    private RoomRole roleWithId(UUID id) {
        RoomRole role = new RoomRole();
        role.setId(id);
        return role;
    }

    private void givenActiveAssignment(RoomParticipant participant, RoomRole role) {
        ParticipantRoleAssignment assignment = new ParticipantRoleAssignment();
        assignment.setRoomRole(role);
        when(participantRoleAssignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(participant.getId()))
                .thenReturn(Optional.of(assignment));
    }
}
