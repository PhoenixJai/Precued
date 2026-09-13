package com.precued.repository;

import com.precued.entity.RoomStageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomStageRoleRepository
        extends JpaRepository<RoomStageRole, RoomStageRole.Id> {
    List<RoomStageRole> findByRoomStageId(UUID roomStageId);
}
