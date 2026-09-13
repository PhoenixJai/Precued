import { describe, expect, it } from "vitest";
import { defaultVisibleRoleIds, toggleVisibleRoleId } from "./fallbackRoleVisibility";
import type { RoomRole } from "../types/precued";

function role(id: string, name: string, isHostRole = false): RoomRole {
  return {
    id,
    roomId: "room-1",
    roleKey: name.toLowerCase().replaceAll(" ", "_"),
    name,
    isHostRole,
    isGuestRole: false,
    maxMembers: 1,
  };
}

describe("fallback role visibility for templates without presets", () => {
  it("defaults screen sharing to every non-host role", () => {
    const roles = [
      role("host", "Interviewer", true),
      role("candidate", "Candidate"),
      role("observer", "Observer"),
    ];

    expect(defaultVisibleRoleIds(roles)).toEqual(["candidate", "observer"]);
  });

  it("toggles one role independently without disturbing the others", () => {
    expect(toggleVisibleRoleId(["candidate", "observer"], "candidate")).toEqual(["observer"]);
    expect(toggleVisibleRoleId(["observer"], "candidate")).toEqual(["observer", "candidate"]);
  });
});
