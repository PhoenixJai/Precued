import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, describeMagicLinkVerifyError, describeSlideImageError } from "./api";

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

describe("request() 401 handling", () => {
  let storage: Storage;
  let assign: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    storage = createFakeStorage();
    assign = vi.fn();
    vi.stubGlobal("sessionStorage", storage);
    vi.stubGlobal("location", { assign });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({ title: "Unauthorized", detail: "Missing or invalid session", status: 401 }),
      }),
    );
  });

  it("an Account Holder's expired session clears storage and goes to /login, not the landing page", async () => {
    // Live incident context: chunk 2 confirmed the backend genuinely
    // returns clean 401s now (SecurityConfig's entry point,
    // AuthSessionInterceptor, AuthenticationRequiredException — all three
    // map to 401). Auth & Account Overhaul: "/" is now a public marketing
    // landing page with no sign-in form on it at all, so an Account
    // Holder's dead session has to land on /login specifically, or there's
    // nowhere on the page to actually recover from it.
    storage.setItem("precued.auth", JSON.stringify({ sessionToken: "stale-auth" }));

    await expect(api.getRoom("room-1")).rejects.toThrow("Your session has expired. Please sign in again.");

    expect(storage.getItem("precued.auth")).toBeNull();
    expect(assign).toHaveBeenCalledWith("/login?sessionExpired=1");
  });

  it("a guest's expired room session clears storage and goes to the landing page, not /login", async () => {
    // A guest never had an account to log back into — /login would be a
    // dead end for them. Distinguished from the Account Holder case by
    // whether precued.auth (not precued.participant) was present.
    storage.setItem("precued.participant", JSON.stringify({ sessionToken: "stale-participant" }));

    await expect(api.getRoom("room-1")).rejects.toThrow("Your session has expired. Please sign in again.");

    expect(storage.getItem("precued.participant")).toBeNull();
    expect(assign).toHaveBeenCalledWith("/?sessionExpired=1");
  });
});

describe("request authentication routing", () => {
  let storage: Storage;
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    storage = createFakeStorage();
    vi.stubGlobal("sessionStorage", storage);
    fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [],
    });
    vi.stubGlobal("fetch", fetchMock);
  });

  it("does not send a RoomParticipant bearer token when reading public built-in presets", async () => {
    // Regression for the production Start Call failure: CallPage polls
    // /api/templates/{templateId}/presets after connecting. That route is
    // public, but AuthSessionInterceptor treats ANY supplied bearer token
    // under /api/templates/** as an Account/AuthSession token. Sending the
    // RoomParticipant token here therefore turns a healthy call into a 401
    // and the frontend misleadingly reports "session expired".
    storage.setItem("precued.participant", JSON.stringify({ sessionToken: "participant-token" }));
    storage.setItem("precued.auth", JSON.stringify({ sessionToken: "account-token" }));

    await api.getPresets("mock_trial");

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers).not.toHaveProperty("Authorization");
  });

  it("still sends the RoomParticipant bearer token on protected room requests", async () => {
    storage.setItem("precued.participant", JSON.stringify({ sessionToken: "participant-token" }));

    await api.getRoom("room-1");

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers).toMatchObject({ Authorization: "Bearer participant-token" });
  });
});

describe("describeSlideImageError", () => {
  it("gives a distinct, honest message for a missing slide image (404)", () => {
    // Matches a real incident: an upload's R2 write reported success but the
    // object was never retrievable — the viewer must see this is a missing
    // asset, not a mysterious failure indistinguishable from any other error.
    expect(describeSlideImageError(404)).toBe(
      "This slide's image is missing. Ask the host to re-upload the presentation.",
    );
  });

  it("falls back to a generic message carrying the status for anything else", () => {
    expect(describeSlideImageError(500)).toBe("Unable to load slide (500)");
    expect(describeSlideImageError(502)).toBe("Unable to load slide (502)");
  });
});

describe("describeMagicLinkVerifyError", () => {
  it("replaces the unknown-token backend message, which leaks the raw token, with a clean one", () => {
    // AuthService.verifyMagicLink: "No magic link token " + token
    expect(describeMagicLinkVerifyError("No magic link token abc123xyz")).toBe(
      "This sign-in link is invalid.",
    );
  });

  it("passes the expired/already-used messages through unchanged — already clear on their own", () => {
    expect(describeMagicLinkVerifyError("Magic link token has expired")).toBe(
      "Magic link token has expired",
    );
    expect(describeMagicLinkVerifyError("Magic link token has already been used")).toBe(
      "Magic link token has already been used",
    );
  });
});
