import type { TemplateId } from "../types/precued";

/**
 * TemplateId is a closed union (3 seeded templates — see
 * V2__seed_templates.sql), so a client-side name lookup is safe here: it's
 * not user data, it's the same fixed set already listed in
 * TemplatePickerPage's template cards. No backend endpoint exposes
 * Template.name today; add one instead of extending this if a 4th
 * template is ever added dynamically rather than via a new migration.
 */
export const TEMPLATE_NAMES: Record<TemplateId, string> = {
  sales_call: "Sales Call",
  mock_trial: "Mock Trial",
  ld_debate: "Lincoln-Douglas Debate",
};

export function templateName(templateId: string): string {
  return (TEMPLATE_NAMES as Record<string, string>)[templateId] ?? templateId;
}
