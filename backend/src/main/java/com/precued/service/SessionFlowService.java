package com.precued.service;

import com.precued.controller.dto.SessionFlowResponse;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomStage;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomRepository;
import com.precued.repository.RoomStageRepository;
import com.precued.repository.RoomStageRoleRepository;
import com.precued.security.CurrentParticipantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manual v1 Session Flow state machine. Persistence/snapshotting lives in
 * RoomService; this service only operates on Room/RoomStage runtime state.
 *
 * Timers are intentionally informational: a stage remains ACTIVE after
 * startedAt + durationSeconds until a host explicitly advances it.
 */
@Service
public class SessionFlowService {

    private final RoomRepository roomRepository;
    private final RoomStageRepository roomStageRepository;
    private final RoomStageRoleRepository roomStageRoleRepository;
    private final ParticipantRoleAssignmentRepository assignmentRepository;

    public SessionFlowService(
            RoomRepository roomRepository,
            RoomStageRepository roomStageRepository,
            RoomStageRoleRepository roomStageRoleRepository,
            ParticipantRoleAssignmentRepository assignmentRepository) {
        this.roomRepository = roomRepository;
        this.roomStageRepository = roomStageRepository;
        this.roomStageRoleRepository = roomStageRoleRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public SessionFlowResponse get(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("No Room with id " + roomId));
        List<RoomStage> stages = roomStageRepository.findByRoomIdOrderBySortOrder(roomId);
        return toResponse(room, stages);
    }

    /**
     * Starts the first configured stage. Locking the Room row serializes
     * concurrent start/advance requests for this Room.
     */
    @Transactional
    public SessionFlowResponse start(UUID roomId) {
        Room room = lockedRoom(roomId);
        requireHost(roomId);
        List<RoomStage> stages = roomStageRepository.findByRoomIdOrderBySortOrder(roomId);
        requireEnabledAndConfigured(room, stages);

        boolean alreadyStarted = stages.stream().anyMatch(stage -> stage.getStatus() != RoomStage.Status.PENDING);
        if (alreadyStarted) {
            throw new IllegalStateException("Session Flow has already started");
        }

        RoomStage first = stages.get(0);
        first.setStatus(RoomStage.Status.ACTIVE);
        first.setStartedAt(Instant.now());
        first.setCompletedAt(null);
        roomStageRepository.save(first);

        return toResponse(room, stages);
    }

    /**
     * Completes the current stage and activates the next one, or completes
     * the flow when the current stage is the final stage. No timer value can
     * call this implicitly; advancement is always host-driven in v1.
     */
    @Transactional
    public SessionFlowResponse advance(UUID roomId) {
        Room room = lockedRoom(roomId);
        requireHost(roomId);
        List<RoomStage> stages = roomStageRepository.findByRoomIdOrderBySortOrder(roomId);
        requireEnabledAndConfigured(room, stages);

        int activeIndex = -1;
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getStatus() == RoomStage.Status.ACTIVE) {
                activeIndex = i;
                break;
            }
        }

        if (activeIndex < 0) {
            boolean completed = stages.stream().allMatch(stage -> stage.getStatus() == RoomStage.Status.COMPLETED);
            if (completed) {
                throw new IllegalStateException("Session Flow is already completed");
            }
            throw new IllegalStateException("Session Flow has not started");
        }

        Instant now = Instant.now();
        RoomStage current = stages.get(activeIndex);
        current.setStatus(RoomStage.Status.COMPLETED);
        current.setCompletedAt(now);

        List<RoomStage> changed = new ArrayList<>();
        changed.add(current);

        if (activeIndex + 1 < stages.size()) {
            RoomStage next = stages.get(activeIndex + 1);
            next.setStatus(RoomStage.Status.ACTIVE);
            next.setStartedAt(now);
            next.setCompletedAt(null);
            changed.add(next);
        }

        roomStageRepository.saveAll(changed);
        return toResponse(room, stages);
    }

    private Room lockedRoom(UUID roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new IllegalArgumentException("No Room with id " + roomId));
    }

    private void requireEnabledAndConfigured(Room room, List<RoomStage> stages) {
        if (!room.isSessionFlowEnabled()) {
            throw new IllegalStateException("Session Flow is disabled for this room");
        }
        if (stages.isEmpty()) {
            throw new IllegalStateException("Session Flow is not configured for this room");
        }
    }

    /** Host authorization is semantic: active RoomRole assignment + is_host_role, not access_level. */
    private void requireHost(UUID roomId) {
        RoomParticipant participant = CurrentParticipantContext.get();
        if (!participant.getRoom().getId().equals(roomId)) {
            throw new IllegalStateException("Not a participant in that room");
        }

        ParticipantRoleAssignment assignment = assignmentRepository
                .findByRoomParticipantIdAndRevokedAtIsNull(participant.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Only a participant holding the host role can control Session Flow"));

        if (!assignment.getRoomRole().isHostRole()
                || !assignment.getRoomRole().getRoom().getId().equals(roomId)) {
            throw new IllegalStateException(
                    "Only a participant holding the host role can control Session Flow");
        }
    }

    private SessionFlowResponse toResponse(Room room, List<RoomStage> stages) {
        SessionFlowResponse.FlowStatus flowStatus = deriveStatus(room, stages);
        UUID currentStageId = stages.stream()
                .filter(stage -> stage.getStatus() == RoomStage.Status.ACTIVE)
                .map(RoomStage::getId)
                .findFirst()
                .orElse(null);

        List<SessionFlowResponse.Stage> stageResponses = stages.stream()
                .map(stage -> new SessionFlowResponse.Stage(
                        stage.getId(),
                        stage.getStageKey(),
                        stage.getName(),
                        stage.getSortOrder(),
                        stage.getDurationSeconds(),
                        stage.getStatus(),
                        stage.getStartedAt(),
                        stage.getCompletedAt(),
                        roomStageRoleRepository.findByRoomStageId(stage.getId()).stream()
                                .map(stageRole -> stageRole.getRoomRole().getId())
                                .toList()))
                .toList();

        return new SessionFlowResponse(
                room.getId(),
                room.isSessionFlowEnabled(),
                flowStatus,
                currentStageId,
                stageResponses);
    }

    private SessionFlowResponse.FlowStatus deriveStatus(Room room, List<RoomStage> stages) {
        if (!room.isSessionFlowEnabled()) {
            return SessionFlowResponse.FlowStatus.DISABLED;
        }
        if (stages.isEmpty()) {
            return SessionFlowResponse.FlowStatus.NOT_CONFIGURED;
        }
        if (stages.stream().anyMatch(stage -> stage.getStatus() == RoomStage.Status.ACTIVE)) {
            return SessionFlowResponse.FlowStatus.IN_PROGRESS;
        }
        if (stages.stream().allMatch(stage -> stage.getStatus() == RoomStage.Status.COMPLETED)) {
            return SessionFlowResponse.FlowStatus.COMPLETED;
        }
        return SessionFlowResponse.FlowStatus.NOT_STARTED;
    }
}
