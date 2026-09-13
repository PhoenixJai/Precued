import { describe, expect, it } from "vitest";
import { buildRoleSetupRows, findHostRole } from "./roleSetup";
import type { RoomParticipantWithGrants, RoomRole } from "../types/precued";

function role(overrides: Partial<RoomRole> & { id: string; roleKey: string }): RoomRole {
  return {
    roomId: "room-1",
    name: overrides.roleKey,
    isHostRole: false,
    isGuestRole: false,
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
  it("builds a row per role for a 4-role template (Mock Trial), not just 3", () => {
    const roles = [
      role({ id: "r-judge", roleKey: "judge", name: "Judge", isHostRole: true, maxMembers: 1 }),
      role({ id: "r-jury", roleKey: "jury", name: "Jury", maxMembers: null }),
      role({ id: "r-defense", roleKey: "defense", name: "Defense", maxMembers: null }),
      role({ id: "r-prosecution", roleKey: "prosecution", name: "Prosecution", maxMembers: null }),
    ];

    const rows = buildRoleSetupRows(roles, []);

    expect(rows).toHaveLength(4);
    expect(rows.map((r) => r.role.roleKey)).toEqual(["judge", "jury", "defense", "prosecution"]);
  });

  it("preserves the runtime guest-role snapshot on each row", () => {
    const rows = buildRoleSetupRows(
      [role({ id: "r-observer", roleKey: "observer", isGuestRole: true })],
      [],
    );

    expect(rows[0].role.isGuestRole).toBe(true);
  });

  it("does not synthesize temporary room-role invite urls", () => {
    const rows = buildRoleSetupRows(
      [role({ id: "r-jury", roleKey: "jury" })],
      [],
    );

    expect(rows[0]).not.toHaveProperty("inviteUrl");
  });

  it("lists every present participant for an unlimited-membership role, not just one", () => {
    const roles = [role({ id: "r-jury", roleKey: "jury", maxMembers: null })];
    const jurorA = participant({ id: "p-a", activeRoomRoleId: "r-jury" });
    const jurorB = participant({ id: "p-b", activeRoomRoleId: "r-jury" });
    const otherRoleParticipant = participant({ id: "p-c", activeRoomRoleId: "r-other" });

    const rows = buildRoleSetupRows(roles, [jurorA, jurorB, otherRoleParticipant]);

    expect(rows[0].joinedParticipants.map((p) => p.id)).toEqual(["p-a", "p-b"]);
    expect(rows[0].assignedParticipants.map((p) => p.id)).toEqual(["p-a", "p-b"]);
  });

  it("keeps a disconnected member assigned for capacity while excluding them from presence", () => {
    const roles = [role({ id: "r-jury", roleKey: "jury" })];
    const departed = participant({ id: "p-a", activeRoomRoleId: "r-jury", leftAt: "2026-01-01T00:05:00Z" });

    const rows = buildRoleSetupRows(roles, [departed]);

    expect(rows[0].joinedParticipants).toEqual([]);
    expect(rows[0].assignedParticipants.map((p) => p.id)).toEqual(["p-a"]);
  });

  it("returns empty membership lists for a role nobody has joined yet", () => {
    const roles = [role({ id: "r-defense", roleKey: "defense" })];

    const rows = buildRoleSetupRows(roles, []);

    expect(rows[0].joinedParticipants).toEqual([]);
    expect(rows[0].assignedParticipants).toEqual([]);
  });
});
