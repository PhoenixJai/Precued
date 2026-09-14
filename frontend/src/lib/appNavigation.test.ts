import { describe, expect, it } from "vitest";
import { PRIMARY_NAVIGATION, WORKSPACE_NAVIGATION } from "./appNavigation";

describe("app navigation", () => {
  it("uses Sessions as the product term instead of Rooms", () => {
    expect(PRIMARY_NAVIGATION.map((item) => item.label)).toEqual(["Templates", "Sessions", "Help"]);
    expect(PRIMARY_NAVIGATION.map((item) => item.label)).not.toContain("Rooms");
  });

  it("keeps the workspace shell focused on the signed-in app", () => {
    expect(WORKSPACE_NAVIGATION.map((item) => item.label)).toEqual(["Overview", "Templates", "Sessions", "Help"]);
    expect(WORKSPACE_NAVIGATION.find((item) => item.label === "Sessions")?.enabled).toBe(false);
  });
});
