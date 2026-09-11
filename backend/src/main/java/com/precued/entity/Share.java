package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Business concept — one instance of a host sharing something.
 * One Share can produce more than one ShareTrack (video + audio).
 * Exactly one publisher per Share: if that RoomParticipant disconnects,
 * this Share transitions to ENDED (see ShareDisconnectListener) rather
 * than being orphaned or reassigned.
 */
@Entity
@Table(name = "share")
@Getter
@Setter
public class Share {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** Must hold a host role. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_participant_id", nullable = false)
    private RoomParticipant publisher;

    /** Which preset the host picked, for reference only. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_preset_id")
    private TemplatePreset appliedPreset;

    @Column(nullable = false)
    private String label; // host-entered, e.g. "Exhibit A"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    /**
     * NEW (Chunk 1, Precued_DataModel.md "Presentations Feature"). Existing
     * rows/callers get SCREEN by default — unaffected.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind = Kind.SCREEN;

    /**
     * NEW (Chunk 1). Only meaningful when kind = PRESENTATION — the
     * presenter's single live slide position, evaluated against every
     * connected participant the same way (Decision #5).
     */
    @Column(name = "current_slide_index", nullable = false)
    private int currentSlideIndex = 0;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public enum Status { ACTIVE, ENDED }

    public enum Kind { SCREEN, PRESENTATION }
}
