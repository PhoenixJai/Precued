import { roleCapacityLabel } from "./inviteReadiness";

export type SessionRoleState = "ready" | "waiting";

export function sessionRoleState(isHostRole: boolean, joinedCount: number): SessionRoleState {
  return isHostRole || joinedCount > 0 ? "ready" : "waiting";
}

export function sessionRoleStatusLabel(
  isHostRole: boolean,
  joinedCount: number,
  maxMembers: number | null,
): string {
  if (isHostRole) return "Ready · Host";
  return roleCapacityLabel(joinedCount, maxMembers);
}

export function formatSessionStageDuration(durationSeconds: number | null): string {
  if (durationSeconds === null) return "Untimed";
  if (durationSeconds < 60) return `${durationSeconds} sec`;
  if (durationSeconds % 60 === 0) {
    const minutes = durationSeconds / 60;
    return `${minutes} min`;
  }
  const minutes = Math.floor(durationSeconds / 60);
  const seconds = durationSeconds % 60;
  return `${minutes}m ${seconds}s`;
}
