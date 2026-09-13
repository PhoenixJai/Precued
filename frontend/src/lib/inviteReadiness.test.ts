import { describe, expect, it } from "vitest";
import { buildInviteJoinUrl, inviteStatusLabel, roleCapacityLabel } from "./inviteReadiness";

describe("room invite/readiness helpers", () => {
  it("shows joined count against bounded role capacity", () => {
    expect(roleCapacityLabel(1, 3)).toBe("1/3 joined");
    expect(roleCapacityLabel(0, 3)).toBe("0/3 joined");
  });

  it("uses a simple joined count for unlimited roles", () => {
    expect(roleCapacityLabel(0, null)).toBe("Not yet joined");
    expect(roleCapacityLabel(2, null)).toBe("2 joined");
  });

  it("renders invitation lifecycle without claiming an email was sent", () => {
    expect(inviteStatusLabel("PENDING")).toBe("Invite ready");
    expect(inviteStatusLabel("USED")).toBe("Joined");
    expect(inviteStatusLabel("EXPIRED")).toBe("Expired");
  });

  it("builds token-based guest links instead of the temporary room-role route", () => {
    expect(buildInviteJoinUrl("https://precued.example", "secret-token"))
      .toBe("https://precued.example/join/secret-token");
  });
});
