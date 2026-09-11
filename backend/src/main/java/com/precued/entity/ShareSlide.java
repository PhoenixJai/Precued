package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * NEW (Chunk 1, Precued_DataModel.md "Presentations Feature"). One slide
 * belonging to a PRESENTATION-kind Share; no rows exist for SCREEN-kind
 * Shares. Has zero relationship to ShareTrack — a presentation Share's
 * actual LiveKit media (if any, e.g. presenter audio) is still tracked via
 * ShareTrack exactly as today. This table only carries the visual slide
 * content and its per-slide visibility hook (ShareRoleGrant.shareSlide).
 */
@Entity
@Table(name = "share_slide")
@Getter
@Setter
public class ShareSlide {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "share_id", nullable = false)
    private Share share;

    /** Unique per share_id — ordering position, matched against Share.currentSlideIndex. */
    @Column(name = "slide_index", nullable = false)
    private int slideIndex;

    /** Nullable in Chunk 1 (schema only, no real content); populated by the import pipeline later. */
    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
