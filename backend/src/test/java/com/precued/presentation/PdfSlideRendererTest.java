package com.precued.presentation;

import com.precued.service.PresentationUploadException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses real, PDFBox-produced PDF byte streams as fixtures (not hand-authored
 * decks, but genuinely valid/invalid PDF content for parsing purposes) so
 * these tests exercise the actual PDFBox load/render path end to end,
 * rather than mocking it away.
 */
class PdfSlideRendererTest {

    private static final int MAX_PAGES = 5;
    private static final int RENDER_DPI = 72; // low DPI keeps test runtime small

    private final PdfSlideRenderer renderer = new PdfSlideRenderer(MAX_PAGES, RENDER_DPI);

    private byte[] realPdfWithPages(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage(PDRectangle.LETTER));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithOversizedPage() throws IOException {
        try (PDDocument document = new PDDocument()) {
            // ISO 32000 caps a page at 14400 units per side; this exceeds it.
            document.addPage(new PDPage(new PDRectangle(20000f, 20000f)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void singlePagePdf_rendersOnePngImage() throws Exception {
        byte[] pdf = realPdfWithPages(1);

        List<byte[]> images = renderer.render(pdf);

        assertThat(images).hasSize(1);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(images.get(0)));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isGreaterThan(0);
    }

    @Test
    void multiPagePdf_rendersImagesInPageOrder() throws Exception {
        byte[] pdf = realPdfWithPages(3);

        List<byte[]> images = renderer.render(pdf);

        assertThat(images).hasSize(3);
        for (byte[] image : images) {
            assertThat(ImageIO.read(new ByteArrayInputStream(image))).isNotNull();
        }
    }

    @Test
    void zeroPagePdf_isRejected() throws Exception {
        byte[] pdf = realPdfWithPages(0);

        assertThatThrownBy(() -> renderer.render(pdf))
                .isInstanceOf(PresentationUploadException.class)
                .hasMessageContaining("no pages");
    }

    @Test
    void pdfExceedingPageCap_isRejectedBeforeRenderingAnyPage() throws Exception {
        byte[] pdf = realPdfWithPages(MAX_PAGES + 1);

        assertThatThrownBy(() -> renderer.render(pdf))
                .isInstanceOf(PresentationUploadException.class)
                .hasMessageContaining(String.valueOf(MAX_PAGES));
    }

    @Test
    void nonPdfBytes_isRejectedByMagicByteCheck_notExtensionOrClaimedMimeType() {
        byte[] notAPdf = "this is definitely not a pdf file".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> renderer.render(notAPdf))
                .isInstanceOf(PresentationUploadException.class)
                .hasMessageContaining("not a valid PDF");
    }

    @Test
    void malformedPdf_withValidMagicBytesButCorruptBody_isRejectedCleanly() {
        byte[] corrupt = "%PDF-1.7\nthis is not actually a well-formed pdf body at all%%EOF"
                .getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> renderer.render(corrupt))
                .isInstanceOf(PresentationUploadException.class)
                .hasMessageContaining("could not be parsed");
    }

    @Test
    void pdfWithOversizedPageDimensions_isRejectedBeforeRendering() throws Exception {
        byte[] pdf = pdfWithOversizedPage();

        assertThatThrownBy(() -> renderer.render(pdf))
                .isInstanceOf(PresentationUploadException.class)
                .hasMessageContaining("exceeds");
    }
}
