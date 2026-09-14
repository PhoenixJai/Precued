import { describe, expect, it } from "vitest";
import {
  formatSessionStageDuration,
  sessionRoleState,
  sessionRoleStatusLabel,
} from "./sessionSetupUi";

describe("session setup ui helpers", () => {
  it("marks the host as ready regardless of participant counts", () => {
    expect(sessionRoleState(true, 0)).toBe("ready");
    expect(sessionRoleStatusLabel(true, 0, 1)).toBe("Ready · Host");
  });

  it("marks non-host roles ready once someone has joined", () => {
    expect(sessionRoleState(false, 0)).toBe("waiting");
    expect(sessionRoleState(false, 2)).toBe("ready");
    expect(sessionRoleStatusLabel(false, 2, 3)).toBe("2/3 joined");
  });

  it("keeps unlimited-role status concise", () => {
    expect(sessionRoleStatusLabel(false, 0, null)).toBe("Not yet joined");
    expect(sessionRoleStatusLabel(false, 4, null)).toBe("4 joined");
  });

  it("formats structure-stage durations for the setup preview", () => {
    expect(formatSessionStageDuration(null)).toBe("Untimed");
    expect(formatSessionStageDuration(30)).toBe("30 sec");
    expect(formatSessionStageDuration(60)).toBe("1 min");
    expect(formatSessionStageDuration(90)).toBe("1m 30s");
    expect(formatSessionStageDuration(120)).toBe("2 min");
  });
});
