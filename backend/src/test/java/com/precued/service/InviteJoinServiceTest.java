package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.Invite;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.repository.InviteRepository;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteJoinServiceTest {

    @Mock private InviteRepository inviteRepository;
    @Mock private RoomRoleRepository roomRoleRepository;
    @Mock private RoomParticipantRepository participantRepository;
    @Mock private ParticipantRoleAssignmentRepository assignmentRepository;
    @Mock private VisibilityEngine visibilityEngine;

    private InviteJoinService service;
    private Room room;
    private RoomRole role;

    @BeforeEach
    void setUp() {
        service = new InviteJoinService(
                inviteRepository, roomRoleRepository, participantRepository,
                assignmentRepository, visibilityEngine);

        room = new Room();
        room.setId(UUID.randomUUID());
        room.setStatus(Room.Status.CREATED);

        role = new RoomRole();
        role.setId(UUID.randomUUID());
        role.setRoom(room);
        role.setRoleKey("candidate");
        role.setName("Candidate");
        role.setMaxMembers(3);
    }

    @Test
    void joinNamedInvite_createsParticipantAndHistoricalAssignmentThenConsumesInvite() {
        Invite invite = usableInvite(Invite.Mode.NAMED, 1, 0);
        when(inviteRepository.findByTokenForUpdate("named-token")).thenReturn(Optional.of(invite));
        when(roomRoleRepository.findByIdForUpdate(role.getId())).thenReturn(Optional.of(role));
        when(assignmentRepository.countByRoomRoleIdAndRevokedAtIsNull(role.getId())).thenReturn(0L);
        when(participantRepository.save(any(RoomParticipant.class))).thenAnswer(invocation -> {
            RoomParticipant participant = invocation.getArgument(0);
            participant.setId(UUID.randomUUID());
            return participant;
        });
        when(assignmentRepository.save(any(ParticipantRoleAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inviteRepository.save(any(Invite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomParticipant participant = service.join(room.getId(), "named-token", "Taylor Candidate");

        assertThat(participant.getRoom()).isSameAs(room);
        assertThat(participant.getUser()).isNull();
        assertThat(participant.getDisplayName()).isEqualTo("Taylor Candidate");
        assertThat(participant.getSessionToken()).isNotBlank();

        ArgumentCaptor<ParticipantRoleAssignment> assignmentCaptor =
                ArgumentCaptor.forClass(ParticipantRoleAssignment.class);
        verify(assignmentRepository).save(assignmentCaptor.capture());
        ParticipantRoleAssignment assignment = assignmentCaptor.getValue();
        assertThat(assignment.getRoomParticipant()).isSameAs(participant);
        assertThat(assignment.getRoomRole()).isSameAs(role);
        assertThat(assignment.getInvite()).isSameAs(invite);

        assertThat(invite.getUsesCount()).isEqualTo(1);
        assertThat(invite.getStatus()).isEqualTo(Invite.Status.USED);
        verify(visibilityEngine).recomputeAndPushForRoom(room.getId());
    }

    @Test
    void joinPoolInvite_keepsInvitePendingUntilFinalUse() {
        Invite invite = usableInvite(Invite.Mode.POOL, 3, 1);
        when(inviteRepository.findByTokenForUpdate("pool-token")).thenReturn(Optional.of(invite));
        when(roomRoleRepository.findByIdForUpdate(role.getId())).thenReturn(Optional.of(role));
        when(assignmentRepository.countByRoomRoleIdAndRevokedAtIsNull(role.getId())).thenReturn(1L);
        when(participantRepository.save(any(RoomParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.save(any(ParticipantRoleAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inviteRepository.save(any(Invite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.join(room.getId(), "pool-token", "Second Candidate");

        assertThat(invite.getUsesCount()).isEqualTo(2);
        assertThat(invite.getStatus()).isEqualTo(Invite.Status.PENDING);
    }

    @Test
    void joinInvite_rejectsWhenRoleIsAtCapacity() {
        Invite invite = usableInvite(Invite.Mode.POOL, 3, 0);
        when(inviteRepository.findByTokenForUpdate("pool-token")).thenReturn(Optional.of(invite));
        when(roomRoleRepository.findByIdForUpdate(role.getId())).thenReturn(Optional.of(role));
        when(assignmentRepository.countByRoomRoleIdAndRevokedAtIsNull(role.getId())).thenReturn(3L);

        assertThatThrownBy(() -> service.join(room.getId(), "pool-token", "Fourth Candidate"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capacity");

        verify(participantRepository, never()).save(any());
        assertThat(invite.getUsesCount()).isZero();
    }

    @Test
    void joinInvite_rejectsExpiredTokenBeforeCreatingParticipant() {
        Invite invite = usableInvite(Invite.Mode.NAMED, 1, 0);
        invite.setExpiresAt(Instant.now().minusSeconds(5));
        when(inviteRepository.findByTokenForUpdate("named-token")).thenReturn(Optional.of(invite));
        when(inviteRepository.save(any(Invite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.join(room.getId(), "named-token", "Late Candidate"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");

        assertThat(invite.getStatus()).isEqualTo(Invite.Status.EXPIRED);
        verify(participantRepository, never()).save(any());
    }

    @Test
    void joinInvite_rejectsTokenForDifferentRoom() {
        Invite invite = usableInvite(Invite.Mode.NAMED, 1, 0);
        when(inviteRepository.findByTokenForUpdate("named-token")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> service.join(UUID.randomUUID(), "named-token", "Wrong Room"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("room");
    }

    private Invite usableInvite(Invite.Mode mode, int maxUses, int usesCount) {
        Invite invite = new Invite();
        invite.setId(UUID.randomUUID());
        invite.setRoomRole(role);
        invite.setToken(mode == Invite.Mode.NAMED ? "named-token" : "pool-token");
        invite.setMode(mode);
        invite.setMaxUses(maxUses);
        invite.setUsesCount(usesCount);
        invite.setStatus(Invite.Status.PENDING);
        invite.setCreatedAt(Instant.now().minusSeconds(5));
        invite.setExpiresAt(Instant.now().plusSeconds(3600));
        return invite;
    }
}
