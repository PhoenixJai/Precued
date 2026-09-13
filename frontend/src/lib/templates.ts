import type { TemplateId } from "../types/precued";

/** Fixed labels for the three Precued-owned built-ins. Custom templates use
 * server-provided names on Room responses; unknown ids fall back to a human
 * label rather than leaking a generated UUID into the UI. */
export const TEMPLATE_NAMES: Record<TemplateId, string> = {
  sales_call: "Sales Call",
  mock_trial: "Mock Trial",
  ld_debate: "Lincoln-Douglas Debate",
};

export function templateName(templateId: string): string {
  return (TEMPLATE_NAMES as Record<string, string>)[templateId] ?? "Custom Template";
}
