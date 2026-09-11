package com.precued.repository;

import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareRoleGrantRepository extends JpaRepository<ShareRoleGrant, UUID> {
    List<ShareRoleGrant> findByShareIdAndRevokedAtIsNull(UUID shareId);

    /**
     * Screen-kind (whole-share model) lookup: at most one active grant is
     * ever assumed per (share, role) pair here — unchanged since before
     * Chunk 1. Do not reuse this for a presentation-kind Share, where a
     * role can legitimately hold several active grants at once (a
     * whole-share grant and/or several slide-specific ones) — use
     * {@link #findAllByShareIdAndRoomRoleIdAndRevokedAtIsNull} instead.
     */
    Optional<ShareRoleGrant> findByShareIdAndRoomRoleIdAndRevokedAtIsNull(UUID shareId, UUID roomRoleId);

    /**
     * NEW (Chunk 1). Every active grant for a (share, role) pair — needed
     * for the presentation Runtime Rule extension, where a role can hold
     * more than one active grant on the same Share (whole-share plus/or
     * several slide-specific grants).
     */
    List<ShareRoleGrant> findAllByShareIdAndRoomRoleIdAndRevokedAtIsNull(UUID shareId, UUID roomRoleId);

    /** Grants currently applicable to a role: active (not revoked) and on a still-active Share. */
    List<ShareRoleGrant> findByRoomRoleIdAndRevokedAtIsNullAndShare_Status(UUID roomRoleId, Share.Status status);
}
