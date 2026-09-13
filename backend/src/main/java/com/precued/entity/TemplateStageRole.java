package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Join table describing which TemplateRoles have the floor / are active during a TemplateStage. */
@Entity
@Table(name = "template_stage_role")
@Getter
@Setter
public class TemplateStageRole {

    @EmbeddedId
    private Id id = new Id();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("templateStageId")
    @JoinColumn(name = "template_stage_id", nullable = false)
    private TemplateStage templateStage;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("templateRoleId")
    @JoinColumn(name = "template_role_id", nullable = false)
    private TemplateRole templateRole;

    @Embeddable
    @Getter
    @Setter
    public static class Id implements Serializable {
        @Column(name = "template_stage_id")
        private UUID templateStageId;

        @Column(name = "template_role_id")
        private UUID templateRoleId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(templateStageId, id.templateStageId)
                    && Objects.equals(templateRoleId, id.templateRoleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(templateStageId, templateRoleId);
        }
    }
}
