package com.precued.presentation;

import com.precued.service.PresentationUploadException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates and renders an uploaded PDF's pages to PNG images (Chunk 2,
 * Precued_DataModel.md "Presentations Feature"). Every check here runs
 * BEFORE any page is rendered, so a hostile file is rejected cheaply
 * instead of hanging the synchronous request thread that calls this:
 * magic bytes first (not extension or client-supplied MIME type — a
 * renamed non-PDF must not reach PDFBox), then page count against the
 * configured cap, then each page's declared MediaBox against a hard
 * dimension ceiling (ISO 32000 already caps a page at 14400 units per
 * side; this only ever rejects a non-conformant/crafted file).
 */
@Component
public class PdfSlideRenderer {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final float MAX_PAGE_POINTS = 14400f;

    private final int maxPages;
    private final int renderDpi;

    public PdfSlideRenderer(
            @Value("${precued.pdf.max-pages}") int maxPages,
            @Value("${precued.pdf.render-dpi}") int renderDpi) {
        this.maxPages = maxPages;
        this.renderDpi = renderDpi;
    }

    public List<byte[]> render(byte[] pdfBytes) {
        if (!hasPdfMagicBytes(pdfBytes)) {
            throw new PresentationUploadException("File is not a valid PDF (magic bytes mismatch)");
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new PresentationUploadException("PDF has no pages");
            }
            if (pageCount > maxPages) {
                throw new PresentationUploadException(
                        "PDF has " + pageCount + " pages, exceeding the " + maxPages + "-page limit");
            }

            for (PDPage page : document.getPages()) {
                PDRectangle box = page.getMediaBox();
                if (box == null || box.getWidth() > MAX_PAGE_POINTS || box.getHeight() > MAX_PAGE_POINTS) {
                    throw new PresentationUploadException(
                            "PDF page size exceeds the maximum supported dimensions");
                }
            }

            PDFRenderer pdfRenderer = new PDFRenderer(document);
            List<byte[]> images = new ArrayList<>(pageCount);
            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(i, renderDpi, ImageType.RGB);
                images.add(toPng(image));
            }
            return images;
        } catch (PresentationUploadException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // PDFBox throws plain IOException for most malformed input, but
            // some corrupt/adversarial files surface as an unchecked parser
            // error instead — both mean "this isn't a usable PDF," never a
            // 500.
            throw new PresentationUploadException("PDF could not be parsed: " + e.getMessage(), e);
        }
    }

    private boolean hasPdfMagicBytes(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode rendered page as PNG", e);
        }
    }
}
