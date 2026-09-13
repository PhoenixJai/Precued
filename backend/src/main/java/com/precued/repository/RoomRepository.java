package com.precued.repository;

import com.precued.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByLivekitRoomName(String livekitRoomName);

    /** RoomResponse exposes the readable Template name, so fetch that LAZY
     * association while the repository session is open instead of relying on
     * open-in-view (which is intentionally disabled). */
    @Query("select r from Room r join fetch r.template where r.id = :id")
    Optional<Room> findByIdWithTemplate(@Param("id") UUID id);

    /**
     * Serializes Session Flow state transitions per Room. The database also
     * has a partial unique index preventing two ACTIVE RoomStages, but this
     * lock prevents two simultaneous advance requests from both observing
     * the same ACTIVE stage and advancing twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") UUID id);
}
