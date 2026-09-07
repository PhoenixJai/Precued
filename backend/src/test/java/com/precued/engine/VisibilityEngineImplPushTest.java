package com.precued.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers Part B (push to the publisher's LiveKit client) and the room-scoped
 * orchestration used by the ParticipantRoleAssignment / participant_joined /
 * participant_left rows of the trigger table (Precued_DataModel.md §
 * "VisibilityEngine — Interface Spec").
 */
@ExtendWith(MockitoExtension.class)
class VisibilityEngineImplPushTest {

    @Mock private ShareRepository shareRepository;
    @Mock private ShareTrackRepository shareTrackRepository;
    @Mock private RoomParticipantRepository roomParticipantRepository;
    @Mock private ParticipantRoleAssignmentRepository participantRoleAssignmentRepository;
    @Mock private ShareRoleGrantRepository shareRoleGrantRepository;
    @Mock private RoomServiceClient roomServiceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VisibilityEngineImpl engine;

    private final UUID roomId = UUID.randomUUID();
    private final UUID shareId = UUID.randomUUID();

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
    }

    @Test
    void pushGrantsToPublisher_targetsOnlyThePublisherIdentity_notRoomWide() throws IOException {
        Room room = new Room();
        room.setId(roomId);
        room.setLivekitRoomName("room-42");

        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(UUID.randomUUID());
        publisher.setLivekitIdentity("host-1");

        Share share = new Share();
        share.setId(shareId);
        share.setRoom(room);
        share.setPublisher(publisher);

        when(shareRepository.findById(shareId)).thenReturn(java.util.Optional.of(share));
        stubSuccessfulSend();

        List<ParticipantTrackPermission> grants =
                List.of(new ParticipantTrackPermission("viewer-1", true, List.of("TR_video1")));

        engine.pushGrantsToPublisher(shareId, grants);

        ArgumentCaptor<List<String>> destinationIdentities = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> destinationSids = ArgumentCaptor.forClass(List.class);
        verify(roomServiceClient).sendData(
                eq("room-42"),
                any(byte[].class),
                eq(LivekitModels.DataPacket.Kind.RELIABLE),
                destinationSids.capture(),
                destinationIdentities.capture(),
                anyString());

        assertThat(destinationIdentities.getValue()).containsExactly("host-1");
        assertThat(destinationSids.getValue()).isEmpty();
    }

    @Test
    void pushGrantsToPublisher_sendsExactKindDestinationsAndSerializedPayload() throws IOException {
        Room room = new Room();
        room.setId(roomId);
        room.setLivekitRoomName("room-42");

        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(UUID.randomUUID());
        publisher.setLivekitIdentity("host-1");

        Share share = new Share();
        share.setId(shareId);
        share.setRoom(room);
        share.setPublisher(publisher);

        when(shareRepository.findById(shareId)).thenReturn(java.util.Optional.of(share));
        stubSuccessfulSend();

        List<ParticipantTrackPermission> grants = List.of(
                new ParticipantTrackPermission("viewer-1", true, List.of("TR_video1", "TR_audio1")),
                new ParticipantTrackPermission("viewer-2", false, List.of()));

        engine.pushGrantsToPublisher(shareId, grants);

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<List<String>> destinationSids = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> destinationIdentities = ArgumentCaptor.forClass(List.class);
        verify(roomServiceClient).sendData(
                eq("room-42"),
                payload.capture(),
                eq(LivekitModels.DataPacket.Kind.RELIABLE), // fails if a refactor swaps in LOSSY or any other kind
                destinationSids.capture(),
                destinationIdentities.capture(),
                anyString());

        assertThat(destinationSids.getValue()).isEmpty();
        assertThat(destinationIdentities.getValue()).containsExactly("host-1");

        List<ParticipantTrackPermission> deserialized = objectMapper.readValue(
                payload.getValue(), new TypeReference<List<ParticipantTrackPermission>>() {});
        assertThat(deserialized).containsExactlyElementsOf(grants);
    }

    @Test
    void recomputeAndPushForRoom_pushesToEveryActiveShareInTheRoom() throws IOException {
        Room room = new Room();
        room.setId(roomId);
        room.setLivekitRoomName("room-42");

        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(UUID.randomUUID());
        publisher.setLivekitIdentity("host-1");

        Share shareA = new Share();
        shareA.setId(UUID.randomUUID());
        shareA.setRoom(room);
        shareA.setPublisher(publisher);

        Share shareB = new Share();
        shareB.setId(UUID.randomUUID());
        shareB.setRoom(room);
        shareB.setPublisher(publisher);

        when(shareRepository.findByRoomIdAndStatus(roomId, Share.Status.ACTIVE))
                .thenReturn(List.of(shareA, shareB));
        when(shareRepository.findById(shareA.getId())).thenReturn(java.util.Optional.of(shareA));
        when(shareRepository.findById(shareB.getId())).thenReturn(java.util.Optional.of(shareB));
        when(shareTrackRepository.findByShareId(any())).thenReturn(List.of());
        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of());
        stubSuccessfulSend();

        engine.recomputeAndPushForRoom(roomId);

        verify(roomServiceClient, times(2)).sendData(
                eq("room-42"), any(byte[].class), eq(LivekitModels.DataPacket.Kind.RELIABLE),
                any(), any(), anyString());
    }

    @Test
    void recomputeAndPushForRoom_isolatesFailurePerShare_continuesToOtherShares() throws IOException {
        Room room = new Room();
        room.setId(roomId);
        room.setLivekitRoomName("room-42");

        RoomParticipant publisherA = new RoomParticipant();
        publisherA.setId(UUID.randomUUID());
        publisherA.setLivekitIdentity("host-A");

        RoomParticipant publisherB = new RoomParticipant();
        publisherB.setId(UUID.randomUUID());
        publisherB.setLivekitIdentity("host-B");

        Share shareA = new Share();
        shareA.setId(UUID.randomUUID());
        shareA.setRoom(room);
        shareA.setPublisher(publisherA);

        Share shareB = new Share();
        shareB.setId(UUID.randomUUID());
        shareB.setRoom(room);
        shareB.setPublisher(publisherB);

        when(shareRepository.findByRoomIdAndStatus(roomId, Share.Status.ACTIVE))
                .thenReturn(List.of(shareA, shareB));
        when(shareRepository.findById(shareA.getId())).thenReturn(java.util.Optional.of(shareA));
        when(shareRepository.findById(shareB.getId())).thenReturn(java.util.Optional.of(shareB));
        when(shareTrackRepository.findByShareId(any())).thenReturn(List.of());
        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of());

        // shareA's publisher push fails outright (simulated network error) ...
        Call<Void> failingCall = mock(Call.class);
        when(failingCall.execute()).thenThrow(new IOException("network blip"));
        when(roomServiceClient.sendData(
                anyString(),
                any(byte[].class),
                any(LivekitModels.DataPacket.Kind.class),
                any(),
                eq(List.of("host-A")),
                anyString()))
                .thenReturn(failingCall);

        // ... but shareB's publisher push must still be attempted and succeed.
        Call<Void> successCall = mock(Call.class);
        when(successCall.execute()).thenReturn(Response.success(null));
        when(roomServiceClient.sendData(
                anyString(),
                any(byte[].class),
                any(LivekitModels.DataPacket.Kind.class),
                any(),
                eq(List.of("host-B")),
                anyString()))
                .thenReturn(successCall);

        // Must not throw: shareA's failure is isolated, not propagated.
        engine.recomputeAndPushForRoom(roomId);

        verify(roomServiceClient).sendData(
                eq("room-42"),
                any(byte[].class),
                eq(LivekitModels.DataPacket.Kind.RELIABLE),
                any(),
                eq(List.of("host-A")),
                anyString());
        verify(roomServiceClient).sendData(
                eq("room-42"),
                any(byte[].class),
                eq(LivekitModels.DataPacket.Kind.RELIABLE),
                any(),
                eq(List.of("host-B")),
                anyString());
    }

    @SuppressWarnings("unchecked")
    private void stubSuccessfulSend() throws IOException {
        Call<Void> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(null));
        when(roomServiceClient.sendData(
                anyString(), any(byte[].class), any(LivekitModels.DataPacket.Kind.class), any(), any(), anyString()))
                .thenReturn(call);
    }
}
