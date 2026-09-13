import type { RoomParticipantWithGrants, RoomRole } from "../types/precued";

/** The host role for a Room — is_host_role is the marker, never a specific roleKey. */
export function findHostRole(roles: RoomRole[]): RoomRole | undefined {
  return roles.find((role) => role.isHostRole);
}

export interface RoleSetupRow {
  role: RoomRole;
  /** Currently present participants holding this role. */
  joinedParticipants: RoomParticipantWithGrants[];
  /**
   * Stable room memberships holding this role, including someone who has
   * disconnected. A consumed invite keeps its seat/history, so capacity must
   * use this count rather than presence alone.
   */
  assignedParticipants: RoomParticipantWithGrants[];
}

/** One row per RoomRole, in whatever order the backend returned them. */
export function buildRoleSetupRows(
  roles: RoomRole[],
  participants: RoomParticipantWithGrants[],
): RoleSetupRow[] {
  return roles.map((role) => {
    const assignedParticipants = participants.filter(
      (participant) => participant.activeRoomRoleId === role.id,
    );
    return {
      role,
      assignedParticipants,
      joinedParticipants: assignedParticipants.filter((participant) => !participant.leftAt),
    };
  });
}
