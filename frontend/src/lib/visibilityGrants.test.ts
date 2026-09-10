import { describe, expect, it } from "vitest";
import {
  computeLocalVisibilityGrants,
  isServerVisibilityGrant,
  toTrackSubscriptionPermissions,
  VISIBILITY_GRANTS_TOPIC,
} from "./visibilityGrants";
import type { ActiveShare, RoomParticipantWithGrants } from "../types/precued";

function participant(overrides: Partial<RoomParticipantWithGrants> & { livekitIdentity: string }): RoomParticipantWithGrants {
  return {
    id: overrides.livekitIdentity,
    roomId: "room-1",
    displayName: overrides.livekitIdentity,
    accessLevel: "MEMBER",
    joinedAt: "2026-01-01T00:00:00Z",
    sessionToken: "irrelevant",
    leftAt: null,
    activeRoomRoleId: null,
    activeShareRoleGrantIds: [],
    ...overrides,
  };
}

describe("isServerVisibilityGrant", () => {
  it("trusts a message on the grants topic with no sender (the server's push)", () => {
    expect(isServerVisibilityGrant(VISIBILITY_GRANTS_TOPIC, undefined)).toBe(true);
  });

  it("ignores a message on the grants topic from a real participant (forged)", () => {
    expect(isServerVisibilityGrant(VISIBILITY_GRANTS_TOPIC, { identity: "guest-123" })).toBe(false);
  });

  it("ignores a message on the grants topic from the host's own identity", () => {
    // Even the publisher's own client isn't trusted as a sender — nothing
    // client-side should ever be treated as the source of truth for grants.
    expect(isServerVisibilityGrant(VISIBILITY_GRANTS_TOPIC, { identity: "host-1" })).toBe(false);
  });

  it("ignores a no-sender message on an unrelated topic", () => {
    expect(isServerVisibilityGrant("some.other.topic", undefined)).toBe(false);
  });

  it("ignores a message with no topic at all", () => {
    expect(isServerVisibilityGrant(undefined, undefined)).toBe(false);
  });
});

describe("computeLocalVisibilityGrants", () => {
  const share: ActiveShare = { id: "share-1", label: "Screen share", roomRoleIds: ["role-sales-eng"] };

  it("always allows base tracks, even with no active share", () => {
    const viewer = participant({ livekitIdentity: "viewer-1", activeRoomRoleId: "role-client" });

    const [grant] = computeLocalVisibilityGrants([viewer], null, ["cam-sid", "mic-sid"], ["screen-sid"]);

    expect(grant).toEqual({
      livekitIdentity: "viewer-1",
      allowed: true,
      trackSids: ["cam-sid", "mic-sid"],
    });
  });

  it("adds the share's tracks only for a viewer whose active role is granted", () => {
    const grantedViewer = participant({ livekitIdentity: "eng-1", activeRoomRoleId: "role-sales-eng" });
    const ungrantedViewer = participant({ livekitIdentity: "client-1", activeRoomRoleId: "role-client" });

    const grants = computeLocalVisibilityGrants(
      [grantedViewer, ungrantedViewer],
      share,
      ["cam-sid"],
      ["screen-sid"],
    );

    expect(grants).toEqual([
      { livekitIdentity: "eng-1", allowed: true, trackSids: ["cam-sid", "screen-sid"] },
      { livekitIdentity: "client-1", allowed: true, trackSids: ["cam-sid"] },
    ]);
  });

  it("treats a participant with no active role as unable to see the share", () => {
    const unassigned = participant({ livekitIdentity: "unassigned-1", activeRoomRoleId: null });

    const [grant] = computeLocalVisibilityGrants([unassigned], share, [], ["screen-sid"]);

    expect(grant).toEqual({ livekitIdentity: "unassigned-1", allowed: false, trackSids: [] });
  });

  it("excludes participants who have already left", () => {
    const left = participant({ livekitIdentity: "gone-1", leftAt: "2026-01-01T00:05:00Z" });
    const present = participant({ livekitIdentity: "here-1" });

    const grants = computeLocalVisibilityGrants([left, present], null, ["cam-sid"], []);

    expect(grants.map((g) => g.livekitIdentity)).toEqual(["here-1"]);
  });
});

describe("toTrackSubscriptionPermissions", () => {
  it("maps allowed grants to their track sids and denied grants to none", () => {
    const permissions = toTrackSubscriptionPermissions([
      { livekitIdentity: "a", allowed: true, trackSids: ["cam-sid"] },
      { livekitIdentity: "b", allowed: false, trackSids: ["cam-sid"] },
    ]);

    expect(permissions).toEqual([
      { participantIdentity: "a", allowAll: false, allowedTrackSids: ["cam-sid"] },
      { participantIdentity: "b", allowAll: false, allowedTrackSids: [] },
    ]);
  });
});
