import type { RoomRole } from "../types/precued";

/**
 * Templates without saved visibility presets still need usable in-call
 * controls. Default to every non-host RoomRole, matching the least-surprising
 * "share with participants" behavior while leaving the host's own local view
 * independent from ShareRoleGrant rows.
 */
export function defaultVisibleRoleIds(roles: RoomRole[]): string[] {
  return roles.filter((role) => !role.isHostRole).map((role) => role.id);
}

/** Toggle exactly one role while preserving the rest of the selection order. */
export function toggleVisibleRoleId(selectedRoleIds: string[], roomRoleId: string): string[] {
  return selectedRoleIds.includes(roomRoleId)
    ? selectedRoleIds.filter((id) => id !== roomRoleId)
    : [...selectedRoleIds, roomRoleId];
}
