import { describe, expect, it } from "vitest";
import {
  moveStage,
  nextStageKey,
  validateTemplateFlowDraft,
  type TemplateFlowStageDraft,
} from "./templateSessionFlowBuilder";

function stage(overrides: Partial<TemplateFlowStageDraft> = {}): TemplateFlowStageDraft {
  return {
    id: null,
    stageKey: "questions",
    name: "Questions",
    durationSeconds: null,
    templateRoleIds: ["candidate"],
    ...overrides,
  };
}

describe("custom Session Flow builder helpers", () => {
  it("generates a stable unique stage key when a new stage is added", () => {
    expect(nextStageKey("Opening Statement", [])).toBe("opening_statement");
    expect(nextStageKey("Opening Statement", ["opening_statement"])).toBe("opening_statement_2");
    expect(nextStageKey("!!!", [])).toBe("stage");
  });

  it("reorders stages without mutating the original array", () => {
    const original = [
      stage({ stageKey: "one", name: "One" }),
      stage({ stageKey: "two", name: "Two" }),
      stage({ stageKey: "three", name: "Three" }),
    ];

    const moved = moveStage(original, 2, 0);

    expect(moved.map((item) => item.stageKey)).toEqual(["three", "one", "two"]);
    expect(original.map((item) => item.stageKey)).toEqual(["one", "two", "three"]);
  });

  it("allows an untimed stage and rejects non-positive timers", () => {
    expect(validateTemplateFlowDraft([stage({ durationSeconds: null })])).toBeNull();
    expect(validateTemplateFlowDraft([stage({ durationSeconds: 0 })])).toContain("timer");
  });

  it("requires every configured stage to have at least one active role", () => {
    expect(validateTemplateFlowDraft([stage({ templateRoleIds: [] })])).toContain("role");
  });
});
