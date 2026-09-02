package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** The actual LiveKit track(s) underneath a Share. */
@Entity
@Table(name = "share_track")
@Getter
@Setter
public class ShareTrack {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "share_id", nullable = false)
    private Share share;

    @Column(name = "livekit_track_sid", nullable = false, unique = true)
    private String livekitTrackSid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Kind kind;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "unpublished_at")
    private Instant unpublishedAt;

    public enum Kind { VIDEO, AUDIO }
}
