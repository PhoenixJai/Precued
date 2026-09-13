import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./api";

function storageWithParticipant(): Storage {
  const store: Record<string, string> = {
    "precued.participant": JSON.stringify({
      id: "participant-1",
      roomId: "room-1",
      roomRoleId: "judge-role",
      roleKey: "judge",
      roleName: "Judge",
      isHost: true,
      displayName: "Judge",
      userId: "user-1",
      sessionToken: "participant-token",
    }),
  };
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value; },
    removeItem: (key: string) => { delete store[key]; },
    clear: () => { Object.keys(store).forEach((key) => delete store[key]); },
    key: (index: number) => Object.keys(store)[index] ?? null,
    get length() { return Object.keys(store).length; },
  } as Storage;
}

const flowResponse = {
  roomId: "room-1",
  enabled: true,
  status: "NOT_STARTED",
  currentStageId: null,
  stages: [],
};

describe("Session Flow API client", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.stubGlobal("sessionStorage", storageWithParticipant());
    fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => flowResponse,
    });
    vi.stubGlobal("fetch", fetchMock);
  });

  it("reads flow state with the participant bearer token", async () => {
    await api.getSessionFlow("room-1");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/rooms/room-1/session-flow",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer participant-token" }),
      }),
    );
  });

  it("starts flow with POST", async () => {
    await api.startSessionFlow("room-1");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/rooms/room-1/session-flow/start",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("advances flow with POST", async () => {
    await api.advanceSessionFlow("room-1");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/rooms/room-1/session-flow/advance",
      expect.objectContaining({ method: "POST" }),
    );
  });
});
