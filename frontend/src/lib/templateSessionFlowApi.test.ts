import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./api";

function emptyStorage(): Storage {
  const store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value; },
    removeItem: (key: string) => { delete store[key]; },
    clear: () => { Object.keys(store).forEach((key) => delete store[key]); },
    key: () => null,
    get length() { return Object.keys(store).length; },
  } as Storage;
}

describe("custom Session Flow API", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.stubGlobal("sessionStorage", emptyStorage());
    fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ templateId: "custom-1", enabled: true, stages: [] }),
    });
    vi.stubGlobal("fetch", fetchMock);
  });

  it("reads the private definition with the Account Holder token", async () => {
    await api.getTemplateSessionFlow("custom-1", "account-token");

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("/api/templates/custom-1/session-flow");
    expect(init.headers).toMatchObject({ Authorization: "Bearer account-token" });
  });

  it("saves list order, role ids, and an untimed stage with PUT", async () => {
    await api.saveTemplateSessionFlow(
      "custom-1",
      {
        enabled: true,
        stages: [{
          id: null,
          stageKey: "questions",
          name: "Questions",
          durationSeconds: null,
          templateRoleIds: ["role-1"],
        }],
      },
      "account-token",
    );

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("/api/templates/custom-1/session-flow");
    expect(init.method).toBe("PUT");
    expect(init.headers).toMatchObject({ Authorization: "Bearer account-token" });
    expect(JSON.parse(init.body)).toEqual({
      enabled: true,
      stages: [{
        id: null,
        stageKey: "questions",
        name: "Questions",
        durationSeconds: null,
        templateRoleIds: ["role-1"],
      }],
    });
  });
});
