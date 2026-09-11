import type { ShareRoleGrant } from "../types/precued";

/**
 * The presenter's per-slide visibility matrix (Chunk 3) has one cell per
 * (RoomRole, ShareSlide) pair, in one of three states:
 * - "always": role holds an active whole-share grant (shareSlideId null) —
 *   applies to every slide, not just this one.
 * - "slide": role holds an active grant scoped to exactly this slide.
 * - "hidden": neither — the Runtime Rule's deny-by-default.
 */
export type CellState = "always" | "slide" | "hidden";

export function cellState(roomRoleId: string, slideId: string, grants: ShareRoleGrant[]): CellState {
  const roleGrants = grants.filter((grant) => grant.roomRoleId === roomRoleId);
  if (roleGrants.some((grant) => grant.shareSlideId === null)) return "always";
  if (roleGrants.some((grant) => grant.shareSlideId === slideId)) return "slide";
  return "hidden";
}

/**
 * One instruction to reach a cell's newly-chosen state from its current
 * grants — the actual API calls are made by the caller (this module stays
 * a pure planner, no fetch). Setting "always" makes every other
 * slide-specific grant for that role on this Share redundant, so it's
 * revoked too — the row visibly becomes "always" for every column, which
 * is the truthful reflection of a whole-share grant, not a per-cell
 * setting. Downgrading FROM "always" (to "slide" or "hidden") revokes that
 * one whole-share grant, which means every other column for that role
 * reverts to "hidden" until granted individually — a real consequence of
 * "always" being row-wide, not per-cell, in the underlying schema.
 */
export interface GrantPlan {
  toCreate: { roomRoleId: string; shareSlideId: string | null }[];
  toRevokeGrantIds: string[];
}

export function planCellStateChange(
  roomRoleId: string,
  slideId: string,
  newState: CellState,
  grants: ShareRoleGrant[],
): GrantPlan {
  const roleGrants = grants.filter((grant) => grant.roomRoleId === roomRoleId);
  const wholeShareGrant = roleGrants.find((grant) => grant.shareSlideId === null);
  const thisSlideGrant = roleGrants.find((grant) => grant.shareSlideId === slideId);

  const toCreate: GrantPlan["toCreate"] = [];
  const toRevokeGrantIds: string[] = [];

  if (newState === "always") {
    if (!wholeShareGrant) toCreate.push({ roomRoleId, shareSlideId: null });
    for (const grant of roleGrants) {
      if (grant.shareSlideId !== null) toRevokeGrantIds.push(grant.id);
    }
  } else if (newState === "slide") {
    if (wholeShareGrant) toRevokeGrantIds.push(wholeShareGrant.id);
    if (!thisSlideGrant) toCreate.push({ roomRoleId, shareSlideId: slideId });
  } else {
    if (wholeShareGrant) toRevokeGrantIds.push(wholeShareGrant.id);
    if (thisSlideGrant) toRevokeGrantIds.push(thisSlideGrant.id);
  }

  return { toCreate, toRevokeGrantIds };
}
