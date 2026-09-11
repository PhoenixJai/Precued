import { describe, expect, it } from "vitest";
import { templateName } from "./templates";

describe("templateName", () => {
  it("resolves each seeded template id to its display name", () => {
    expect(templateName("sales_call")).toBe("Sales Call");
    expect(templateName("mock_trial")).toBe("Mock Trial");
    expect(templateName("ld_debate")).toBe("Lincoln-Douglas Debate");
  });

  it("falls back to the raw id for an unknown template", () => {
    expect(templateName("something_new")).toBe("something_new");
  });
});
