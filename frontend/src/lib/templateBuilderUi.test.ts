import { describe, expect, it } from "vitest";
import type { TemplateRoleDefinition } from "../types/precued";
import {
  TEMPLATE_BUILDER_SECTIONS,
  formatStageDuration,
  roleSummaryLabels,
} from "./templateBuilderUi";

const interviewer: TemplateRoleDefinition = {
  id: "role-1",
  templateId: "template-1",
  roleKey: "interviewer",
  name: "Interviewer",
  isHostRole: true,
  isGuestRole: false,
  maxMembers: 3,
  sortOrder: 0,
};

describe("template builder UI contract", () => {
  it("keeps the implemented builder focused on Structure and Roles", () => {
    expect(TEMPLATE_BUILDER_SECTIONS).toEqual(["Structure", "Roles"]);
  });

  it("formats stage durations for agenda-style summaries", () => {
    expect(formatStageDuration(null)).toBe("Untimed");
    expect(formatStageDuration(300)).toBe("5 min");
    expect(formatStageDuration(90)).toBe("1 min 30 sec");
  });

  it("summarizes role constraints without inventing unsupported metadata", () => {
    expect(roleSummaryLabels(interviewer)).toEqual(["Host", "Max 3"]);
    expect(roleSummaryLabels({ ...interviewer, isHostRole: false, isGuestRole: true, maxMembers: null })).toEqual(["Guest", "Unlimited"]);
  });
});
