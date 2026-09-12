import { describe, expect, it } from "vitest";
import { describeSlideImageError } from "./api";

describe("describeSlideImageError", () => {
  it("gives a distinct, honest message for a missing slide image (404)", () => {
    // Matches a real incident: an upload's R2 write reported success but the
    // object was never retrievable — the viewer must see this is a missing
    // asset, not a mysterious failure indistinguishable from any other error.
    expect(describeSlideImageError(404)).toBe(
      "This slide's image is missing. Ask the host to re-upload the presentation.",
    );
  });

  it("falls back to a generic message carrying the status for anything else", () => {
    expect(describeSlideImageError(500)).toBe("Unable to load slide (500)");
    expect(describeSlideImageError(502)).toBe("Unable to load slide (502)");
  });
});
