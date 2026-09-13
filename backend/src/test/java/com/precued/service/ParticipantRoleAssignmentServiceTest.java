package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.User;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRoleRepository;
import com.precued.security.CurrentParticipantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PR 9 makes InviteJoinService the only initial assignment path for guest
 * roles. The legacy self-assign endpoint remains only for Account Holder host
 * bootstrap, so possessing a participant token cannot bypass invite/capacity
 * enforcement by choosing an arbitrary non-host RoomRole afterward.
 */
@ExtendWith(MockitoExtension.class)
class ParticipantRoleAssignmentServiceTest {

    @Mock private ParticipantRoleAssignmentRepository assignmentRepository;
    @Mock private RoomParticipantRepository roomParticipantRepository;
    @Mock private RoomRoleRepository roomRoleRepository;
    @Mock private VisibilityEngine engine;

    private ParticipantRoleAssignmentService service;

    private final UUID roomId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();

    @BeforeEach
    void authenticateAsSelf() {
        RoomParticipant self = new RoomParticipant();
        self.setId(participantId);
        CurrentParticipantContext.set(self);
    }

    @AfterEach
    void clearAuthentication() {
        CurrentParticipantContext.clear();
    }

    private RoomParticipant participantWithUser(User user) {
        Room room = new Room();
        room.setId(roomId);
        RoomParticipant participant = new RoomParticipant();
        participant.setId(participantId);
        participant.setRoom(room);
        participant.setUser(user);
        return participant;
    }

    private RoomRole roleInRoom(boolean hostRole) {
        Room room = new Room();
        room.setId(roomId);
        RoomRole role = new RoomRole();
        role.setId(roleId);
        role.setRoom(room);
        role.setHostRole(hostRole);
        return role;
    }

    @Test
    void assign_hostRoleWithoutLinkedUser_rejectsAndDoesNotCreateAssignment() {
        service = new ParticipantRoleAssignmentService(
                assignmentRepository, roomParticipantRepository, roomRoleRepository, engine);

        RoomParticipant guest = participantWithUser(null);
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.of(guest));
        RoomRole hostRole = roleInRoom(true);
        when(roomRoleRepository.findByIdForUpdate(roleId)).thenReturn(Optional.of(hostRole));

        assertThatThrownBy(() -> service.assign(participantId, roleId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no linked User")
                .hasMessageContaining("host role");

        verify(assignmentRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForRoom(any());
    }

    @Test
    void assign_hostRoleWithLinkedUser_succeeds() {
        service = new ParticipantRoleAssignmentService(
                assignmentRepository, roomParticipantRepository, roomRoleRepository, engine);

        User user = new User();
        user.setId(UUID.randomUUID());
        RoomParticipant host = participantWithUser(user);
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.of(host));
        RoomRole hostRole = roleInRoom(true);
        when(roomRoleRepository.findByIdForUpdate(roleId)).thenReturn(Optional.of(hostRole));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.assign(participantId, roleId)).isNotNull();

        verify(engine).recomputeAndPushForRoom(roomId);
    }

    @Test
    void assign_hostRoleAtCapacity_isRejected() {
        service = new ParticipantRoleAssignmentService(
                assignmentRepository, roomParticipantRepository, roomRoleRepository, engine);

        User user = new User();
        user.setId(UUID.randomUUID());
        RoomParticipant host = participantWithUser(user);
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.of(host));
        RoomRole hostRole = roleInRoom(true);
        hostRole.setMaxMembers(1);
        when(roomRoleRepository.findByIdForUpdate(roleId)).thenReturn(Optional.of(hostRole));
        when(assignmentRepository.countByRoomRoleIdAndRevokedAtIsNull(roleId)).thenReturn(1L);

        assertThatThrownBy(() -> service.assign(participantId, roleId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capacity");

        verify(assignmentRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForRoom(any());
    }

    @Test
    void assign_nonHostRoleDirectly_isRejected_guestMustUseInviteJoin() {
        service = new ParticipantRoleAssignmentService(
                assignmentRepository, roomParticipantRepository, roomRoleRepository, engine);

        RoomParticipant guest = participantWithUser(null);
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.of(guest));
        RoomRole memberRole = roleInRoom(false);
        when(roomRoleRepository.findByIdForUpdate(roleId)).thenReturn(Optional.of(memberRole));

        assertThatThrownBy(() -> service.assign(participantId, roleId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invite");

        verify(assignmentRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForRoom(any());
    }

    @Test
    void assign_roleFromAnotherRoom_isRejected() {
        service = new ParticipantRoleAssignmentService(
                assignmentRepository, roomParticipantRepository, roomRoleRepository, engine);

        User user = new User();
        user.setId(UUID.randomUUID());
        RoomParticipant host = participantWithUser(user);
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.of(host));

        Room otherRoom = new Room();
        otherRoom.setId(UUID.randomUUID());
        RoomRole hostRole = new RoomRole();
        hostRole.setId(roleId);
        hostRole.setRoom(otherRoom);
        hostRole.setHostRole(true);
        when(roomRoleRepository.findByIdForUpdate(roleId)).thenReturn(Optional.of(hostRole));

        assertThatThrownBy(() -> service.assign(participantId, roleId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same room");

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assign_toAnotherParticipant_rejectsAndDoesNotCreateAssignment() {
        service = new ParticipantRoleAssignmentService(
                assignmentRepository, roomParticipantRepository, roomRoleRepository, engine);

        RoomParticipant target = participantWithUser(null);
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.of(target));
        RoomRole role = roleInRoom(true);
        when(roomRoleRepository.findByIdForUpdate(roleId)).thenReturn(Optional.of(role));

        RoomParticipant someoneElse = new RoomParticipant();
        someoneElse.setId(UUID.randomUUID());
        CurrentParticipantContext.set(someoneElse);

        assertThatThrownBy(() -> service.assign(participantId, roleId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot assign a role to another participant");

        verify(assignmentRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForRoom(any());
    }
}
