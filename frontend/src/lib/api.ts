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
  TemplateRoleDefinition,
  TemplateSummary,
} from "../types/precued";
import { clearSession, getAuthSession, getParticipant } from "./session";

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

  if (response.status === 401) {
    // A 401 here always means "not authenticated at all" (SecurityConfig's
    // AuthenticationEntryPoint, AuthSessionInterceptor, or
    // AuthenticationRequiredException on the backend — never a
    // business-rule rejection, which is 403/404/400 instead). The stored
    // token is worthless once this happens, whatever the reason (expired,
    // revoked, or simply never valid) — clear it and force a fresh sign-in
    // rather than leaving the app stuck retrying with the same bad token.
    //
    // Auth & Account Overhaul: "/" is a public landing page with no sign-in
    // form on it, so an Account Holder's dead session has to land on
    // /login specifically. Checked before clearing: an Account Holder
    // session (precued.auth) means /login; a guest's room-scoped session
    // alone means /, since a guest never had an account to log back into.
    const wasAccountHolder = Boolean(getAuthSession());
    clearSession();
    location.assign(wasAccountHolder ? "/login?sessionExpired=1" : "/?sessionExpired=1");
    throw new Error("Your session has expired. Please sign in again.");
  }

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

/**
 * A distinct, honest message for a slide image that genuinely doesn't exist
 * in storage (404 — see SlideImageStorage's NoSuchKeyException handling on
 * the backend) versus every other failure, which stays a generic
 * status-carrying message since there's nothing more specific to say.
 */
export function describeSlideImageError(status: number): string {
  if (status === 404) {
    return "This slide's image is missing. Ask the host to re-upload the presentation.";
  }
  return `Unable to load slide (${status})`;
}

/**
 * AuthService.verifyMagicLink's own messages are already clear for an
 * expired or already-used link ("...has expired" / "...already been used");
 * only the unknown-token case ("No magic link token <token>") leaks a raw
 * token value that means nothing to the person reading it.
 */
export function describeMagicLinkVerifyError(message: string): string {
  if (message.startsWith("No magic link token")) {
    return "This sign-in link is invalid.";
  }
  return message;
}

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

  signUp(email: string, password: string, displayName: string) {
    return request<SessionResponse>("/api/auth/signup", {
      method: "POST",
      body: JSON.stringify({ email, password, displayName }),
    });
  },

  logIn(email: string, password: string) {
    return request<SessionResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
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

  /**
   * M-Templates custom role builder. authSessionToken (from getAuthSession())
   * is required here the same way it is for createRoom — TemplateService
   * derives ownership from it, never from a body field.
   */
  createTemplate(name: string, authSessionToken: string) {
    return request<TemplateSummary>("/api/templates", {
      method: "POST",
      headers: { Authorization: `Bearer ${authSessionToken}` },
      body: JSON.stringify({ name }),
    });
  },

  listMyTemplates(authSessionToken: string) {
    return request<TemplateSummary[]>("/api/templates/mine", {
      headers: { Authorization: `Bearer ${authSessionToken}` },
    });
  },

  getTemplate(templateId: string, authSessionToken: string) {
    return request<TemplateSummary>(`/api/templates/${templateId}`, {
      headers: { Authorization: `Bearer ${authSessionToken}` },
    });
  },

  getTemplateRoles(templateId: string, authSessionToken: string) {
    return request<TemplateRoleDefinition[]>(`/api/templates/${templateId}/roles`, {
      headers: { Authorization: `Bearer ${authSessionToken}` },
    });
  },

  addTemplateRole(
    templateId: string,
    role: { roleKey: string; name: string; isHostRole: boolean; isGuestRole: boolean; maxMembers: number | null },
    authSessionToken: string,
  ) {
    return request<TemplateRoleDefinition>(`/api/templates/${templateId}/roles`, {
      method: "POST",
      headers: { Authorization: `Bearer ${authSessionToken}` },
      body: JSON.stringify(role),
    });
  },

  removeTemplateRole(templateId: string, roleId: string, authSessionToken: string) {
    return request<void>(`/api/templates/${templateId}/roles/${roleId}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${authSessionToken}` },
    });
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
