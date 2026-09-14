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
export type VisibilityScope = "slide" | "presentation";
export type VisibilityAudienceMode = "everyone" | "specific";

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

/**
 * Returns the roles that are effectively visible for the simplified host
 * controls. "This slide" treats both whole-share and current-slide grants as
 * visible. "Entire presentation" intentionally reports only whole-share
 * grants because a patchwork of slide grants is not equivalent to Always.
 */
export function selectedRoleIdsForScope(
  roomRoleIds: string[],
  currentSlideId: string,
  scope: VisibilityScope,
  grants: ShareRoleGrant[],
): string[] {
  return roomRoleIds.filter((roomRoleId) => {
    const roleGrants = grants.filter((grant) => grant.roomRoleId === roomRoleId);
    if (scope === "presentation") return roleGrants.some((grant) => grant.shareSlideId === null);
    return roleGrants.some((grant) => grant.shareSlideId === null || grant.shareSlideId === currentSlideId);
  });
}

export function visibilityAudienceMode(
  roomRoleIds: string[],
  selectedRoleIds: string[],
): VisibilityAudienceMode {
  if (roomRoleIds.length > 0 && roomRoleIds.every((id) => selectedRoleIds.includes(id))) return "everyone";
  return "specific";
}

function planScopedRoleVisibilityChange(
  roomRoleId: string,
  slideIds: string[],
  currentSlideId: string,
  scope: VisibilityScope,
  visible: boolean,
  grants: ShareRoleGrant[],
): GrantPlan {
  const roleGrants = grants.filter((grant) => grant.roomRoleId === roomRoleId);
  const wholeShareGrant = roleGrants.find((grant) => grant.shareSlideId === null);
  const slideGrantById = new Map(
    roleGrants
      .filter((grant): grant is ShareRoleGrant & { shareSlideId: string } => grant.shareSlideId !== null)
      .map((grant) => [grant.shareSlideId, grant]),
  );

  const toCreate: GrantPlan["toCreate"] = [];
  const toRevokeGrantIds: string[] = [];

  if (scope === "presentation") {
    if (visible) {
      if (!wholeShareGrant) toCreate.push({ roomRoleId, shareSlideId: null });
      for (const grant of roleGrants) {
        if (grant.shareSlideId !== null) toRevokeGrantIds.push(grant.id);
      }
    } else {
      for (const grant of roleGrants) toRevokeGrantIds.push(grant.id);
    }
    return { toCreate, toRevokeGrantIds };
  }

  if (visible) {
    if (!wholeShareGrant && !slideGrantById.has(currentSlideId)) {
      toCreate.push({ roomRoleId, shareSlideId: currentSlideId });
    }
    return { toCreate, toRevokeGrantIds };
  }

  if (wholeShareGrant) {
    // A whole-share grant cannot express "visible everywhere except this
    // slide". Preserve the user's prior effective visibility by replacing it
    // with explicit grants for every other slide before hiding this one.
    toRevokeGrantIds.push(wholeShareGrant.id);
    for (const slideId of slideIds) {
      if (slideId === currentSlideId || slideGrantById.has(slideId)) continue;
      toCreate.push({ roomRoleId, shareSlideId: slideId });
    }
  }

  const currentSlideGrant = slideGrantById.get(currentSlideId);
  if (currentSlideGrant) toRevokeGrantIds.push(currentSlideGrant.id);

  return { toCreate, toRevokeGrantIds };
}

/**
 * Pure bulk planner used by the simplified visibility controls. It preserves
 * the matrix semantics exactly while allowing the host to think in terms of
 * an audience plus a scope instead of editing every matrix cell directly.
 */
export function planScopedAudienceChange(
  roomRoleIds: string[],
  visibleRoleIds: string[],
  slideIds: string[],
  currentSlideId: string,
  scope: VisibilityScope,
  grants: ShareRoleGrant[],
): GrantPlan {
  const toCreate: GrantPlan["toCreate"] = [];
  const revokeIds = new Set<string>();
  const createKeys = new Set<string>();

  for (const roomRoleId of roomRoleIds) {
    const rolePlan = planScopedRoleVisibilityChange(
      roomRoleId,
      slideIds,
      currentSlideId,
      scope,
      visibleRoleIds.includes(roomRoleId),
      grants,
    );

    for (const grantId of rolePlan.toRevokeGrantIds) revokeIds.add(grantId);
    for (const item of rolePlan.toCreate) {
      const key = `${item.roomRoleId}:${item.shareSlideId ?? "*"}`;
      if (createKeys.has(key)) continue;
      createKeys.add(key);
      toCreate.push(item);
    }
  }

  return { toCreate, toRevokeGrantIds: [...revokeIds] };
}
