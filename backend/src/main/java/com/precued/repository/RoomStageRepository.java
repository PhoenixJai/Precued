package com.precued.repository;

import com.precued.entity.RoomStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomStageRepository extends JpaRepository<RoomStage, UUID> {
    List<RoomStage> findByRoomIdOrderBySortOrder(UUID roomId);
}
