import type { TemplateId } from "../types/precued";

/** Fixed labels for the three Precued-owned built-ins. Custom template names
 * arrive on Room responses and are remembered for the current app lifetime so
 * existing call-page code can render a readable title from only templateId. */
export const TEMPLATE_NAMES: Record<TemplateId, string> = {
  sales_call: "Sales Call",
  mock_trial: "Mock Trial",
  ld_debate: "Lincoln-Douglas Debate",
};

const runtimeTemplateNames = new Map<string, string>();

export function rememberTemplateName(templateId: string, name: string) {
  const cleanName = name.trim();
  if (cleanName) runtimeTemplateNames.set(templateId, cleanName);
}

export function templateName(templateId: string): string {
  return (TEMPLATE_NAMES as Record<string, string>)[templateId]
    ?? runtimeTemplateNames.get(templateId)
    ?? "Custom Template";
}
