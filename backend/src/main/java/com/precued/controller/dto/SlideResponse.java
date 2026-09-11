package com.precued.controller.dto;

import java.util.UUID;

/**
 * imageUrl is this backend's own proxy path, never a raw R2 key/URL — see
 * ShareSlideImageService's Javadoc for why images are proxied rather than
 * served from a public or presigned R2 URL.
 */
public record SlideResponse(UUID id, int slideIndex, String imageUrl) {

    public static SlideResponse from(com.precued.entity.ShareSlide slide, UUID shareId) {
        return new SlideResponse(
                slide.getId(),
                slide.getSlideIndex(),
                "/api/shares/" + shareId + "/slides/" + slide.getSlideIndex() + "/image");
    }
}
