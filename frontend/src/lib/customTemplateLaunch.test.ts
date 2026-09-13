import { describe, expect, it } from "vitest";
import { customTemplateLaunchCards, hasHostRole, roomTemplateTitle } from "./customTemplateLaunch";
import { rememberTemplateName, templateName } from "./templates";
import type { TemplateRoleDefinition, TemplateSummary } from "../types/precued";

const customTemplate: TemplateSummary = {
  id: "3cda5ea2-915e-4a55-a0b8-dbd71cf9b3e4",
  name: "Negotiation Lab",
  isCustom: true,
  createdAt: "2026-09-13T12:00:00Z",
};

function role(overrides: Partial<TemplateRoleDefinition> = {}): TemplateRoleDefinition {
  return {
    id: "role-1",
    templateId: customTemplate.id,
    roleKey: "participant",
    name: "Participant",
    isHostRole: false,
    isGuestRole: false,
    maxMembers: 1,
    sortOrder: 0,
    ...overrides,
  };
}

describe("custom template launch parity", () => {
  it("turns owned custom templates into selectable picker cards without changing their ids", () => {
    expect(customTemplateLaunchCards([customTemplate])).toEqual([
      {
        id: customTemplate.id,
        title: "Negotiation Lab",
        description: "Your private custom template",
      },
    ]);
  });

  it("requires a host role before a custom template can launch a room", () => {
    expect(hasHostRole([role()])).toBe(false);
    expect(hasHostRole([role({ roleKey: "facilitator", name: "Facilitator", isHostRole: true })])).toBe(true);
  });

  it("uses the room's server-provided template name for custom rooms", () => {
    expect(roomTemplateTitle(customTemplate.id, "Negotiation Lab")).toBe("Negotiation Lab");
  });

  it("remembers the server-provided custom name for later call-page lookups", () => {
    rememberTemplateName(customTemplate.id, customTemplate.name);
    expect(templateName(customTemplate.id)).toBe("Negotiation Lab");
  });

  it("keeps the built-in name fallback for older/built-in room payloads", () => {
    expect(roomTemplateTitle("mock_trial", undefined)).toBe("Mock Trial");
  });
});
