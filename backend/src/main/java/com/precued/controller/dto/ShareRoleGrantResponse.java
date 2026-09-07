package com.precued.controller.dto;

import com.precued.entity.ShareRoleGrant;

import java.time.Instant;
import java.util.UUID;

public record ShareRoleGrantResponse(
        UUID id, UUID shareId, UUID roomRoleId, Instant grantedAt, Instant revokedAt) {

    public static ShareRoleGrantResponse from(ShareRoleGrant grant) {
        return new ShareRoleGrantResponse(
                grant.getId(),
                grant.getShare().getId(),
                grant.getRoomRole().getId(),
                grant.getGrantedAt(),
                grant.getRevokedAt());
    }
}
