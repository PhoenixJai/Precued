import type {
  ActiveShare,
  LiveKitTokenResponse,
  MagicLinkResponse,
  ParticipantRoleAssignment,
  Room,
  RoomParticipant,
  RoomParticipantWithGrants,
  RoomRole,
  SessionResponse,
  Share,
  ShareRoleGrant,
  TemplateId,
  TemplatePreset,
} from "../types/precued";
import { getParticipant } from "./session";

const API_BASE = import.meta.env.VITE_API_BASE ?? "";

type ProblemDetail = {
  title?: string;
  detail?: string;
  status?: number;
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  // Every request scoped to a Room or to acting as a participant needs this
  // (see ParticipantSessionInterceptor, backend). Endpoints reachable before
  // a session exists (auth, room creation, join, templates) just won't have
  // one in storage yet, and the backend doesn't require it for those.
  const participant = getParticipant();
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(participant ? { Authorization: `Bearer ${participant.sessionToken}` } : {}),
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const problem = (await response.json()) as ProblemDetail;
      message = problem.detail ?? problem.title ?? message;
    } catch {
      // Keep the status fallback when the server has no JSON error body.
    }
    throw new Error(message);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  requestMagicLink(email: string) {
    return request<MagicLinkResponse>("/api/auth/magic-link", {
      method: "POST",
      body: JSON.stringify({ email }),
    });
  },

  verifyMagicLink(token: string) {
    return request<SessionResponse>("/api/auth/verify", {
      method: "POST",
      body: JSON.stringify({ token }),
    });
  },

  createRoom(templateId: TemplateId, createdByUserId: string) {
    return request<Room>("/api/rooms", {
      method: "POST",
      body: JSON.stringify({
        templateId,
        createdByUserId,
        hostDisconnectPolicy: "END_CALL",
      }),
    });
  },

  getRoom(roomId: string) {
    return request<Room>(`/api/rooms/${roomId}`);
  },

  getRoomRoles(roomId: string) {
    return request<RoomRole[]>(`/api/rooms/${roomId}/room-roles`);
  },

  getRoomParticipants(roomId: string) {
    return request<RoomParticipantWithGrants[]>(`/api/rooms/${roomId}/room-participants`);
  },

  joinRoom(roomId: string, displayName: string, userId: string | null) {
    return request<RoomParticipant>("/api/room-participants", {
      method: "POST",
      body: JSON.stringify({ roomId, userId, displayName }),
    });
  },

  assignRole(roomParticipantId: string, roomRoleId: string) {
    return request<ParticipantRoleAssignment>("/api/participant-role-assignments", {
      method: "POST",
      body: JSON.stringify({ roomParticipantId, roomRoleId }),
    });
  },

  getLiveKitToken(roomParticipantId: string) {
    return request<LiveKitTokenResponse>(`/api/room-participants/${roomParticipantId}/livekit-token`);
  },

  getPresets(templateId: TemplateId) {
    return request<TemplatePreset[]>(`/api/templates/${templateId}/presets`);
  },

  getActiveShares(roomId: string) {
    return request<ActiveShare[]>(`/api/rooms/${roomId}/active-shares`);
  },

  startShare(roomId: string, publisherParticipantId: string, appliedPresetId: string, label: string) {
    return request<Share>("/api/shares", {
      method: "POST",
      body: JSON.stringify({
        roomId,
        publisherParticipantId,
        appliedPresetId,
        label,
      }),
    });
  },

  endShare(shareId: string) {
    return request<Share>(`/api/shares/${shareId}/end`, { method: "POST" });
  },

  createGrant(shareId: string, roomRoleId: string) {
    return request<ShareRoleGrant>("/api/share-role-grants", {
      method: "POST",
      body: JSON.stringify({ shareId, roomRoleId }),
    });
  },

  revokeGrant(grantId: string) {
    return request<ShareRoleGrant>(`/api/share-role-grants/${grantId}/revoke`, { method: "POST" });
  },
};
