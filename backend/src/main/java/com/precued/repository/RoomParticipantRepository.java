package com.precued.repository;

import com.precued.entity.RoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, UUID> {
    List<RoomParticipant> findByRoomId(UUID roomId);
    Optional<RoomParticipant> findByRoomIdAndLivekitIdentity(UUID roomId, String livekitIdentity);
}
