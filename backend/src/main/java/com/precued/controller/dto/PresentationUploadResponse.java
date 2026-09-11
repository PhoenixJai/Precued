package com.precued.controller.dto;

import com.precued.entity.Share;
import com.precued.service.PresentationUploadResult;

import java.util.List;
import java.util.UUID;

public record PresentationUploadResponse(
        UUID shareId,
        UUID roomId,
        UUID publisherParticipantId,
        String label,
        Share.Kind kind,
        int currentSlideIndex,
        List<SlideResponse> slides) {

    public static PresentationUploadResponse from(PresentationUploadResult result) {
        Share share = result.share();
        List<SlideResponse> slides = result.slides().stream()
                .map(slide -> SlideResponse.from(slide, share.getId()))
                .toList();
        return new PresentationUploadResponse(
                share.getId(),
                share.getRoom().getId(),
                share.getPublisher().getId(),
                share.getLabel(),
                share.getKind(),
                share.getCurrentSlideIndex(),
                slides);
    }
}
