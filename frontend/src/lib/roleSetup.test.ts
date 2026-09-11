import { describe, expect, it } from "vitest";
import { buildRoleSetupRows, findHostRole } from "./roleSetup";
import type { RoomParticipantWithGrants, RoomRole } from "../types/precued";

function role(overrides: Partial<RoomRole> & { id: string; roleKey: string }): RoomRole {
  return {
    roomId: "room-1",
    name: overrides.roleKey,
    isHostRole: false,
    maxMembers: null,
    ...overrides,
  };
}

function participant(overrides: Partial<RoomParticipantWithGrants> & { id: string }): RoomParticipantWithGrants {
  return {
    roomId: "room-1",
    livekitIdentity: overrides.id,
    displayName: overrides.id,
    accessLevel: "MEMBER",
    joinedAt: "2026-01-01T00:00:00Z",
    sessionToken: "irrelevant",
    leftAt: null,
    activeRoomRoleId: null,
    activeShareRoleGrantIds: [],
    ...overrides,
  };
}

describe("findHostRole", () => {
  it("finds the host role regardless of roleKey or position", () => {
    const roles = [
      role({ id: "r-jury", roleKey: "jury" }),
      role({ id: "r-judge", roleKey: "judge", isHostRole: true }),
      role({ id: "r-defense", roleKey: "defense" }),
    ];

    expect(findHostRole(roles)?.id).toBe("r-judge");
  });

  it("returns undefined when no role is marked host", () => {
    expect(findHostRole([role({ id: "r-1", roleKey: "audience" })])).toBeUndefined();
  });
});

describe("buildRoleSetupRows", () => {
  const roomId = "room-1";
  const origin = "https://precued.example";

  it("builds a row per role for a 4-role template (Mock Trial), not just 3", () => {
    const roles = [
      role({ id: "r-judge", roleKey: "judge", name: "Judge", isHostRole: true, maxMembers: 1 }),
      role({ id: "r-jury", roleKey: "jury", name: "Jury", maxMembers: null }),
      role({ id: "r-defense", roleKey: "defense", name: "Defense", maxMembers: null }),
      role({ id: "r-prosecution", roleKey: "prosecution", name: "Prosecution", maxMembers: null }),
    ];

    const rows = buildRoleSetupRows(roles, [], roomId, origin);

    expect(rows).toHaveLength(4);
    expect(rows.map((r) => r.role.roleKey)).toEqual(["judge", "jury", "defense", "prosecution"]);
  });

  it("gives the host role no invite link, and every other role an invite link", () => {
    const roles = [
      role({ id: "r-judge", roleKey: "judge", isHostRole: true }),
      role({ id: "r-jury", roleKey: "jury" }),
    ];

    const rows = buildRoleSetupRows(roles, [], roomId, origin);

    expect(rows.find((r) => r.role.id === "r-judge")?.inviteUrl).toBeNull();
    expect(rows.find((r) => r.role.id === "r-jury")?.inviteUrl)
        .toBe(`${origin}/join/${roomId}/r-jury`);
  });

  it("lists every joined participant for an unlimited-membership role, not just one", () => {
    // The old sales_call-only UI assumed exactly one participant per role
    // (a single .find()) — Jury/Audience have max_members = null and can
    // legitimately hold several participants at once.
    const roles = [role({ id: "r-jury", roleKey: "jury", maxMembers: null })];
    const jurorA = participant({ id: "p-a", activeRoomRoleId: "r-jury" });
    const jurorB = participant({ id: "p-b", activeRoomRoleId: "r-jury" });
    const otherRoleParticipant = participant({ id: "p-c", activeRoomRoleId: "r-other" });

    const rows = buildRoleSetupRows(roles, [jurorA, jurorB, otherRoleParticipant], roomId, origin);

    expect(rows[0].joinedParticipants.map((p) => p.id)).toEqual(["p-a", "p-b"]);
  });

  it("excludes participants who have left", () => {
    const roles = [role({ id: "r-jury", roleKey: "jury" })];
    const departed = participant({ id: "p-a", activeRoomRoleId: "r-jury", leftAt: "2026-01-01T00:05:00Z" });

    const rows = buildRoleSetupRows(roles, [departed], roomId, origin);

    expect(rows[0].joinedParticipants).toEqual([]);
  });

  it("returns an empty joinedParticipants list for a role nobody has joined yet", () => {
    const roles = [role({ id: "r-defense", roleKey: "defense" })];

    const rows = buildRoleSetupRows(roles, [], roomId, origin);

    expect(rows[0].joinedParticipants).toEqual([]);
  });
});
