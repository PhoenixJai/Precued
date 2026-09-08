package com.precued.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.entity.ShareTrack;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareRoleGrantRepository;
import com.precued.repository.ShareTrackRepository;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@link VisibilityEngineImpl#computeGrantsForPublisher}, the fix for
 * the publisher-wide permission bug: LiveKit's client-side
 * setTrackSubscriptionPermissions call replaces a publisher's entire
 * permission matrix, so a compute scoped to only one of a publisher's
 * active Shares is never safe to push on its own. This must union every
 * active Share's grants per viewer, plus base camera/mic tracks that are
 * always allowed to anyone still connected to the room.
 */
@ExtendWith(MockitoExtension.class)
class VisibilityEngineImplPublisherComputeTest {

    @Mock private ShareRepository shareRepository;
    @Mock private ShareTrackRepository shareTrackRepository;
    @Mock private RoomParticipantRepository roomParticipantRepository;
    @Mock private ParticipantRoleAssignmentRepository participantRoleAssignmentRepository;
    @Mock private ShareRoleGrantRepository shareRoleGrantRepository;
    @Mock private RoomServiceClient roomServiceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VisibilityEngineImpl engine;

    private final UUID roomId = UUID.randomUUID();
    private final UUID roleAId = UUID.randomUUID();
    private final UUID roleBId = UUID.randomUUID();

    private RoomParticipant publisher;
    private Room room;
    private final List<Share> activeShares = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        engine = new VisibilityEngineImpl(
                shareRepository,
                shareTrackRepository,
                roomParticipantRepository,
                participantRoleAssignmentRepository,
                shareRoleGrantRepository,
                roomServiceClient,
                objectMapper);

        room = new Room();
        room.setId(roomId);
        room.setLivekitRoomName("room-42");

        publisher = new RoomParticipant();
        publisher.setId(UUID.randomUUID());
        publisher.setRoom(room);
        publisher.setLivekitIdentity("host-1");
        when(roomParticipantRepository.findById(publisher.getId())).thenReturn(Optional.of(publisher));
    }

    @Test
    void twoActiveShares_viewerWithAccessToOnlyOneShare_keepsThatSharesTracksWithoutLosingBaseTracks()
            throws IOException {
        Share shareA = activeShare("TR_A_video");
        Share shareB = activeShare("TR_B_video");

        RoomParticipant viewer = connectedParticipant("viewer-1");
        RoomRole roleA = roleWithId(roleAId);
        givenActiveAssignment(viewer, roleA);

        when(shareRoleGrantRepository.findByShareIdAndRoomRoleIdAndRevokedAtIsNull(shareA.getId(), roleAId))
                .thenReturn(Optional.of(new ShareRoleGrant()));
        when(shareRoleGrantRepository.findByShareIdAndRoomRoleIdAndRevokedAtIsNull(shareB.getId(), roleAId))
                .thenReturn(Optional.empty());

        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(viewer));
        stubBaseTracks("TR_cam1");

        List<ParticipantTrackPermission> grants = engine.computeGrantsForPublisher(publisher.getId());

        assertThat(grants).hasSize(1);
        ParticipantTrackPermission grant = grants.get(0);
        assertThat(grant.livekitIdentity()).isEqualTo("viewer-1");
        assertThat(grant.allowed()).isTrue();
        // Access to shareA is retained, shareB (not granted) is excluded,
        // and the publisher's base camera track is still present — a second
        // Share's recompute must never silently drop tracks the viewer is
        // separately entitled to.
        assertThat(grant.trackSids()).containsExactlyInAnyOrder("TR_A_video", "TR_cam1");
    }

    @Test
    void viewerWithNoShareGrantAtAll_stillReceivesBaseCameraMicTracks() throws IOException {
        activeShare("TR_A_video");

        RoomParticipant viewer = connectedParticipant("viewer-2");
        RoomRole roleB = roleWithId(roleBId);
        givenActiveAssignment(viewer, roleB);
        when(shareRoleGrantRepository.findByShareIdAndRoomRoleIdAndRevokedAtIsNull(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(roleBId)))
                .thenReturn(Optional.empty());

        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(viewer));
        stubBaseTracks("TR_mic1");

        List<ParticipantTrackPermission> grants = engine.computeGrantsForPublisher(publisher.getId());

        assertThat(grants).hasSize(1);
        ParticipantTrackPermission grant = grants.get(0);
        assertThat(grant.allowed()).isTrue();
        assertThat(grant.trackSids()).containsExactly("TR_mic1");
    }

    @Test
    void noShareGrantAndNoBaseTracks_notAllowedWithEmptyTracks() throws IOException {
        activeShare("TR_A_video");

        RoomParticipant viewer = connectedParticipant("viewer-3");
        when(participantRoleAssignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(viewer.getId()))
                .thenReturn(Optional.empty());

        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(viewer));
        stubBaseTracks();

        List<ParticipantTrackPermission> grants = engine.computeGrantsForPublisher(publisher.getId());

        assertThat(grants).hasSize(1);
        ParticipantTrackPermission grant = grants.get(0);
        assertThat(grant.allowed()).isFalse();
        assertThat(grant.trackSids()).isEmpty();
    }

    @Test
    void liveKitLookupFails_baseTracksOmittedButShareGrantsStillCompute() throws IOException {
        Share shareA = activeShare("TR_A_video");

        RoomParticipant viewer = connectedParticipant("viewer-4");
        RoomRole roleA = roleWithId(roleAId);
        givenActiveAssignment(viewer, roleA);
        when(shareRoleGrantRepository.findByShareIdAndRoomRoleIdAndRevokedAtIsNull(shareA.getId(), roleAId))
                .thenReturn(Optional.of(new ShareRoleGrant()));

        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(viewer));

        @SuppressWarnings("unchecked")
        Call<LivekitModels.ParticipantInfo> failingCall = mock(Call.class);
        when(failingCall.execute()).thenThrow(new IOException("livekit unreachable"));
        when(roomServiceClient.getParticipant("room-42", "host-1")).thenReturn(failingCall);

        List<ParticipantTrackPermission> grants = engine.computeGrantsForPublisher(publisher.getId());

        assertThat(grants).hasSize(1);
        ParticipantTrackPermission grant = grants.get(0);
        assertThat(grant.allowed()).isTrue();
        assertThat(grant.trackSids()).containsExactly("TR_A_video");
    }

    private Share activeShare(String trackSid) {
        Share share = new Share();
        share.setId(UUID.randomUUID());
        share.setRoom(room);
        share.setPublisher(publisher);
        when(shareTrackRepository.findByShareId(share.getId())).thenReturn(List.of(track(trackSid)));
        when(shareRepository.findById(share.getId())).thenReturn(Optional.of(share));
        activeShares.add(share);
        when(shareRepository.findByPublisherIdAndStatus(publisher.getId(), Share.Status.ACTIVE))
                .thenReturn(List.copyOf(activeShares));
        return share;
    }

    private ShareTrack track(String sid) {
        ShareTrack track = new ShareTrack();
        track.setLivekitTrackSid(sid);
        return track;
    }

    @SuppressWarnings("unchecked")
    private void stubBaseTracks(String... trackSids) throws IOException {
        LivekitModels.ParticipantInfo.Builder infoBuilder =
                LivekitModels.ParticipantInfo.newBuilder().setIdentity("host-1");
        for (String sid : trackSids) {
            infoBuilder.addTracks(LivekitModels.TrackInfo.newBuilder()
                    .setSid(sid)
                    .setSource(LivekitModels.TrackSource.CAMERA));
        }
        Call<LivekitModels.ParticipantInfo> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(infoBuilder.build()));
        when(roomServiceClient.getParticipant("room-42", "host-1")).thenReturn(call);
    }

    private RoomParticipant connectedParticipant(String livekitIdentity) {
        RoomParticipant participant = new RoomParticipant();
        participant.setId(UUID.randomUUID());
        participant.setRoom(room);
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
