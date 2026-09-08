package com.precued.service;

import com.precued.controller.dto.RoomParticipantWithGrantsResponse;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRepository;
import com.precued.repository.ShareRoleGrantRepository;
import com.precued.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers RoomParticipantService#listWithGrants: every RoomParticipant in a
 * room, resolved with their currently active RoomRole and the
 * ShareRoleGrants currently applicable to it (active grant, on a
 * still-active Share) — the "GET RoomParticipants with current role + active
 * grants" endpoint the frontend needs.
 */
@ExtendWith(MockitoExtension.class)
class RoomParticipantServiceTest {

    @Mock private RoomParticipantRepository roomParticipantRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private ParticipantRoleAssignmentRepository assignmentRepository;
    @Mock private ShareRoleGrantRepository shareRoleGrantRepository;

    private RoomParticipantService service;

    private final UUID roomId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RoomParticipantService(
                roomParticipantRepository, roomRepository, userRepository, assignmentRepository,
                shareRoleGrantRepository);
    }

    @Test
    void listWithGrants_participantWithActiveRoleAndGrant_includesRoleAndGrantIds() {
        when(roomRepository.existsById(roomId)).thenReturn(true);
        RoomParticipant participant = participant("viewer-1");

        UUID roleId = UUID.randomUUID();
        RoomRole role = new RoomRole();
        role.setId(roleId);
        ParticipantRoleAssignment assignment = new ParticipantRoleAssignment();
        assignment.setRoomRole(role);
        when(assignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(participant.getId()))
                .thenReturn(Optional.of(assignment));

        UUID grantId = UUID.randomUUID();
        ShareRoleGrant grant = new ShareRoleGrant();
        grant.setId(grantId);
        when(shareRoleGrantRepository.findByRoomRoleIdAndRevokedAtIsNullAndShare_Status(roleId, Share.Status.ACTIVE))
                .thenReturn(List.of(grant));

        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(participant));

        List<RoomParticipantWithGrantsResponse> result = service.listWithGrants(roomId);

        assertThat(result).hasSize(1);
        RoomParticipantWithGrantsResponse response = result.get(0);
        assertThat(response.livekitIdentity()).isEqualTo("viewer-1");
        assertThat(response.activeRoomRoleId()).isEqualTo(roleId);
        assertThat(response.activeShareRoleGrantIds()).containsExactly(grantId);
    }

    @Test
    void listWithGrants_participantWithNoActiveAssignment_hasNullRoleAndNoGrants() {
        when(roomRepository.existsById(roomId)).thenReturn(true);
        RoomParticipant participant = participant("viewer-2");
        when(assignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(participant.getId()))
                .thenReturn(Optional.empty());
        when(roomParticipantRepository.findByRoomId(roomId)).thenReturn(List.of(participant));

        List<RoomParticipantWithGrantsResponse> result = service.listWithGrants(roomId);

        assertThat(result).hasSize(1);
        RoomParticipantWithGrantsResponse response = result.get(0);
        assertThat(response.activeRoomRoleId()).isNull();
        assertThat(response.activeShareRoleGrantIds()).isEmpty();

        org.mockito.Mockito.verify(shareRoleGrantRepository, org.mockito.Mockito.never())
                .findByRoomRoleIdAndRevokedAtIsNullAndShare_Status(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listWithGrants_unknownRoom_throwsIllegalArgumentException() {
        UUID unknownRoomId = UUID.randomUUID();
        when(roomRepository.existsById(unknownRoomId)).thenReturn(false);

        assertThatThrownBy(() -> service.listWithGrants(unknownRoomId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(unknownRoomId.toString());
    }

    private RoomParticipant participant(String livekitIdentity) {
        Room room = new Room();
        room.setId(roomId);
        RoomParticipant participant = new RoomParticipant();
        participant.setId(UUID.randomUUID());
        participant.setRoom(room);
        participant.setLivekitIdentity(livekitIdentity);
        participant.setDisplayName(livekitIdentity);
        return participant;
    }
}
