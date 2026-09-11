package com.precued.service;

import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.entity.ShareSlide;
import com.precued.presentation.PdfSlideRenderer;
import com.precued.repository.ShareSlideRepository;
import com.precued.storage.SlideImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates a PDF-to-presentation upload (Chunk 2, Precued_DataModel.md
 * "Presentations Feature"): validate the raw upload -> authorize the
 * publisher -> render every page -> only then create the Share and its
 * ShareSlide rows and upload each rendered page to storage. Ordering is
 * deliberate: nothing is persisted or uploaded until the PDF is confirmed
 * valid, so a rejected upload never leaves a half-created Share behind. The
 * whole method is one transaction, so a mid-loop failure (a storage upload
 * error, say) rolls back every DB write made so far — any R2 objects
 * already uploaded in that failed attempt are orphaned rather than
 * cleaned up, an accepted MVP-scope gap since nothing ever references them
 * without a surviving ShareSlide row.
 */
@Service
public class PresentationUploadService {

    private final ShareLifecycleService shareLifecycleService;
    private final ShareSlideRepository shareSlideRepository;
    private final PdfSlideRenderer pdfSlideRenderer;
    private final SlideImageStorage slideImageStorage;
    private final long maxFileSizeBytes;

    public PresentationUploadService(
            ShareLifecycleService shareLifecycleService,
            ShareSlideRepository shareSlideRepository,
            PdfSlideRenderer pdfSlideRenderer,
            SlideImageStorage slideImageStorage,
            @Value("${precued.pdf.max-file-size-bytes}") long maxFileSizeBytes) {
        this.shareLifecycleService = shareLifecycleService;
        this.shareSlideRepository = shareSlideRepository;
        this.pdfSlideRenderer = pdfSlideRenderer;
        this.slideImageStorage = slideImageStorage;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Transactional
    public PresentationUploadResult upload(
            UUID roomId, UUID publisherParticipantId, String label, MultipartFile file) {
        if (file.isEmpty()) {
            throw new PresentationUploadException("File is empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new PresentationUploadException(
                    "File exceeds the " + (maxFileSizeBytes / (1024 * 1024)) + " MB size limit");
        }

        RoomParticipant publisher = shareLifecycleService.requireHostPublisher(roomId, publisherParticipantId);

        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException e) {
            throw new PresentationUploadException("Could not read uploaded file", e);
        }

        List<byte[]> pageImages = pdfSlideRenderer.render(pdfBytes);

        Share share = shareLifecycleService.startPresentation(publisher, resolveLabel(label, file));

        List<ShareSlide> slides = new ArrayList<>(pageImages.size());
        int slideIndex = 0;
        for (byte[] pageImage : pageImages) {
            String key = "shares/" + share.getId() + "/slides/" + slideIndex + ".png";
            slideImageStorage.upload(key, pageImage, "image/png");

            ShareSlide slide = new ShareSlide();
            slide.setShare(share);
            slide.setSlideIndex(slideIndex);
            slide.setImageUrl(key);
            slide.setCreatedAt(Instant.now());
            slides.add(shareSlideRepository.save(slide));

            slideIndex++;
        }

        return new PresentationUploadResult(share, slides);
    }

    private String resolveLabel(String label, MultipartFile file) {
        if (label != null && !label.isBlank()) {
            return label;
        }
        String filename = file.getOriginalFilename();
        return filename != null && !filename.isBlank() ? filename : "Presentation";
    }
}
