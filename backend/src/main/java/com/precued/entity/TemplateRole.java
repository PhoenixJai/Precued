package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "template_role")
@Getter
@Setter
public class TemplateRole {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(name = "role_key", nullable = false)
    private String roleKey; // e.g. "judge", "jury"

    @Column(nullable = false)
    private String name;

    @Column(name = "is_host_role", nullable = false)
    private boolean isHostRole;

    /**
     * Default false. Opt-in per template — role gets zero default
     * ShareRoleGrant, shown as optional/dimmed on the role-assignment screen.
     * No runtime logic change: ShareRoleGrant already defaults to
     * no-visibility for every role: this just makes that the intended
     * steady-state for one specific role instead of an oversight.
     */
    @Column(name = "is_guest_role", nullable = false)
    private boolean isGuestRole = false;

    @Column(name = "max_members")
    private Integer maxMembers; // null = unlimited (e.g. Jury, Audience)

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
