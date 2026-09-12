package com.precued.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.entity.ShareSlide;
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
import static org.mockito.Mockito.when;

/**
 * Covers the Chunk 1 slide-level extension to the Runtime Rule
 * (Precued_DataModel.md § "Runtime Rule" — "Extended for slide-level
 * visibility"): for a PRESENTATION-kind Share, a role sees the current
 * slide iff it holds an active whole-share grant (share_slide_id IS NULL)
 * OR an active grant whose ShareSlide.slideIndex matches
 * Share.currentSlideIndex. SCREEN-kind Shares are covered separately by
 * {@link VisibilityEngineImplTest} and are untouched by any of this.
 */
@ExtendWith(MockitoExtension.class)
class VisibilityEngineImplPresentationTest {

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

        when(shareTrackRepository.findByShareId(shareId)).thenReturn(List.of());
    }

    private Share presentationShare(int currentSlideIndex) {
        Room room = new Room();
        room.setId(roomId);

        Share share = new Share();
        share.setId(shareId);
        share.setRoom(room);
        share.setKind(Share.Kind.PRESENTATION);
        share.setCurrentSlideIndex(currentSlideIndex);

        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        return share;
    }

    private ShareSlide slideAt(int index) {
        ShareSlide slide = new ShareSlide();
        slide.setId(UUID.randomUUID());
        slide.setSlideIndex(index);
        return slide;
    }

    private RoomParticipant connectedParticipant(String livekitIdentity) {
        RoomParticipant participant = new RoomParticipant();
        participant.setId(UUID.randomUUID());
        participant.setLivekitIdentity(livekitIdentity);
        participant.setLeftAt(null);
        return participant;
    }

    private void givenActiveAssignment(RoomParticipant participant, UUID roleId) {
        RoomRole role = new RoomRole();
        role.setId(roleId);
        ParticipantRoleAssignment assignment = new ParticipantRoleAssignment();
        assignment.setRoomRole(role);
        when(participantRoleAssignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(participant.getId()))
                .thenReturn(Optional.of(assignment));
    }

    private ShareRoleGrant wholeShareGrant() {
        return new ShareRoleGrant(); // shareSlide left null
    }

    private ShareRoleGrant slideSpecificGrant(ShareSlide slide) {
        ShareRoleGrant grant = new ShareRoleGrant();
        grant.setShareSlide(slide);
        return grant;
    }

    @Test
    void wholeShareGrant_isAllowedRegardlessOfCurrentSlide() {
        presentationShare(2);
        RoomParticipant participant = connectedParticipant("viewer-1");
        givenActiveAssignment(participant, roomRoleId);
        when(shareRoleGrantRepository.findAllByShareIdAndRoomRoleIdAndRevokedAtIsNull(shareId, roomRoleId))
                .thenReturn(List.of(wholeShareGrant()));
        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(participant));

        List<ParticipantTrackPermission> grants = engine.computeGrantsForShare(shareId);

        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).allowed()).isTrue();
    }

    @Test
    void slideSpecificGrant_matchingCurrentSlide_isAllowed() {
        presentationShare(1);
        ShareSlide slide1 = slideAt(1);
        RoomParticipant participant = connectedParticipant("viewer-2");
        givenActiveAssignment(participant, roomRoleId);
        when(shareRoleGrantRepository.findAllByShareIdAndRoomRoleIdAndRevokedAtIsNull(shareId, roomRoleId))
                .thenReturn(List.of(slideSpecificGrant(slide1)));
        when(shareSlideRepository.findByShareIdAndSlideIndex(shareId, 1)).thenReturn(Optional.of(slide1));
        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(participant));

        List<ParticipantTrackPermission> grants = engine.computeGrantsForShare(shareId);

        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).allowed()).isTrue();
    }

    @Test
    void slideSpecificGrant_notMatchingCurrentSlide_isDenied() {
        presentationShare(0);
        ShareSlide slide1 = slideAt(1); // grant is for slide 1, current slide is 0
        RoomParticipant participant = connectedParticipant("viewer-3");
        givenActiveAssignment(participant, roomRoleId);
        when(shareRoleGrantRepository.findAllByShareIdAndRoomRoleIdAndRevokedAtIsNull(shareId, roomRoleId))
                .thenReturn(List.of(slideSpecificGrant(slide1)));
        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(participant));

        List<ParticipantTrackPermission> grants = engine.computeGrantsForShare(shareId);

        assertThat(grants).hasSize(1);
        ParticipantTrackPermission grant = grants.get(0);
        assertThat(grant.allowed()).isFalse();
        assertThat(grant.trackSids()).isEmpty();
    }

    @Test
    void noGrantAtAll_isDenied_existingBehaviorUnaffected() {
        presentationShare(0);
        RoomParticipant participant = connectedParticipant("viewer-4");
        givenActiveAssignment(participant, roomRoleId);
        when(shareRoleGrantRepository.findAllByShareIdAndRoomRoleIdAndRevokedAtIsNull(shareId, roomRoleId))
                .thenReturn(List.of());
        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(participant));

        List<ParticipantTrackPermission> grants = engine.computeGrantsForShare(shareId);

        assertThat(grants).hasSize(1);
        ParticipantTrackPermission grant = grants.get(0);
        assertThat(grant.allowed()).isFalse();
        assertThat(grant.trackSids()).isEmpty();
    }

    @Test
    void roleWithBothWholeShareAndSlideSpecificGrants_isAllowedOnAnySlide() {
        presentationShare(5); // no slide at index 5 has an explicit grant
        ShareSlide slide1 = slideAt(1);
        RoomParticipant participant = connectedParticipant("viewer-5");
        givenActiveAssignment(participant, roomRoleId);
        when(shareRoleGrantRepository.findAllByShareIdAndRoomRoleIdAndRevokedAtIsNull(shareId, roomRoleId))
                .thenReturn(List.of(slideSpecificGrant(slide1), wholeShareGrant()));
        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(participant));

        List<ParticipantTrackPermission> grants = engine.computeGrantsForShare(shareId);

        assertThat(grants.get(0).allowed()).isTrue();
    }
}
