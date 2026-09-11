package com.precued.controller;

import com.precued.controller.dto.ChangeSlideRequest;
import com.precued.controller.dto.ShareResponse;
import com.precued.controller.dto.ShareRoleGrantResponse;
import com.precued.controller.dto.SlideResponse;
import com.precued.controller.dto.StartShareRequest;
import com.precued.entity.Share;
import com.precued.service.ShareLifecycleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * TEMPORARY: no auth/session enforcement yet. publisherParticipantId is
 * accepted directly from the request body and trusted as the caller's
 * identity claim — the host-role check below only validates against DB
 * state, not against who is actually making this HTTP call. This will be
 * replaced once auth exists.
 */
@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final ShareLifecycleService shareLifecycleService;

    public ShareController(ShareLifecycleService shareLifecycleService) {
        this.shareLifecycleService = shareLifecycleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShareResponse start(@Valid @RequestBody StartShareRequest request) {
        Share share = shareLifecycleService.start(
                request.roomId(), request.publisherParticipantId(), request.appliedPresetId(), request.label());
        return ShareResponse.from(share);
    }

    @PostMapping("/{id}/end")
    public ShareResponse end(@PathVariable UUID id) {
        return ShareResponse.from(shareLifecycleService.end(id));
    }

    /** Judgment call, flagged: named as a sub-resource ("set the current slide"), matching /{id}/end's action-suffix shape. */
    @PostMapping("/{id}/current-slide")
    public ShareResponse changeSlide(@PathVariable UUID id, @Valid @RequestBody ChangeSlideRequest request) {
        return ShareResponse.from(shareLifecycleService.changeSlide(id, request.slideIndex()));
    }

    @GetMapping("/{id}/slides")
    public List<SlideResponse> listSlides(@PathVariable UUID id) {
        return shareLifecycleService.listSlides(id);
    }

    @GetMapping("/{id}/grants")
    public List<ShareRoleGrantResponse> listGrants(@PathVariable UUID id) {
        return shareLifecycleService.listGrants(id);
    }
}
