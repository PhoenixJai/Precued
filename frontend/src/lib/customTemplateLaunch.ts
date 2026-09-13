import type { TemplateRoleDefinition, TemplateSummary } from "../types/precued";
import { templateName } from "./templates";

export interface CustomTemplateLaunchCard {
  id: string;
  title: string;
  description: string;
}

export function customTemplateLaunchCards(templates: TemplateSummary[]): CustomTemplateLaunchCard[] {
  return templates
    .filter((template) => template.isCustom)
    .map((template) => ({
      id: template.id,
      title: template.name,
      description: "Your private custom template",
    }));
}

export function hasHostRole(roles: TemplateRoleDefinition[]): boolean {
  return roles.some((role) => role.isHostRole);
}

export function roomTemplateTitle(templateId: string, templateDisplayName?: string): string {
  return templateDisplayName?.trim() || templateName(templateId);
}
