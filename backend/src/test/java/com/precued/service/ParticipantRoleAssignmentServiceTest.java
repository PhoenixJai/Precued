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
 * Covers the host-role-requires-User enforcement added for hybrid auth
 * (Issue #1, resolved): magic-link auth is required for host-role
 * participants, so a host-role assignment must never succeed for a
 * RoomParticipant with no linked User (a guest). Non-host roles are
 * untouched by this check — guest join stays exactly as it was.
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
        // All three tests below have participantId assign their own role —
        // matches every real caller (self-assign only, enforced in the service).
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

    @Test
    void assign_hostRoleWithoutLinkedUser_rejectsAndDoesNotCreateAssignment() {
        service = new ParticipantRoleAssignmentService(
                assignmentRepository, roomParticipantRepository, roomRoleRepository, engine);

        RoomParticipant guest = participantWithUser(null);
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.of(guest));
        RoomRole hostRole = new RoomRole();
        hostRole.setId(roleId);
        hostRole.setHostRole(true);
        when(roomRoleRepository.findById(roleId)).thenReturn(Optional.of(hostRole));

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
        RoomRole hostRole = new RoomRole();
        hostRole.setId(roleId);
        hostRole.setHostRole(true);
        when(roomRoleRepository.findById(roleId)).thenReturn(Optional.of(hostRole));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.assign(participantId, roleId)).isNotNull();

        verify(engine).recomputeAndPushForRoom(roomId);
    }

    @Test
    void assign_nonHostRoleWithoutLinkedUser_stillSucceeds_guestJoinUnaffected() {
        service = new ParticipantRoleAssignmentService(
                assignmentRepository, roomParticipantRepository, roomRoleRepository, engine);

        RoomParticipant guest = participantWithUser(null);
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.of(guest));
        RoomRole memberRole = new RoomRole();
        memberRole.setId(roleId);
        memberRole.setHostRole(false);
        when(roomRoleRepository.findById(roleId)).thenReturn(Optional.of(memberRole));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.assign(participantId, roleId)).isNotNull();

        verify(engine).recomputeAndPushForRoom(roomId);
    }

    @Test
    void assign_toAnotherParticipant_rejectsAndDoesNotCreateAssignment() {
        service = new ParticipantRoleAssignmentService(
                assignmentRepository, roomParticipantRepository, roomRoleRepository, engine);

        RoomParticipant target = participantWithUser(null);
        when(roomParticipantRepository.findById(participantId)).thenReturn(Optional.of(target));
        RoomRole role = new RoomRole();
        role.setId(roleId);
        when(roomRoleRepository.findById(roleId)).thenReturn(Optional.of(role));

        // Authenticated as someone other than the target participantId.
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
