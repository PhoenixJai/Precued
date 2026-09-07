package com.precued.repository;

import com.precued.entity.RoomRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomRoleRepository extends JpaRepository<RoomRole, UUID> {
    List<RoomRole> findByRoomId(UUID roomId);
}
