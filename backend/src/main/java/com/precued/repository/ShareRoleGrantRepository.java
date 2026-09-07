package com.precued.repository;

import com.precued.entity.ShareRoleGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareRoleGrantRepository extends JpaRepository<ShareRoleGrant, UUID> {
    List<ShareRoleGrant> findByShareIdAndRevokedAtIsNull(UUID shareId);
    Optional<ShareRoleGrant> findByShareIdAndRoomRoleIdAndRevokedAtIsNull(UUID shareId, UUID roomRoleId);
}
