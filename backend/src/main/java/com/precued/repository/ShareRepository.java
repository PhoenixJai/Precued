package com.precued.repository;

import com.precued.entity.Share;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShareRepository extends JpaRepository<Share, UUID> {
    List<Share> findByRoomIdAndStatus(UUID roomId, Share.Status status);
    List<Share> findByPublisherIdAndStatus(UUID publisherId, Share.Status status);
}
