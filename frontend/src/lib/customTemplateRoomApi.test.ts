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

describe("custom template room API", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.stubGlobal("sessionStorage", emptyStorage());
    fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({
        id: "room-1",
        templateId: "3cda5ea2-915e-4a55-a0b8-dbd71cf9b3e4",
        templateName: "Negotiation Lab",
        createdByUserId: "user-1",
        livekitRoomName: "room-room-1",
        status: "CREATED",
        hostDisconnectPolicy: "END_CALL",
        createdAt: "2026-09-13T12:00:00Z",
      }),
    });
    vi.stubGlobal("fetch", fetchMock);
  });

  it("sends a generated custom template id unchanged with the Account Holder token", async () => {
    const templateId = "3cda5ea2-915e-4a55-a0b8-dbd71cf9b3e4";

    await api.createRoom(templateId, "account-session-token");

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("/api/rooms");
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({ Authorization: "Bearer account-session-token" });
    expect(JSON.parse(init.body)).toMatchObject({ templateId, hostDisconnectPolicy: "END_CALL" });
  });
});
