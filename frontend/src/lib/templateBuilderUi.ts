import type { TemplateRoleDefinition } from "../types/precued";

export const TEMPLATE_BUILDER_SECTIONS = ["Structure", "Roles"] as const;
export type TemplateBuilderSection = typeof TEMPLATE_BUILDER_SECTIONS[number];

export function formatStageDuration(durationSeconds: number | null): string {
  if (durationSeconds === null) return "Untimed";
  const minutes = Math.floor(durationSeconds / 60);
  const seconds = durationSeconds % 60;
  if (minutes === 0) return `${seconds} sec`;
  if (seconds === 0) return `${minutes} min`;
  return `${minutes} min ${seconds} sec`;
}

export function roleSummaryLabels(role: TemplateRoleDefinition): string[] {
  const labels: string[] = [];
  if (role.isHostRole) labels.push("Host");
  if (role.isGuestRole) labels.push("Guest");
  labels.push(role.maxMembers === null ? "Unlimited" : `Max ${role.maxMembers}`);
  return labels;
}
