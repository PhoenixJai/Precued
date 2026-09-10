import { describe, expect, it } from "vitest";
import { isServerVisibilityGrant, VISIBILITY_GRANTS_TOPIC } from "./visibilityGrants";

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
