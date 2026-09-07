package com.precued.repository;

import com.precued.entity.ShareTrack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareTrackRepository extends JpaRepository<ShareTrack, UUID> {
    List<ShareTrack> findByShareId(UUID shareId);
    Optional<ShareTrack> findByLivekitTrackSid(String livekitTrackSid);
}
