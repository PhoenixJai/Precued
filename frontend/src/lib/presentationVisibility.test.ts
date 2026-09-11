import { describe, expect, it } from "vitest";
import { cellState, planCellStateChange } from "./presentationVisibility";
import type { ShareRoleGrant } from "../types/precued";

function grant(overrides: Partial<ShareRoleGrant> & { id: string; roomRoleId: string }): ShareRoleGrant {
  return {
    shareId: "share-1",
    shareSlideId: null,
    grantedAt: "2026-01-01T00:00:00Z",
    revokedAt: null,
    ...overrides,
  };
}

describe("cellState", () => {
  it("is 'always' when the role holds a whole-share grant, regardless of slide", () => {
    const grants = [grant({ id: "g1", roomRoleId: "role-A", shareSlideId: null })];
    expect(cellState("role-A", "slide-1", grants)).toBe("always");
    expect(cellState("role-A", "slide-2", grants)).toBe("always");
  });

  it("is 'slide' when the role holds a grant scoped to exactly this slide", () => {
    const grants = [grant({ id: "g1", roomRoleId: "role-A", shareSlideId: "slide-1" })];
    expect(cellState("role-A", "slide-1", grants)).toBe("slide");
  });

  it("is 'hidden' when the role's grant is for a different slide", () => {
    const grants = [grant({ id: "g1", roomRoleId: "role-A", shareSlideId: "slide-1" })];
    expect(cellState("role-A", "slide-2", grants)).toBe("hidden");
  });

  it("is 'hidden' when the role has no grants at all", () => {
    expect(cellState("role-A", "slide-1", [])).toBe("hidden");
  });

  it("ignores another role's grants entirely", () => {
    const grants = [grant({ id: "g1", roomRoleId: "role-B", shareSlideId: null })];
    expect(cellState("role-A", "slide-1", grants)).toBe("hidden");
  });
});

describe("planCellStateChange", () => {
  it("setting 'always' from no grants creates one whole-share grant", () => {
    const plan = planCellStateChange("role-A", "slide-1", "always", []);
    expect(plan.toCreate).toEqual([{ roomRoleId: "role-A", shareSlideId: null }]);
    expect(plan.toRevokeGrantIds).toEqual([]);
  });

  it("setting 'always' when already whole-share is a no-op create, but still revokes stray slide-specific grants", () => {
    const grants = [
      grant({ id: "whole", roomRoleId: "role-A", shareSlideId: null }),
      grant({ id: "stray", roomRoleId: "role-A", shareSlideId: "slide-2" }),
    ];
    const plan = planCellStateChange("role-A", "slide-1", "always", grants);
    expect(plan.toCreate).toEqual([]);
    expect(plan.toRevokeGrantIds).toEqual(["stray"]);
  });

  it("setting 'slide' from no grants creates one slide-specific grant", () => {
    const plan = planCellStateChange("role-A", "slide-1", "slide", []);
    expect(plan.toCreate).toEqual([{ roomRoleId: "role-A", shareSlideId: "slide-1" }]);
    expect(plan.toRevokeGrantIds).toEqual([]);
  });

  it("setting 'slide' when currently 'always' revokes the whole-share grant and creates the slide grant", () => {
    const grants = [grant({ id: "whole", roomRoleId: "role-A", shareSlideId: null })];
    const plan = planCellStateChange("role-A", "slide-1", "slide", grants);
    expect(plan.toRevokeGrantIds).toEqual(["whole"]);
    expect(plan.toCreate).toEqual([{ roomRoleId: "role-A", shareSlideId: "slide-1" }]);
  });

  it("setting 'slide' when already exactly that slide grant is a true no-op", () => {
    const grants = [grant({ id: "g1", roomRoleId: "role-A", shareSlideId: "slide-1" })];
    const plan = planCellStateChange("role-A", "slide-1", "slide", grants);
    expect(plan.toCreate).toEqual([]);
    expect(plan.toRevokeGrantIds).toEqual([]);
  });

  it("setting 'hidden' when currently 'always' revokes the whole-share grant", () => {
    const grants = [grant({ id: "whole", roomRoleId: "role-A", shareSlideId: null })];
    const plan = planCellStateChange("role-A", "slide-1", "hidden", grants);
    expect(plan.toRevokeGrantIds).toEqual(["whole"]);
    expect(plan.toCreate).toEqual([]);
  });

  it("setting 'hidden' when currently 'slide' for this exact slide revokes it", () => {
    const grants = [grant({ id: "g1", roomRoleId: "role-A", shareSlideId: "slide-1" })];
    const plan = planCellStateChange("role-A", "slide-1", "hidden", grants);
    expect(plan.toRevokeGrantIds).toEqual(["g1"]);
  });

  it("setting 'hidden' when already hidden is a true no-op", () => {
    const plan = planCellStateChange("role-A", "slide-1", "hidden", []);
    expect(plan.toCreate).toEqual([]);
    expect(plan.toRevokeGrantIds).toEqual([]);
  });

  it("never touches another role's grants", () => {
    const grants = [grant({ id: "other", roomRoleId: "role-B", shareSlideId: null })];
    const plan = planCellStateChange("role-A", "slide-1", "hidden", grants);
    expect(plan.toRevokeGrantIds).toEqual([]);
  });
});
