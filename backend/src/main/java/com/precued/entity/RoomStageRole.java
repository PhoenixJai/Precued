package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Runtime snapshot of TemplateStageRole, mapped onto this Room's RoomRole snapshots. */
@Entity
@Table(name = "room_stage_role")
@Getter
@Setter
public class RoomStageRole {

    @EmbeddedId
    private Id id = new Id();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("roomStageId")
    @JoinColumn(name = "room_stage_id", nullable = false)
    private RoomStage roomStage;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("roomRoleId")
    @JoinColumn(name = "room_role_id", nullable = false)
    private RoomRole roomRole;

    @Embeddable
    @Getter
    @Setter
    public static class Id implements Serializable {
        @Column(name = "room_stage_id")
        private UUID roomStageId;

        @Column(name = "room_role_id")
        private UUID roomRoleId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(roomStageId, id.roomStageId)
                    && Objects.equals(roomRoleId, id.roomRoleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomStageId, roomRoleId);
        }
    }
}
