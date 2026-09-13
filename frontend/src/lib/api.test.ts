import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, describeSlideImageError } from "./api";

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

  it("clears the stored session/participant and sends the browser to a fresh sign-in", async () => {
    // Live incident context: chunk 2 confirmed the backend genuinely
    // returns clean 401s now (SecurityConfig's entry point,
    // AuthSessionInterceptor, AuthenticationRequiredException — all three
    // map to 401). Before this chunk, the frontend just surfaced that as a
    // raw error banner and left a now-useless token in storage.
    storage.setItem("precued.auth", JSON.stringify({ sessionToken: "stale-auth" }));
    storage.setItem("precued.participant", JSON.stringify({ sessionToken: "stale-participant" }));

    await expect(api.getRoom("room-1")).rejects.toThrow("Your session has expired. Please sign in again.");

    expect(storage.getItem("precued.auth")).toBeNull();
    expect(storage.getItem("precued.participant")).toBeNull();
    expect(assign).toHaveBeenCalledWith("/?sessionExpired=1");
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
