import { describe, expect, it } from "vitest";
import { sessionFlowToggleText } from "./sessionFlowToggle";

describe("Session Flow toggle presentation", () => {
  it("uses simple on/off language for a switch control", () => {
    expect(sessionFlowToggleText(true)).toBe("On");
    expect(sessionFlowToggleText(false)).toBe("Off");
  });
});
