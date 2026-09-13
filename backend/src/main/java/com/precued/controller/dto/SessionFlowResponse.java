package com.precued.controller.dto;

import com.precued.entity.RoomStage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Runtime Session Flow snapshot returned to every participant in a Room.
 * Timers are derived by clients from startedAt + durationSeconds; the server
 * never mutates stage state merely because a duration elapsed.
 */
public record SessionFlowResponse(
        UUID roomId,
        boolean enabled,
        FlowStatus status,
        UUID currentStageId,
        List<Stage> stages) {

    public enum FlowStatus {
        DISABLED,
        NOT_CONFIGURED,
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED
    }

    public record Stage(
            UUID id,
            String stageKey,
            String name,
            int sortOrder,
            Integer durationSeconds,
            RoomStage.Status status,
            Instant startedAt,
            Instant completedAt,
            List<UUID> roomRoleIds) {}
}
