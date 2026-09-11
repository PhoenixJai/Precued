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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the LiveKit-webhook rows of the trigger table (Precued_DataModel.md
 * § "VisibilityEngine — Interface Spec"), with particular attention to the
 * publisher-reconnect rule: a plain participant_joined recomputes the whole
 * room, but a joining participant who publishes an active Share must ALSO
 * get an explicit re-push for that Share, because a reliable LiveKit data
 * message sent while they were disconnected is simply lost, not queued.
 */
@ExtendWith(MockitoExtension.class)
class LiveKitWebhookHandlerTest {

    @Mock private VisibilityEngine engine;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomParticipantRepository roomParticipantRepository;
    @Mock private ShareRepository shareRepository;
    @Mock private ShareTrackRepository shareTrackRepository;

    private LiveKitWebhookHandler handler;

    private final UUID roomId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new LiveKitWebhookHandler(
                engine, roomRepository, roomParticipantRepository, shareRepository, shareTrackRepository);
    }

    private void givenRoomExists() {
        Room room = new Room();
        room.setId(roomId);
        when(roomRepository.findByLivekitRoomName("room-42")).thenReturn(Optional.of(room));
    }

    @Test
    void participantJoined_asPublisher_repushesEachOfTheirActiveShares_inAdditionToRoomRecompute() {
        givenRoomExists();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(participantId);
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "host-1"))
                .thenReturn(Optional.of(publisher));

        Share shareA = new Share();
        shareA.setId(UUID.randomUUID());
        Share shareB = new Share();
        shareB.setId(UUID.randomUUID());
        when(shareRepository.findByPublisherIdAndStatus(participantId, Share.Status.ACTIVE))
                .thenReturn(List.of(shareA, shareB));

        WebhookEvent event = WebhookEvent.newBuilder()
                .setEvent("participant_joined")
                .setRoom(LivekitModels.Room.newBuilder().setName("room-42").build())
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder().setIdentity("host-1").build())
                .build();

        handler.handle(event);

        // General rule: every active Share in the room gets recomputed.
        verify(engine).recomputeAndPushForRoom(roomId);
        // Critical rule: the reconnecting publisher's own Shares are
        // explicitly re-pushed too, not merely assumed covered above.
        verify(engine).recomputeAndPushForShare(shareA.getId());
        verify(engine).recomputeAndPushForShare(shareB.getId());
        // No stale leftAt on this row, so no needless write.
        verify(roomParticipantRepository, never()).save(any());
    }

    @Test
    void participantJoined_nonPublisher_onlyRecomputesTheRoom() {
        givenRoomExists();
        RoomParticipant viewer = new RoomParticipant();
        viewer.setId(participantId);
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "viewer-1"))
                .thenReturn(Optional.of(viewer));
        when(shareRepository.findByPublisherIdAndStatus(participantId, Share.Status.ACTIVE))
                .thenReturn(List.of());

        WebhookEvent event = WebhookEvent.newBuilder()
                .setEvent("participant_joined")
                .setRoom(LivekitModels.Room.newBuilder().setName("room-42").build())
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder().setIdentity("viewer-1").build())
                .build();

        handler.handle(event);

        verify(engine).recomputeAndPushForRoom(roomId);
        verify(engine, never()).recomputeAndPushForShare(org.mockito.ArgumentMatchers.any());
        verify(roomParticipantRepository, never()).save(any());
    }

    /**
     * The reconnect ambiguity: resolveParticipant (used by both
     * participant_joined and participant_left) always finds the SAME row by
     * (roomId, livekitIdentity) — RoomParticipantService.join only creates a
     * new row for a fresh REST /api/room-participants call, never for a
     * LiveKit-protocol-level reconnect under an identity that's already
     * joined. LiveKit fires participant_left then participant_joined for
     * that same identity on a full reconnect (after a resume window
     * expires), so a rejoin finding leftAt already set is a real, expected
     * case, not a hypothetical — and ParticipantSessionInterceptor rejects
     * every request from a leftAt-set participant's session token with
     * nothing else able to ever clear it. Leaving it set here would
     * permanently lock a reconnected participant out of their own session.
     */
    @Test
    void participantJoined_rejoinAfterPriorLeftAt_clearsLeftAt_andStillRunsNormalJoinLogic() {
        givenRoomExists();
        RoomParticipant viewer = new RoomParticipant();
        viewer.setId(participantId);
        viewer.setLeftAt(java.time.Instant.now().minusSeconds(10));
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "viewer-1"))
                .thenReturn(Optional.of(viewer));
        when(shareRepository.findByPublisherIdAndStatus(participantId, Share.Status.ACTIVE))
                .thenReturn(List.of());

        WebhookEvent event = WebhookEvent.newBuilder()
                .setEvent("participant_joined")
                .setRoom(LivekitModels.Room.newBuilder().setName("room-42").build())
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder().setIdentity("viewer-1").build())
                .build();

        handler.handle(event);

        assertThat(viewer.getLeftAt()).isNull();
        verify(roomParticipantRepository).save(viewer);
        verify(engine).recomputeAndPushForRoom(roomId);
    }

    @Test
    void participantLeft_setsLeftAtOnResolvedParticipant_thenRecomputesTheRoom() {
        givenRoomExists();
        RoomParticipant participant = new RoomParticipant();
        participant.setId(participantId);
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "viewer-1"))
                .thenReturn(Optional.of(participant));

        WebhookEvent event = WebhookEvent.newBuilder()
                .setEvent("participant_left")
                .setRoom(LivekitModels.Room.newBuilder().setName("room-42").build())
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder().setIdentity("viewer-1").build())
                .build();

        handler.handle(event);

        assertThat(participant.getLeftAt()).isNotNull().isBeforeOrEqualTo(java.time.Instant.now());
        verify(roomParticipantRepository).save(participant);
        verify(engine).recomputeAndPushForRoom(roomId);
    }

    @Test
    void participantLeft_alreadyMarkedLeft_doesNotOverwriteTimestamp_butStillRecomputes() {
        // Redelivery of the same event (LiveKit retries on transient
        // failures) must not push the timestamp forward on every retry.
        givenRoomExists();
        java.time.Instant firstLeftAt = java.time.Instant.now().minusSeconds(30);
        RoomParticipant participant = new RoomParticipant();
        participant.setId(participantId);
        participant.setLeftAt(firstLeftAt);
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "viewer-1"))
                .thenReturn(Optional.of(participant));

        WebhookEvent event = WebhookEvent.newBuilder()
                .setEvent("participant_left")
                .setRoom(LivekitModels.Room.newBuilder().setName("room-42").build())
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder().setIdentity("viewer-1").build())
                .build();

        handler.handle(event);

        assertThat(participant.getLeftAt()).isEqualTo(firstLeftAt);
        verify(roomParticipantRepository, never()).save(any());
        verify(engine).recomputeAndPushForRoom(roomId);
    }

    @Test
    void trackPublished_recomputesOnlyTheOwningShare() {
        Share share = new Share();
        share.setId(UUID.randomUUID());
        ShareTrack track = new ShareTrack();
        track.setShare(share);
        when(shareTrackRepository.findByLivekitTrackSid("TR_abc")).thenReturn(Optional.of(track));

        WebhookEvent event = WebhookEvent.newBuilder()
                .setEvent("track_published")
                .setTrack(LivekitModels.TrackInfo.newBuilder().setSid("TR_abc").build())
                .build();

        handler.handle(event);

        verify(engine).recomputeAndPushForShare(share.getId());
        verify(engine, never()).recomputeAndPushForRoom(eq(roomId));
    }

    @Test
    void trackPublished_withValidShareIdTag_createsShareTrackAndRecomputes() {
        givenRoomExists();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(participantId);
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "host-1"))
                .thenReturn(Optional.of(publisher));

        Share share = new Share();
        share.setId(UUID.randomUUID());
        share.setStatus(Share.Status.ACTIVE);
        share.setPublisher(publisher);
        when(shareTrackRepository.findByLivekitTrackSid("TR_video1")).thenReturn(Optional.empty());
        when(shareRepository.findById(share.getId())).thenReturn(Optional.of(share));

        WebhookEvent event = trackPublishedEvent("TR_video1", share.getId() + ":Exhibit A", "host-1");

        handler.handle(event);

        ArgumentCaptor<ShareTrack> captor = ArgumentCaptor.forClass(ShareTrack.class);
        verify(shareTrackRepository).save(captor.capture());
        ShareTrack saved = captor.getValue();
        assertThat(saved.getShare()).isSameAs(share);
        assertThat(saved.getLivekitTrackSid()).isEqualTo("TR_video1");
        assertThat(saved.getKind()).isEqualTo(ShareTrack.Kind.VIDEO);
        assertThat(saved.getPublishedAt()).isNotNull();

        verify(engine).recomputeAndPushForShare(share.getId());
    }

    @Test
    void trackPublished_publisherAlreadyLeft_logsAndNoOps_withoutLookingUpTheShare() {
        // LiveKit queues webhook events per resource (track vs. participant),
        // with no cross-resource ordering guarantee: track_published for this
        // participant's track can arrive after participant_left already set
        // leftAt. That must be treated as stale, not as a valid publish.
        givenRoomExists();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(participantId);
        publisher.setLeftAt(java.time.Instant.now());
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "host-1"))
                .thenReturn(Optional.of(publisher));
        when(shareTrackRepository.findByLivekitTrackSid("TR_stale")).thenReturn(Optional.empty());

        WebhookEvent event = trackPublishedEvent("TR_stale", UUID.randomUUID() + ":Exhibit A", "host-1");

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();

        verify(shareRepository, never()).findById(any());
        verify(shareTrackRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }

    @Test
    void trackPublished_nameWithNoDelimiter_logsAndNoOps() {
        givenRoomExists();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(participantId);
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "host-1"))
                .thenReturn(Optional.of(publisher));
        when(shareTrackRepository.findByLivekitTrackSid("TR_bad1")).thenReturn(Optional.empty());

        WebhookEvent event = trackPublishedEvent("TR_bad1", "not-a-tagged-name", "host-1");

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();

        verify(shareTrackRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
        verify(shareRepository, never()).findById(any());
    }

    @Test
    void trackPublished_nameWithNonUuidPrefix_logsAndNoOps() {
        givenRoomExists();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(participantId);
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "host-1"))
                .thenReturn(Optional.of(publisher));
        when(shareTrackRepository.findByLivekitTrackSid("TR_bad2")).thenReturn(Optional.empty());

        WebhookEvent event = trackPublishedEvent("TR_bad2", "not-a-uuid:Exhibit A", "host-1");

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();

        verify(shareTrackRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
        verify(shareRepository, never()).findById(any());
    }

    @Test
    void trackPublished_shareTaggedButBelongsToDifferentPublisher_rejectedAndNoOp() {
        givenRoomExists();
        RoomParticipant publishingParticipant = new RoomParticipant();
        publishingParticipant.setId(participantId);
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "host-1"))
                .thenReturn(Optional.of(publishingParticipant));

        RoomParticipant actualOwner = new RoomParticipant();
        actualOwner.setId(UUID.randomUUID());
        Share share = new Share();
        share.setId(UUID.randomUUID());
        share.setStatus(Share.Status.ACTIVE);
        share.setPublisher(actualOwner); // NOT publishingParticipant — stale/forged tag
        when(shareTrackRepository.findByLivekitTrackSid("TR_forged")).thenReturn(Optional.empty());
        when(shareRepository.findById(share.getId())).thenReturn(Optional.of(share));

        WebhookEvent event = trackPublishedEvent("TR_forged", share.getId() + ":Exhibit A", "host-1");

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();

        verify(shareTrackRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }

    @Test
    void trackPublished_twoConcurrentActiveSharesForSamePublisher_eachAttributedViaItsOwnTag() {
        givenRoomExists();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(participantId);
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "host-1"))
                .thenReturn(Optional.of(publisher));

        Share shareA = new Share();
        shareA.setId(UUID.randomUUID());
        shareA.setStatus(Share.Status.ACTIVE);
        shareA.setPublisher(publisher);

        Share shareB = new Share();
        shareB.setId(UUID.randomUUID());
        shareB.setStatus(Share.Status.ACTIVE);
        shareB.setPublisher(publisher);

        when(shareTrackRepository.findByLivekitTrackSid("TR_a")).thenReturn(Optional.empty());
        when(shareTrackRepository.findByLivekitTrackSid("TR_b")).thenReturn(Optional.empty());
        when(shareRepository.findById(shareA.getId())).thenReturn(Optional.of(shareA));
        when(shareRepository.findById(shareB.getId())).thenReturn(Optional.of(shareB));

        handler.handle(trackPublishedEvent("TR_a", shareA.getId() + ":Exhibit A", "host-1"));
        handler.handle(trackPublishedEvent("TR_b", shareB.getId() + ":Exhibit B", "host-1"));

        ArgumentCaptor<ShareTrack> captor = ArgumentCaptor.forClass(ShareTrack.class);
        verify(shareTrackRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<ShareTrack> saved = captor.getAllValues();
        assertThat(saved).extracting(ShareTrack::getLivekitTrackSid).containsExactly("TR_a", "TR_b");
        assertThat(saved.get(0).getShare()).isSameAs(shareA);
        assertThat(saved.get(1).getShare()).isSameAs(shareB);

        verify(engine).recomputeAndPushForShare(shareA.getId());
        verify(engine).recomputeAndPushForShare(shareB.getId());
    }

    @Test
    void trackUnpublished_setsUnpublishedAtOnResolvedTrack_thenRecomputesTheShare() {
        Share share = new Share();
        share.setId(UUID.randomUUID());
        ShareTrack track = new ShareTrack();
        track.setShare(share);
        track.setLivekitTrackSid("TR_abc");
        when(shareTrackRepository.findByLivekitTrackSid("TR_abc")).thenReturn(Optional.of(track));

        WebhookEvent event = trackUnpublishedEvent("TR_abc");

        handler.handle(event);

        assertThat(track.getUnpublishedAt()).isNotNull().isBeforeOrEqualTo(java.time.Instant.now());
        verify(shareTrackRepository).save(track);
        verify(engine).recomputeAndPushForShare(share.getId());
    }

    @Test
    void trackUnpublished_alreadyUnpublished_doesNotOverwriteTimestamp_butStillRecomputes() {
        // Redelivery of the same event must not push the timestamp forward.
        Share share = new Share();
        share.setId(UUID.randomUUID());
        java.time.Instant firstUnpublishedAt = java.time.Instant.now().minusSeconds(30);
        ShareTrack track = new ShareTrack();
        track.setShare(share);
        track.setUnpublishedAt(firstUnpublishedAt);
        when(shareTrackRepository.findByLivekitTrackSid("TR_abc")).thenReturn(Optional.of(track));

        handler.handle(trackUnpublishedEvent("TR_abc"));

        assertThat(track.getUnpublishedAt()).isEqualTo(firstUnpublishedAt);
        verify(shareTrackRepository, never()).save(any());
        verify(engine).recomputeAndPushForShare(share.getId());
    }

    /**
     * The common case, not an edge case: only Share-tagged tracks (screen
     * shares published with the "<shareId>:<label>" name, per
     * onTrackPublished) ever get a ShareTrack row at all. Every base
     * camera/mic track unpublish resolves to nothing here — that must be a
     * silent no-op, not an error.
     */
    @Test
    void trackUnpublished_unknownTrackSid_noOp() {
        when(shareTrackRepository.findByLivekitTrackSid("TR_camera")).thenReturn(Optional.empty());

        assertThatCode(() -> handler.handle(trackUnpublishedEvent("TR_camera"))).doesNotThrowAnyException();

        verify(shareTrackRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }

    private static WebhookEvent trackUnpublishedEvent(String trackSid) {
        return WebhookEvent.newBuilder()
                .setEvent("track_unpublished")
                .setTrack(LivekitModels.TrackInfo.newBuilder().setSid(trackSid).build())
                .build();
    }

    private static WebhookEvent trackPublishedEvent(String trackSid, String trackName, String publisherIdentity) {
        return WebhookEvent.newBuilder()
                .setEvent("track_published")
                .setRoom(LivekitModels.Room.newBuilder().setName("room-42").build())
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder().setIdentity(publisherIdentity).build())
                .setTrack(LivekitModels.TrackInfo.newBuilder()
                        .setSid(trackSid)
                        .setName(trackName)
                        .setType(LivekitModels.TrackType.VIDEO)
                        .build())
                .build();
    }
}
