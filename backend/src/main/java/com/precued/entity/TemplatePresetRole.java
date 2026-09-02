package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Join table: which TemplateRoles a TemplatePreset grants.
 * Composite PK (preset_id, template_role_id) — this is the one place
 * Postgres's native composite-PK support (vs. MySQL) actually mattered.
 */
@Entity
@Table(name = "template_preset_role")
@Getter
@Setter
public class TemplatePresetRole {

    @EmbeddedId
    private Id id = new Id();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("presetId")
    @JoinColumn(name = "preset_id", nullable = false)
    private TemplatePreset preset;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("templateRoleId")
    @JoinColumn(name = "template_role_id", nullable = false)
    private TemplateRole templateRole;

    @Embeddable
    @Getter
    @Setter
    public static class Id implements Serializable {
        @Column(name = "preset_id")
        private UUID presetId;

        @Column(name = "template_role_id")
        private UUID templateRoleId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(presetId, id.presetId) && Objects.equals(templateRoleId, id.templateRoleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(presetId, templateRoleId);
        }
    }
}
