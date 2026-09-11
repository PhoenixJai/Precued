package com.precued.service;

import com.precued.entity.Share;
import com.precued.entity.ShareSlide;

import java.util.List;

/** The Share and its ShareSlide rows in slide-index order, from one upload. */
public record PresentationUploadResult(Share share, List<ShareSlide> slides) {
}
