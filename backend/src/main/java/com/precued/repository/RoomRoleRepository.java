package com.precued.repository;

import com.precued.entity.RoomRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRoleRepository extends JpaRepository<RoomRole, UUID> {
    List<RoomRole> findByRoomId(UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rr from RoomRole rr join fetch rr.room where rr.id = :id")
    Optional<RoomRole> findByIdForUpdate(@Param("id") UUID id);
}
