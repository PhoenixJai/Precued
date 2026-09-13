import type { RoomParticipantWithGrants, RoomRole } from "../types/precued";

/** The host role for a Room — is_host_role is the marker, never a specific roleKey. */
export function findHostRole(roles: RoomRole[]): RoomRole | undefined {
  return roles.find((role) => role.isHostRole);
}

export interface RoleSetupRow {
  role: RoomRole;
  /**
   * Every currently-present participant holding this role, not just one —
   * a role with maxMembers === null (e.g. Jury, Audience) can legitimately
   * hold several at once.
   */
  joinedParticipants: RoomParticipantWithGrants[];
}

/** One row per RoomRole, in whatever order the backend returned them. */
export function buildRoleSetupRows(
  roles: RoomRole[],
  participants: RoomParticipantWithGrants[],
): RoleSetupRow[] {
  return roles.map((role) => ({
    role,
    joinedParticipants: participants.filter(
      (participant) => participant.activeRoomRoleId === role.id && !participant.leftAt,
    ),
  }));
}
