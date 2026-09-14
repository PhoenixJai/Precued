import { describe, expect, it } from "vitest";
import {
  cellState,
  planCellStateChange,
  planScopedAudienceChange,
  selectedRoleIdsForScope,
  visibilityAudienceMode,
} from "./presentationVisibility";
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

describe("simplified presentation visibility", () => {
  it("derives current-slide visibility from both whole-presentation and current-slide grants", () => {
    const grants = [
      grant({ id: "whole", roomRoleId: "role-A", shareSlideId: null }),
      grant({ id: "current", roomRoleId: "role-B", shareSlideId: "slide-1" }),
      grant({ id: "other-slide", roomRoleId: "role-C", shareSlideId: "slide-2" }),
    ];

    expect(selectedRoleIdsForScope(["role-A", "role-B", "role-C"], "slide-1", "slide", grants))
      .toEqual(["role-A", "role-B"]);
  });

  it("derives entire-presentation visibility only from whole-share grants", () => {
    const grants = [
      grant({ id: "whole", roomRoleId: "role-A", shareSlideId: null }),
      grant({ id: "current", roomRoleId: "role-B", shareSlideId: "slide-1" }),
    ];

    expect(selectedRoleIdsForScope(["role-A", "role-B"], "slide-1", "presentation", grants))
      .toEqual(["role-A"]);
  });

  it("reports Everyone only when every available role is selected", () => {
    expect(visibilityAudienceMode(["role-A", "role-B"], ["role-A", "role-B"])).toBe("everyone");
    expect(visibilityAudienceMode(["role-A", "role-B"], ["role-A"])).toBe("specific");
    expect(visibilityAudienceMode([], [])).toBe("specific");
  });

  it("applies Entire presentation by normalizing selected roles to whole-share and fully hiding unselected roles", () => {
    const grants = [
      grant({ id: "a-slide", roomRoleId: "role-A", shareSlideId: "slide-1" }),
      grant({ id: "b-whole", roomRoleId: "role-B", shareSlideId: null }),
      grant({ id: "b-slide", roomRoleId: "role-B", shareSlideId: "slide-2" }),
    ];

    const plan = planScopedAudienceChange(
      ["role-A", "role-B"],
      ["role-A"],
      ["slide-1", "slide-2"],
      "slide-1",
      "presentation",
      grants,
    );

    expect(plan.toCreate).toEqual([{ roomRoleId: "role-A", shareSlideId: null }]);
    expect(plan.toRevokeGrantIds.sort()).toEqual(["a-slide", "b-slide", "b-whole"].sort());
  });

  it("hiding a role on This slide preserves its visibility on every other slide when it was previously Always", () => {
    const grants = [grant({ id: "whole", roomRoleId: "role-A", shareSlideId: null })];

    const plan = planScopedAudienceChange(
      ["role-A"],
      [],
      ["slide-1", "slide-2", "slide-3"],
      "slide-1",
      "slide",
      grants,
    );

    expect(plan.toRevokeGrantIds).toEqual(["whole"]);
    expect(plan.toCreate).toEqual([
      { roomRoleId: "role-A", shareSlideId: "slide-2" },
      { roomRoleId: "role-A", shareSlideId: "slide-3" },
    ]);
  });

  it("changing This slide leaves unrelated slide-specific grants untouched", () => {
    const grants = [grant({ id: "other", roomRoleId: "role-A", shareSlideId: "slide-2" })];

    const plan = planScopedAudienceChange(
      ["role-A"],
      ["role-A"],
      ["slide-1", "slide-2"],
      "slide-1",
      "slide",
      grants,
    );

    expect(plan.toRevokeGrantIds).toEqual([]);
    expect(plan.toCreate).toEqual([{ roomRoleId: "role-A", shareSlideId: "slide-1" }]);
  });
});
