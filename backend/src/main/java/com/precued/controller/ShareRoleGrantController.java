package com.precued.controller;

import com.precued.controller.dto.CreateShareRoleGrantRequest;
import com.precued.controller.dto.ShareRoleGrantResponse;
import com.precued.service.ShareRoleGrantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** TEMPORARY: no auth/session enforcement yet — see RoomController for the same caveat. */
@RestController
@RequestMapping("/api/share-role-grants")
public class ShareRoleGrantController {

    private final ShareRoleGrantService shareRoleGrantService;

    public ShareRoleGrantController(ShareRoleGrantService shareRoleGrantService) {
        this.shareRoleGrantService = shareRoleGrantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShareRoleGrantResponse create(@Valid @RequestBody CreateShareRoleGrantRequest request) {
        // Preserves the exact prior call shape for a whole-share grant
        // (shareSlideId omitted/null); the 3-arg overload is only used for
        // the new Chunk 3 slide-specific case.
        var grant = request.shareSlideId() == null
                ? shareRoleGrantService.grant(request.shareId(), request.roomRoleId())
                : shareRoleGrantService.grant(request.shareId(), request.roomRoleId(), request.shareSlideId());
        return ShareRoleGrantResponse.from(grant);
    }

    @PostMapping("/{id}/revoke")
    public ShareRoleGrantResponse revoke(@PathVariable UUID id) {
        return ShareRoleGrantResponse.from(shareRoleGrantService.revoke(id));
    }
}
