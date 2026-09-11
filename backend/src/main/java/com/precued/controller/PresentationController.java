package com.precued.controller;

import com.precued.controller.dto.PresentationUploadResponse;
import com.precued.service.PresentationUploadResult;
import com.precued.service.PresentationUploadService;
import com.precued.service.ShareSlideImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Chunk 2 (Precued_DataModel.md "Presentations Feature"): upload a PDF to
 * start a presentation-kind Share, and fetch a slide's image. Like
 * ShareController, resource ownership for the upload isn't checkable by
 * ParticipantSessionInterceptor itself (roomId/publisherParticipantId are
 * request params here, not a {roomId} path variable) — PresentationUploadService
 * (via ShareLifecycleService#requireHostPublisher) does that check against
 * CurrentParticipantContext.
 */
@RestController
@RequestMapping("/api/shares")
public class PresentationController {

    private final PresentationUploadService presentationUploadService;
    private final ShareSlideImageService shareSlideImageService;

    public PresentationController(
            PresentationUploadService presentationUploadService, ShareSlideImageService shareSlideImageService) {
        this.presentationUploadService = presentationUploadService;
        this.shareSlideImageService = shareSlideImageService;
    }

    @PostMapping(value = "/presentations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PresentationUploadResponse upload(
            @RequestParam UUID roomId,
            @RequestParam UUID publisherParticipantId,
            @RequestParam(required = false) String label,
            @RequestParam("file") MultipartFile file) {
        PresentationUploadResult result =
                presentationUploadService.upload(roomId, publisherParticipantId, label, file);
        return PresentationUploadResponse.from(result);
    }

    @GetMapping(value = "/{shareId}/slides/{slideIndex}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable UUID shareId, @PathVariable int slideIndex) {
        byte[] bytes = shareSlideImageService.fetch(shareId, slideIndex);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes);
    }
}
