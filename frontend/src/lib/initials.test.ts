import { describe, expect, it } from "vitest";
import { initials } from "./initials";

describe("initials", () => {
  it("takes the first letter of up to two words", () => {
    expect(initials("Alex Chen")).toBe("AC");
  });

  it("uppercases lowercase input", () => {
    expect(initials("alex chen")).toBe("AC");
  });

  it("handles a single-word name", () => {
    expect(initials("Cher")).toBe("C");
  });

  it("collapses extra whitespace between words", () => {
    expect(initials("  Alex   Chen  ")).toBe("AC");
  });

  it("returns an empty string for a blank name — matches the two pre-existing copies of this function", () => {
    expect(initials("")).toBe("");
    expect(initials("   ")).toBe("");
  });
});
