package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Copied from TemplateRole when the Room is created — NOT a live reference.
 * Editing a TemplateRole later never corrupts a call already in progress.
 */
@Entity
@Table(name = "room_role")
@Getter
@Setter
public class RoomRole {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** Traceability only — not a live dependency. May outlive the source row. */
    @Column(name = "source_template_role_id")
    private UUID sourceTemplateRoleId;

    @Column(name = "role_key", nullable = false)
    private String roleKey;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_host_role", nullable = false)
    private boolean isHostRole;

    /**
     * Snapshotted from TemplateRole so runtime role-assignment UI can preserve
     * the template's guest-role hint without reading mutable config-time rows.
     */
    @Column(name = "is_guest_role", nullable = false)
    private boolean isGuestRole;

    @Column(name = "max_members")
    private Integer maxMembers;
}
