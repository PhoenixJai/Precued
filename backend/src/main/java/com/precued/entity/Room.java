package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room")
@Getter
@Setter
public class Room {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(name = "livekit_room_name", nullable = false, unique = true)
    private String livekitRoomName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.CREATED;

    /**
     * Host-set at room creation. Governs what happens when the connected
     * RoomParticipant holding access_level=host disconnects.
     * end_call -> Room.status = ended immediately.
     * persist_indefinitely -> room stays open, no host, until manually ended
     *   or someone is granted host.
     * persist_for_duration -> room stays open for hostDisconnectGraceSeconds,
     *   then auto-transitions to ended if no host reconnects.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "host_disconnect_policy", nullable = false, length = 32)
    private HostDisconnectPolicy hostDisconnectPolicy = HostDisconnectPolicy.END_CALL;

    @Column(name = "host_disconnect_grace_seconds")
    private Integer hostDisconnectGraceSeconds; // only used when policy = PERSIST_FOR_DURATION

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public enum Status { CREATED, ACTIVE, ENDED }

    public enum HostDisconnectPolicy { END_CALL, PERSIST_INDEFINITELY, PERSIST_FOR_DURATION }
}
