import type {
  ActiveShare,
  LiveKitTokenResponse,
  MagicLinkResponse,
  ParticipantRoleAssignment,
  PresentationUploadResponse,
  Room,
  RoomParticipant,
  RoomParticipantWithGrants,
  RoomRole,
  SessionResponse,
  Share,
  ShareRoleGrant,
  ShareSlide,
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
  // A FormData body (presentation upload) must let the browser set its own
  // multipart/form-data boundary header — forcing application/json here
  // would break the request.
  const isFormData = init?.body instanceof FormData;
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      ...(isFormData ? {} : { "Content-Type": "application/json" }),
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

/**
 * A plain <img src> can't send an Authorization header, and the proxy
 * endpoint requires one (ParticipantSessionInterceptor) — so slide images
 * are fetched here as an authenticated blob and turned into an object URL,
 * never loaded directly by src. 403 (not authorized for this slide right
 * now) is an expected, common outcome — not an exception — since a viewer
 * without a qualifying grant on the current slide hits it on every poll
 * tick; only unexpected failures are distinguished by status for the
 * caller to decide how to react.
 */
export type SlideImageResult =
  | { ok: true; objectUrl: string }
  | { ok: false; status: number };

async function fetchSlideImage(shareId: string, slideIndex: number): Promise<SlideImageResult> {
  const participant = getParticipant();
  const response = await fetch(`${API_BASE}/api/shares/${shareId}/slides/${slideIndex}/image`, {
    headers: participant ? { Authorization: `Bearer ${participant.sessionToken}` } : {},
  });
  if (!response.ok) return { ok: false, status: response.status };
  const blob = await response.blob();
  return { ok: true, objectUrl: URL.createObjectURL(blob) };
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

  /**
   * authSessionToken (from getAuthSession()) is the ONLY thing that
   * determines who this room is created by — the backend derives
   * createdByUserId from it (AuthSessionInterceptor), never from a body
   * field, since a body field was the original vulnerability.
   */
  createRoom(templateId: TemplateId, authSessionToken: string) {
    return request<Room>("/api/rooms", {
      method: "POST",
      headers: { Authorization: `Bearer ${authSessionToken}` },
      body: JSON.stringify({
        templateId,
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

  /**
   * authSessionToken is required whenever userId is non-null — the backend
   * rejects a userId claim with no matching AuthSession behind it. A guest
   * join (userId null) omits it.
   */
  joinRoom(roomId: string, displayName: string, userId: string | null, authSessionToken?: string) {
    return request<RoomParticipant>("/api/room-participants", {
      method: "POST",
      headers: authSessionToken ? { Authorization: `Bearer ${authSessionToken}` } : undefined,
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

  /** shareSlideId omitted/null creates a whole-share grant — unchanged prior behavior. */
  createGrant(shareId: string, roomRoleId: string, shareSlideId?: string | null) {
    return request<ShareRoleGrant>("/api/share-role-grants", {
      method: "POST",
      body: JSON.stringify({ shareId, roomRoleId, shareSlideId: shareSlideId ?? null }),
    });
  },

  revokeGrant(grantId: string) {
    return request<ShareRoleGrant>(`/api/share-role-grants/${grantId}/revoke`, { method: "POST" });
  },

  uploadPresentation(roomId: string, publisherParticipantId: string, label: string, file: File) {
    const formData = new FormData();
    formData.append("roomId", roomId);
    formData.append("publisherParticipantId", publisherParticipantId);
    formData.append("label", label);
    formData.append("file", file);
    return request<PresentationUploadResponse>("/api/shares/presentations", {
      method: "POST",
      body: formData,
    });
  },

  getSlides(shareId: string) {
    return request<ShareSlide[]>(`/api/shares/${shareId}/slides`);
  },

  getShareGrants(shareId: string) {
    return request<ShareRoleGrant[]>(`/api/shares/${shareId}/grants`);
  },

  changeSlide(shareId: string, slideIndex: number) {
    return request<Share>(`/api/shares/${shareId}/current-slide`, {
      method: "POST",
      body: JSON.stringify({ slideIndex }),
    });
  },

  fetchSlideImage,
};
