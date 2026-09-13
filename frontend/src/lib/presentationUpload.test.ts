import { describe, expect, it } from "vitest";
import { MAX_PRESENTATION_UPLOAD_BYTES, derivePresentationLabel, validatePresentationFile } from "./presentationUpload";

describe("validatePresentationFile", () => {
  it("accepts a PDF under the size limit", () => {
    expect(validatePresentationFile({ type: "application/pdf", name: "deck.pdf", size: 1024 })).toBeNull();
  });

  it("accepts by extension even when the browser reports no/wrong MIME type", () => {
    expect(validatePresentationFile({ type: "", name: "deck.PDF", size: 1024 })).toBeNull();
  });

  it("rejects a non-PDF file", () => {
    expect(validatePresentationFile({ type: "image/png", name: "deck.png", size: 1024 })).toBe(
      "Please choose a PDF file.",
    );
  });

  it("rejects a PDF over the client-side size warning threshold", () => {
    expect(
      validatePresentationFile({
        type: "application/pdf",
        name: "deck.pdf",
        size: MAX_PRESENTATION_UPLOAD_BYTES + 1,
      }),
    ).toBe("This file is larger than 25 MB and the server will likely reject it.");
  });
});

describe("derivePresentationLabel", () => {
  it("strips a .pdf extension", () => {
    expect(derivePresentationLabel("Q3 Deck.pdf")).toBe("Q3 Deck");
  });

  it("falls back to a generic label when nothing is left after stripping", () => {
    expect(derivePresentationLabel(".pdf")).toBe("Presentation");
    expect(derivePresentationLabel("")).toBe("Presentation");
  });
});
