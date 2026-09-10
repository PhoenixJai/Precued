package com.precued.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precued.controller.dto.LiveKitTokenResponse;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers the LiveKit client-token issuance endpoint's service: a
 * RoomParticipant must get back a JWT scoped to their own livekit_identity
 * and the room's livekit_room_name, plus the LiveKit server URL the
 * frontend needs to connect (both taken from precued.livekit.* config, not
 * re-derived or guessed).
 */
@ExtendWith(MockitoExtension.class)
class LiveKitTokenServiceTest {

    @Mock private RoomParticipantRepository roomParticipantRepository;
    @Mock private RoomRepository roomRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LiveKitTokenService service;

    private final UUID participantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LiveKitTokenService(
                roomParticipantRepository, roomRepository, "test-api-key",
                "test-api-secret-must-be-32-bytes!!", "wss://precued.livekit.cloud");
    }

    @Test
    void issueToken_existingParticipant_returnsJwtScopedToIdentityAndRoom() throws Exception {
        Room room = new Room();
        room.setId(UUID.randomUUID());
        room.setLivekitRoomName("room-42");

        RoomParticipant participant = new RoomParticipant();
        participant.setId(participantId);
        participant.setRoom(room);
        participant.setLivekitIdentity("identity-1");
        participant.setDisplayName("Jordan");
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        LiveKitTokenResponse response = service.issueToken(participantId);

        assertThat(response.livekitUrl()).isEqualTo("wss://precued.livekit.cloud");
        assertThat(response.roomName()).isEqualTo("room-42");
        assertThat(response.identity()).isEqualTo("identity-1");
        assertThat(response.token()).isNotBlank();

        String[] segments = response.token().split("\\.");
        assertThat(segments).hasSize(3); // header.payload.signature

        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(segments[1]));
        assertThat(payload.get("sub").asText()).isEqualTo("identity-1");
        assertThat(payload.get("video").get("room").asText()).isEqualTo("room-42");
        assertThat(payload.get("video").get("roomJoin").asBoolean()).isTrue();
    }

    /**
     * canPublishData must never be granted to a participant token: the only
     * legitimate sender of a visibility-grants data message is the backend
     * itself, via RoomServiceClient's server API (VisibilityEngineImpl), not
     * a client token. A participant token that could publish data could
     * forge that message to every other client in the room.
     */
    @Test
    void issueToken_neverGrantsCanPublishData() throws Exception {
        Room room = new Room();
        room.setId(UUID.randomUUID());
        room.setLivekitRoomName("room-42");

        RoomParticipant participant = new RoomParticipant();
        participant.setId(participantId);
        participant.setRoom(room);
        participant.setLivekitIdentity("identity-1");
        participant.setDisplayName("Jordan");
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        LiveKitTokenResponse response = service.issueToken(participantId);

        String[] segments = response.token().split("\\.");
        JsonNode video = objectMapper.readTree(Base64.getUrlDecoder().decode(segments[1])).get("video");
        assertThat(video.get("canPublishData").asBoolean()).isFalse();
    }

    @Test
    void issueToken_unknownParticipant_throwsIllegalArgumentException() {
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueToken(participantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(participantId.toString());
    }
}
