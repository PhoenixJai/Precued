package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Runtime snapshot of a TemplateStage. Live progression reads this row, not mutable TemplateStage config. */
@Entity
@Table(name = "room_stage")
@Getter
@Setter
public class RoomStage {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** Traceability only — never a live dependency. May become null if the source TemplateStage is deleted. */
    @Column(name = "source_template_stage_id")
    private UUID sourceTemplateStageId;

    @Column(name = "stage_key", nullable = false)
    private String stageKey;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Null means untimed. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public enum Status { PENDING, ACTIVE, COMPLETED }
}
