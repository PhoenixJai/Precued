export interface TemplateRoleDraft {
  roleKey: string;
  name: string;
  isHostRole: boolean;
  isGuestRole: boolean;
  maxMembers: number | null;
}

export interface ExistingTemplateRole {
  roleKey: string;
  isHostRole: boolean;
}

/**
 * Client-side mirror of TemplateService#addRole's own checks (non-blank
 * fields, unique roleKey per template, at most one host role) — gives
 * immediate feedback before a round trip; the server is still the real
 * authority and re-validates independently.
 */
export function validateNewTemplateRole(
  draft: TemplateRoleDraft,
  existingRoles: ExistingTemplateRole[],
): string | null {
  const roleKey = draft.roleKey.trim();
  if (!roleKey) return "Role key is required.";
  if (!draft.name.trim()) return "Display name is required.";
  if (draft.maxMembers !== null && draft.maxMembers < 1) return "Max members must be at least 1.";
  if (existingRoles.some((role) => role.roleKey === roleKey)) {
    return `A role with the key "${roleKey}" already exists.`;
  }
  if (draft.isHostRole && existingRoles.some((role) => role.isHostRole)) {
    return "This template already has a host role — only one is allowed.";
  }
  return null;
}
