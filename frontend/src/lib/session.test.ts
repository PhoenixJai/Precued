import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearSession,
  getAuthSession,
  getGrantId,
  getParticipant,
  rememberGrantId,
  saveAuthSession,
  saveParticipant,
} from "./session";

function createFakeStorage(): Storage {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => (key in store ? store[key] : null),
    setItem: (key: string, value: string) => {
      store[key] = value;
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    },
    key: () => null,
    get length() {
      return Object.keys(store).length;
    },
  } as Storage;
}

describe("clearSession", () => {
  beforeEach(() => {
    vi.stubGlobal("sessionStorage", createFakeStorage());
  });

  it("removes the auth session, participant, and remembered grant ids together", () => {
    // A 401 means none of these three are trustworthy anymore — see
    // lib/api.ts's request(), which calls this on every 401 response.
    // Leaving any one behind risks a stale sessionToken getting sent again
    // right after the app redirects to sign-in.
    saveAuthSession({ sessionToken: "auth-tok", userId: "u1", email: "host@example.com", displayName: "Host", expiresAt: "2099-01-01T00:00:00Z" });
    saveParticipant({
      id: "p1",
      roomId: "r1",
      roomRoleId: "role1",
      roleKey: "host",
      roleName: "Host",
      isHost: true,
      displayName: "Host",
      userId: "u1",
      sessionToken: "participant-tok",
    });
    rememberGrantId("share1", "role1", "grant1");

    clearSession();

    expect(getAuthSession()).toBeNull();
    expect(getParticipant()).toBeNull();
    expect(getGrantId("share1", "role1")).toBeNull();
  });
});
