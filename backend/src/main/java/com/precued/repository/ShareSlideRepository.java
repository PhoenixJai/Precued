package com.precued.repository;

import com.precued.entity.ShareSlide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShareSlideRepository extends JpaRepository<ShareSlide, UUID> {
    Optional<ShareSlide> findByShareIdAndSlideIndex(UUID shareId, int slideIndex);
}
