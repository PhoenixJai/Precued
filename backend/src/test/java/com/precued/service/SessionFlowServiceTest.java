package com.precued.service;

import com.precued.controller.dto.SessionFlowResponse;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.RoomStage;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomRepository;
import com.precued.repository.RoomStageRepository;
import com.precued.repository.RoomStageRoleRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionFlowServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomStageRepository roomStageRepository;
    @Mock private RoomStageRoleRepository roomStageRoleRepository;
    @Mock private ParticipantRoleAssignmentRepository assignmentRepository;

    private SessionFlowService service;
    private Room room;
    private RoomParticipant participant;
    private RoomRole hostRole;

    @BeforeEach
    void setUp() {
        service = new SessionFlowService(
                roomRepository,
                roomStageRepository,
                roomStageRoleRepository,
                assignmentRepository);

        room = new Room();
        room.setId(UUID.randomUUID());
        room.setSessionFlowEnabled(true);

        participant = new RoomParticipant();
        participant.setId(UUID.randomUUID());
        participant.setRoom(room);
        participant.setDisplayName("Host");
        CurrentParticipantContext.set(participant);

        hostRole = new RoomRole();
        hostRole.setId(UUID.randomUUID());
        hostRole.setRoom(room);
        hostRole.setHostRole(true);
        hostRole.setName("Judge");
    }

    @AfterEach
    void clearContext() {
        CurrentParticipantContext.clear();
    }

    @Test
    void get_disabledRoom_reportsDisabledEvenWhenSavedStagesExist() {
        room.setSessionFlowEnabled(false);
        RoomStage saved = stage("opening", 0, RoomStage.Status.PENDING, 120);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomStageRepository.findByRoomIdOrderBySortOrder(room.getId())).thenReturn(List.of(saved));
        when(roomStageRoleRepository.findByRoomStageId(saved.getId())).thenReturn(List.of());

        SessionFlowResponse response = service.get(room.getId());

        assertThat(response.status()).isEqualTo(SessionFlowResponse.FlowStatus.DISABLED);
        assertThat(response.enabled()).isFalse();
        assertThat(response.currentStageId()).isNull();
        assertThat(response.stages()).hasSize(1);
    }

    @Test
    void get_enabledRoomWithNoStages_reportsNotConfigured() {
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomStageRepository.findByRoomIdOrderBySortOrder(room.getId())).thenReturn(List.of());

        SessionFlowResponse response = service.get(room.getId());

        assertThat(response.status()).isEqualTo(SessionFlowResponse.FlowStatus.NOT_CONFIGURED);
        assertThat(response.stages()).isEmpty();
    }

    @Test
    void get_pendingStages_reportsNotStarted() {
        RoomStage first = stage("opening", 0, RoomStage.Status.PENDING, 120);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomStageRepository.findByRoomIdOrderBySortOrder(room.getId())).thenReturn(List.of(first));
        when(roomStageRoleRepository.findByRoomStageId(first.getId())).thenReturn(List.of());

        assertThat(service.get(room.getId()).status())
                .isEqualTo(SessionFlowResponse.FlowStatus.NOT_STARTED);
    }

    @Test
    void get_activeStage_reportsInProgressAndDoesNotAutoAdvanceExpiredTimer() {
        RoomStage first = stage("opening", 0, RoomStage.Status.ACTIVE, 10);
        first.setStartedAt(Instant.now().minusSeconds(60));
        RoomStage second = stage("closing", 1, RoomStage.Status.PENDING, null);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomStageRepository.findByRoomIdOrderBySortOrder(room.getId())).thenReturn(List.of(first, second));
        when(roomStageRoleRepository.findByRoomStageId(first.getId())).thenReturn(List.of());
        when(roomStageRoleRepository.findByRoomStageId(second.getId())).thenReturn(List.of());

        SessionFlowResponse response = service.get(room.getId());

        assertThat(response.status()).isEqualTo(SessionFlowResponse.FlowStatus.IN_PROGRESS);
        assertThat(response.currentStageId()).isEqualTo(first.getId());
        assertThat(first.getStatus()).isEqualTo(RoomStage.Status.ACTIVE);
        assertThat(second.getStatus()).isEqualTo(RoomStage.Status.PENDING);
        verify(roomStageRepository, never()).save(any());
    }

    @Test
    void get_allCompletedStages_reportsCompleted() {
        RoomStage first = stage("opening", 0, RoomStage.Status.COMPLETED, 120);
        RoomStage second = stage("closing", 1, RoomStage.Status.COMPLETED, null);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomStageRepository.findByRoomIdOrderBySortOrder(room.getId())).thenReturn(List.of(first, second));
        when(roomStageRoleRepository.findByRoomStageId(first.getId())).thenReturn(List.of());
        when(roomStageRoleRepository.findByRoomStageId(second.getId())).thenReturn(List.of());

        assertThat(service.get(room.getId()).status())
                .isEqualTo(SessionFlowResponse.FlowStatus.COMPLETED);
    }

    @Test
    void start_hostActivatesFirstPendingStageUsingServerTimestamp() {
        RoomStage first = stage("opening", 0, RoomStage.Status.PENDING, 120);
        RoomStage second = stage("closing", 1, RoomStage.Status.PENDING, null);
        stubLockedRoomWithHost(List.of(first, second));
        when(roomStageRoleRepository.findByRoomStageId(first.getId())).thenReturn(List.of());
        when(roomStageRoleRepository.findByRoomStageId(second.getId())).thenReturn(List.of());

        Instant before = Instant.now();
        SessionFlowResponse response = service.start(room.getId());
        Instant after = Instant.now();

        assertThat(first.getStatus()).isEqualTo(RoomStage.Status.ACTIVE);
        assertThat(first.getStartedAt()).isBetween(before, after);
        assertThat(first.getCompletedAt()).isNull();
        assertThat(second.getStatus()).isEqualTo(RoomStage.Status.PENDING);
        assertThat(response.status()).isEqualTo(SessionFlowResponse.FlowStatus.IN_PROGRESS);
        assertThat(response.currentStageId()).isEqualTo(first.getId());
        verify(roomRepository).findByIdForUpdate(room.getId());
        verify(roomStageRepository).save(first);
    }

    @Test
    void start_nonHostIsRejected() {
        hostRole.setHostRole(false);
        RoomStage first = stage("opening", 0, RoomStage.Status.PENDING, 120);
        stubLockedRoomWithHost(List.of(first));

        assertThatThrownBy(() -> service.start(room.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a participant holding the host role can control Session Flow");

        verify(roomStageRepository, never()).save(any());
    }

    @Test
    void start_disabledFlowIsRejected() {
        room.setSessionFlowEnabled(false);
        RoomStage first = stage("opening", 0, RoomStage.Status.PENDING, 120);
        stubLockedRoomWithHost(List.of(first));

        assertThatThrownBy(() -> service.start(room.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session Flow is disabled for this room");
    }

    @Test
    void start_enabledFlowWithNoStagesIsRejected() {
        stubLockedRoomWithHost(List.of());

        assertThatThrownBy(() -> service.start(room.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session Flow is not configured for this room");
    }

    @Test
    void start_afterFlowAlreadyStartedIsRejected() {
        RoomStage first = stage("opening", 0, RoomStage.Status.ACTIVE, 120);
        first.setStartedAt(Instant.now());
        stubLockedRoomWithHost(List.of(first));

        assertThatThrownBy(() -> service.start(room.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session Flow has already started");
    }

    @Test
    void advance_completesCurrentStageAndActivatesNextStageAtomically() {
        RoomStage first = stage("opening", 0, RoomStage.Status.ACTIVE, 120);
        first.setStartedAt(Instant.now().minusSeconds(30));
        RoomStage second = stage("questions", 1, RoomStage.Status.PENDING, null);
        stubLockedRoomWithHost(List.of(first, second));
        when(roomStageRoleRepository.findByRoomStageId(first.getId())).thenReturn(List.of());
        when(roomStageRoleRepository.findByRoomStageId(second.getId())).thenReturn(List.of());

        Instant before = Instant.now();
        SessionFlowResponse response = service.advance(room.getId());
        Instant after = Instant.now();

        assertThat(first.getStatus()).isEqualTo(RoomStage.Status.COMPLETED);
        assertThat(first.getCompletedAt()).isBetween(before, after);
        assertThat(second.getStatus()).isEqualTo(RoomStage.Status.ACTIVE);
        assertThat(second.getStartedAt()).isBetween(before, after);
        assertThat(response.status()).isEqualTo(SessionFlowResponse.FlowStatus.IN_PROGRESS);
        assertThat(response.currentStageId()).isEqualTo(second.getId());
        verify(roomRepository).findByIdForUpdate(room.getId());
        verify(roomStageRepository).saveAll(List.of(first, second));
    }

    @Test
    void advance_finalStageCompletesFlowWithoutCreatingAnotherActiveStage() {
        RoomStage only = stage("verdict", 0, RoomStage.Status.ACTIVE, null);
        only.setStartedAt(Instant.now().minusSeconds(5));
        stubLockedRoomWithHost(List.of(only));
        when(roomStageRoleRepository.findByRoomStageId(only.getId())).thenReturn(List.of());

        SessionFlowResponse response = service.advance(room.getId());

        assertThat(only.getStatus()).isEqualTo(RoomStage.Status.COMPLETED);
        assertThat(only.getCompletedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(SessionFlowResponse.FlowStatus.COMPLETED);
        assertThat(response.currentStageId()).isNull();
        verify(roomStageRepository).saveAll(List.of(only));
    }

    @Test
    void advance_beforeStartIsRejected() {
        RoomStage first = stage("opening", 0, RoomStage.Status.PENDING, 120);
        stubLockedRoomWithHost(List.of(first));

        assertThatThrownBy(() -> service.advance(room.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session Flow has not started");
    }

    @Test
    void advance_afterCompletionIsRejected() {
        RoomStage first = stage("opening", 0, RoomStage.Status.COMPLETED, 120);
        stubLockedRoomWithHost(List.of(first));

        assertThatThrownBy(() -> service.advance(room.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session Flow is already completed");
    }

    private void stubLockedRoomWithHost(List<RoomStage> stages) {
        when(roomRepository.findByIdForUpdate(room.getId())).thenReturn(Optional.of(room));
        when(roomStageRepository.findByRoomIdOrderBySortOrder(room.getId())).thenReturn(stages);

        ParticipantRoleAssignment assignment = new ParticipantRoleAssignment();
        assignment.setRoomParticipant(participant);
        assignment.setRoomRole(hostRole);
        when(assignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(participant.getId()))
                .thenReturn(Optional.of(assignment));
    }

    private RoomStage stage(String key, int sortOrder, RoomStage.Status status, Integer durationSeconds) {
        RoomStage stage = new RoomStage();
        stage.setId(UUID.randomUUID());
        stage.setRoom(room);
        stage.setStageKey(key);
        stage.setName(key);
        stage.setSortOrder(sortOrder);
        stage.setStatus(status);
        stage.setDurationSeconds(durationSeconds);
        return stage;
    }
}
