package com.precued.service;

import com.precued.entity.Invite;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.repository.InviteRepository;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomRoleRepository;
import com.precued.security.CurrentParticipantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock private InviteRepository inviteRepository;
    @Mock private RoomRoleRepository roomRoleRepository;
    @Mock private ParticipantRoleAssignmentRepository assignmentRepository;

    private InviteService service;
    private Room room;
    private RoomRole hostRole;
    private RoomRole candidateRole;
    private RoomParticipant host;

    @BeforeEach
    void setUp() {
        service = new InviteService(inviteRepository, roomRoleRepository, assignmentRepository, 7);

        room = new Room();
        room.setId(UUID.randomUUID());
        room.setStatus(Room.Status.CREATED);

        hostRole = role("host", true, 1);
        candidateRole = role("candidate", false, 3);

        host = new RoomParticipant();
        host.setId(UUID.randomUUID());
        host.setRoom(room);
        CurrentParticipantContext.set(host);

        ParticipantRoleAssignment hostAssignment = new ParticipantRoleAssignment();
        hostAssignment.setRoomParticipant(host);
        hostAssignment.setRoomRole(hostRole);
        // Public resolve() intentionally needs no host context, so this common
        // fixture is lenient for that one test while remaining active for all
        // host-management operations.
        lenient().when(assignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(host.getId()))
                .thenReturn(Optional.of(hostAssignment));
    }

    @AfterEach
    void clearContext() {
        CurrentParticipantContext.clear();
    }

    @Test
    void createNamedInvite_tracksEmailAndUsesOneSeat() {
        when(roomRoleRepository.findByIdForUpdate(candidateRole.getId())).thenReturn(Optional.of(candidateRole));
        when(inviteRepository.findByRoomRoleId(candidateRole.getId())).thenReturn(List.of());
        when(assignmentRepository.countByRoomRoleIdAndRevokedAtIsNull(candidateRole.getId())).thenReturn(0L);
        when(inviteRepository.save(any(Invite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invite invite = service.create(
                room.getId(), candidateRole.getId(), Invite.Mode.NAMED,
                " Candidate@One.Example ", null, null);

        assertThat(invite.getInviteeEmail()).isEqualTo("candidate@one.example");
        assertThat(invite.getMode()).isEqualTo(Invite.Mode.NAMED);
        assertThat(invite.getMaxUses()).isEqualTo(1);
        assertThat(invite.getUsesCount()).isZero();
        assertThat(invite.getStatus()).isEqualTo(Invite.Status.PENDING);
        assertThat(invite.getToken()).isNotBlank();
        assertThat(invite.getExpiresAt()).isAfter(Instant.now().plusSeconds(6 * 24 * 60 * 60));
    }

    @Test
    void createPoolInvite_rejectsCapacityReservedByActiveAndPendingSeats() {
        when(roomRoleRepository.findByIdForUpdate(candidateRole.getId())).thenReturn(Optional.of(candidateRole));
        when(assignmentRepository.countByRoomRoleIdAndRevokedAtIsNull(candidateRole.getId())).thenReturn(1L);

        Invite pendingNamed = new Invite();
        pendingNamed.setRoomRole(candidateRole);
        pendingNamed.setMode(Invite.Mode.NAMED);
        pendingNamed.setMaxUses(1);
        pendingNamed.setUsesCount(0);
        pendingNamed.setStatus(Invite.Status.PENDING);
        pendingNamed.setExpiresAt(Instant.now().plusSeconds(3600));
        when(inviteRepository.findByRoomRoleId(candidateRole.getId())).thenReturn(List.of(pendingNamed));

        assertThatThrownBy(() -> service.create(
                room.getId(), candidateRole.getId(), Invite.Mode.POOL,
                null, 2, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capacity");

        verify(inviteRepository, never()).save(any());
    }

    @Test
    void createPoolInvite_forUnlimitedRole_requiresExplicitMaxUses() {
        candidateRole.setMaxMembers(null);
        when(roomRoleRepository.findByIdForUpdate(candidateRole.getId())).thenReturn(Optional.of(candidateRole));
        when(inviteRepository.findByRoomRoleId(candidateRole.getId())).thenReturn(List.of());
        when(assignmentRepository.countByRoomRoleIdAndRevokedAtIsNull(candidateRole.getId())).thenReturn(0L);

        assertThatThrownBy(() -> service.create(
                room.getId(), candidateRole.getId(), Invite.Mode.POOL,
                null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max uses");
    }

    @Test
    void createInvite_nonHostCaller_isRejected() {
        ParticipantRoleAssignment nonHostAssignment = new ParticipantRoleAssignment();
        nonHostAssignment.setRoomParticipant(host);
        nonHostAssignment.setRoomRole(candidateRole);
        when(assignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(host.getId()))
                .thenReturn(Optional.of(nonHostAssignment));

        assertThatThrownBy(() -> service.create(
                room.getId(), candidateRole.getId(), Invite.Mode.NAMED,
                "person@example.com", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("host");
    }

    @Test
    void expirePendingInvite_releasesItFromFutureCapacityReservations() {
        Invite invite = new Invite();
        invite.setId(UUID.randomUUID());
        invite.setRoomRole(candidateRole);
        invite.setStatus(Invite.Status.PENDING);
        invite.setMode(Invite.Mode.NAMED);
        invite.setMaxUses(1);
        invite.setUsesCount(0);
        when(inviteRepository.findById(invite.getId())).thenReturn(Optional.of(invite));
        when(inviteRepository.save(any(Invite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invite expired = service.expire(room.getId(), invite.getId());

        assertThat(expired.getStatus()).isEqualTo(Invite.Status.EXPIRED);
    }

    @Test
    void resolveExpiredInvite_marksItExpiredAndRejectsIt() {
        Invite invite = new Invite();
        invite.setToken("expired-token");
        invite.setRoomRole(candidateRole);
        invite.setStatus(Invite.Status.PENDING);
        invite.setMaxUses(1);
        invite.setExpiresAt(Instant.now().minusSeconds(1));
        when(inviteRepository.findByToken("expired-token")).thenReturn(Optional.of(invite));
        when(inviteRepository.save(any(Invite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.resolve("expired-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
        assertThat(invite.getStatus()).isEqualTo(Invite.Status.EXPIRED);
    }

    private RoomRole role(String key, boolean hostRoleFlag, Integer maxMembers) {
        RoomRole role = new RoomRole();
        role.setId(UUID.randomUUID());
        role.setRoom(room);
        role.setRoleKey(key);
        role.setName(key);
        role.setHostRole(hostRoleFlag);
        role.setMaxMembers(maxMembers);
        return role;
    }
}
