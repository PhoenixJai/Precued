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
    }

    @Test
    void participantLeft_recomputesTheRoom() {
        givenRoomExists();
        WebhookEvent event = WebhookEvent.newBuilder()
                .setEvent("participant_left")
                .setRoom(LivekitModels.Room.newBuilder().setName("room-42").build())
                .build();

        handler.handle(event);

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
    void trackPublished_createsShareTrack_andRecomputes_whenExactlyOneActiveShareForPublisher() {
        givenRoomExists();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(participantId);
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "host-1"))
                .thenReturn(Optional.of(publisher));

        Share share = new Share();
        share.setId(UUID.randomUUID());
        when(shareTrackRepository.findByLivekitTrackSid("TR_video1")).thenReturn(Optional.empty());
        when(shareRepository.findByPublisherIdAndStatus(participantId, Share.Status.ACTIVE))
                .thenReturn(List.of(share));

        WebhookEvent event = WebhookEvent.newBuilder()
                .setEvent("track_published")
                .setRoom(LivekitModels.Room.newBuilder().setName("room-42").build())
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder().setIdentity("host-1").build())
                .setTrack(LivekitModels.TrackInfo.newBuilder()
                        .setSid("TR_video1")
                        .setType(LivekitModels.TrackType.VIDEO)
                        .build())
                .build();

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
    void trackPublished_noActiveShareForPublisher_logsAndNoOps_withoutThrowingOrCreatingARow() {
        givenRoomExists();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(participantId);
        when(roomParticipantRepository.findByRoomIdAndLivekitIdentity(roomId, "host-1"))
                .thenReturn(Optional.of(publisher));

        when(shareTrackRepository.findByLivekitTrackSid("TR_orphan")).thenReturn(Optional.empty());
        when(shareRepository.findByPublisherIdAndStatus(participantId, Share.Status.ACTIVE))
                .thenReturn(List.of());

        WebhookEvent event = WebhookEvent.newBuilder()
                .setEvent("track_published")
                .setRoom(LivekitModels.Room.newBuilder().setName("room-42").build())
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder().setIdentity("host-1").build())
                .setTrack(LivekitModels.TrackInfo.newBuilder()
                        .setSid("TR_orphan")
                        .setType(LivekitModels.TrackType.VIDEO)
                        .build())
                .build();

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();

        verify(shareTrackRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }
}
