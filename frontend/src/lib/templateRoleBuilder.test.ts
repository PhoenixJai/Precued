import { describe, expect, it } from "vitest";
import { validateNewTemplateRole } from "./templateRoleBuilder";

const noExistingRoles: { roleKey: string; isHostRole: boolean }[] = [];

describe("validateNewTemplateRole", () => {
  it("accepts a well-formed role with no conflicts", () => {
    expect(
      validateNewTemplateRole(
        { roleKey: "jury", name: "Jury", isHostRole: false, isGuestRole: false, maxMembers: null },
        noExistingRoles,
      ),
    ).toBeNull();
  });

  it("rejects a blank role key", () => {
    expect(
      validateNewTemplateRole(
        { roleKey: "  ", name: "Jury", isHostRole: false, isGuestRole: false, maxMembers: null },
        noExistingRoles,
      ),
    ).toBe("Role key is required.");
  });

  it("rejects a blank display name", () => {
    expect(
      validateNewTemplateRole(
        { roleKey: "jury", name: "", isHostRole: false, isGuestRole: false, maxMembers: null },
        noExistingRoles,
      ),
    ).toBe("Display name is required.");
  });

  it("rejects a non-positive max members", () => {
    expect(
      validateNewTemplateRole(
        { roleKey: "jury", name: "Jury", isHostRole: false, isGuestRole: false, maxMembers: 0 },
        noExistingRoles,
      ),
    ).toBe("Max members must be at least 1.");
  });

  it("rejects a role key that already exists on this template", () => {
    expect(
      validateNewTemplateRole(
        { roleKey: "judge", name: "Second Judge", isHostRole: false, isGuestRole: false, maxMembers: null },
        [{ roleKey: "judge", isHostRole: true }],
      ),
    ).toBe('A role with the key "judge" already exists.');
  });

  it("rejects a second host role — mirrors TemplateService#addRole's own check", () => {
    expect(
      validateNewTemplateRole(
        { roleKey: "bailiff", name: "Bailiff", isHostRole: true, isGuestRole: false, maxMembers: null },
        [{ roleKey: "judge", isHostRole: true }],
      ),
    ).toBe("This template already has a host role — only one is allowed.");
  });
});
