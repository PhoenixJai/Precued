import type { InviteStatus } from "../types/precued";

export function roleCapacityLabel(joinedCount: number, maxMembers: number | null): string {
  if (maxMembers !== null) return `${joinedCount}/${maxMembers} joined`;
  if (joinedCount === 0) return "Not yet joined";
  return `${joinedCount} joined`;
}

export function inviteStatusLabel(status: InviteStatus): string {
  if (status === "USED") return "Joined";
  if (status === "EXPIRED") return "Expired";
  return "Invite ready";
}

export function buildInviteJoinUrl(origin: string, token: string): string {
  return `${origin.replace(/\/$/, "")}/join/${token}`;
}
