import type {
  ActiveShare,
  InviteMode,
  InvitePreview,
  LiveKitTokenResponse,
  MagicLinkResponse,
  ParticipantRoleAssignment,
  PresentationUploadResponse,
  Room,
  RoomInvite,
  RoomParticipant,
  RoomParticipantWithGrants,
  RoomRole,
  SaveTemplateSessionFlowInput,
  SessionFlow,
  SessionResponse,
  Share,
  ShareRoleGrant,
  ShareSlide,
  TemplatePreset,
  TemplateRoleDefinition,
  TemplateSessionFlowDefinition,
  TemplateSummary,
} from "../types/precued";
import { clearSession, getAuthSession, getParticipant } from "./session";
import { rememberTemplateName } from "./templates";

const API_BASE = import.meta.env.VITE_API_BASE ?? "";

type ProblemDetail = {
  title?: string;
  detail?: string;
  status?: number;
};

type RequestAuthMode = "participant" | "none";

async function request<T>(path: string, init?: RequestInit, authMode: RequestAuthMode = "participant"): Promise<T> {
  // Most requests scoped to a Room or to acting as a participant need the
  // RoomParticipant bearer token. Some public endpoints intentionally must
  // NOT receive it, because /api/templates/** treats any supplied bearer as
  // an Account/AuthSession token. Those callers opt out with authMode=none.
  const participant = authMode === "participant" ? getParticipant() : null;
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

export type SlideImageResult =
  | { ok: true; objectUrl: string }
  | { ok: false; status: number };

export function describeSlideImageError(status: number): string {
  if (status === 404) {
    return "This slide's image is missing. Ask the host to re-upload the presentation.";
  }
  return `Unable to load slide (${status})`;
}

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

  createRoom(templateId: string, authSessionToken: string) {
    return request<Room>("/api/rooms", {
      method: "POST",
      headers: { Authorization: `Bearer ${authSessionToken}` },
      body: JSON.stringify({
        templateId,
        hostDisconnectPolicy: "END_CALL",
      }),
    });
  },

  async getRoom(roomId: string) {
    const room = await request<Room>(`/api/rooms/${roomId}`);
    rememberTemplateName(room.templateId, room.templateName);
    return room;
  },

  getSessionFlow(roomId: string) {
    return request<SessionFlow>(`/api/rooms/${roomId}/session-flow`);
  },

  startSessionFlow(roomId: string) {
    return request<SessionFlow>(`/api/rooms/${roomId}/session-flow/start`, { method: "POST" });
  },

  advanceSessionFlow(roomId: string) {
    return request<SessionFlow>(`/api/rooms/${roomId}/session-flow/advance`, { method: "POST" });
  },

  getRoomRoles(roomId: string) {
    return request<RoomRole[]>(`/api/rooms/${roomId}/room-roles`);
  },

  getRoomParticipants(roomId: string) {
    return request<RoomParticipantWithGrants[]>(`/api/rooms/${roomId}/room-participants`);
  },

  listRoomInvites(roomId: string) {
    return request<RoomInvite[]>(`/api/rooms/${roomId}/invites`);
  },

  createRoomInvite(
    roomId: string,
    input: {
      roomRoleId: string;
      mode: InviteMode;
      inviteeEmail?: string | null;
      maxUses?: number | null;
      expiresAt?: string | null;
    },
  ) {
    return request<RoomInvite>(`/api/rooms/${roomId}/invites`, {
      method: "POST",
      body: JSON.stringify(input),
    });
  },

  getInvitePreview(inviteToken: string) {
    return request<InvitePreview>(`/api/invites/${encodeURIComponent(inviteToken)}`, undefined, "none");
  },

  /**
   * Account Holder joins use userId + AuthSession. Guest joins have userId
   * null and must supply a real Invite token; the backend atomically creates
   * the role assignment while consuming that invite.
   */
  joinRoom(
    roomId: string,
    displayName: string,
    userId: string | null,
    authSessionToken?: string,
    inviteToken?: string,
  ) {
    return request<RoomParticipant>("/api/room-participants", {
      method: "POST",
      headers: authSessionToken ? { Authorization: `Bearer ${authSessionToken}` } : undefined,
      body: JSON.stringify({ roomId, userId, displayName, inviteToken }),
    });
  },

  /** Host bootstrap only after PR 9; guest roles are assigned by invite consumption. */
  assignRole(roomParticipantId: string, roomRoleId: string) {
    return request<ParticipantRoleAssignment>("/api/participant-role-assignments", {
      method: "POST",
      body: JSON.stringify({ roomParticipantId, roomRoleId }),
    });
  },

  getLiveKitToken(roomParticipantId: string) {
    return request<LiveKitTokenResponse>(`/api/room-participants/${roomParticipantId}/livekit-token`);
  },

  getPresets(templateId: string) {
    return request<TemplatePreset[]>(`/api/templates/${templateId}/presets`, undefined, "none");
  },

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

  getTemplateSessionFlow(templateId: string, authSessionToken: string) {
    return request<TemplateSessionFlowDefinition>(`/api/templates/${templateId}/session-flow`, {
      headers: { Authorization: `Bearer ${authSessionToken}` },
    });
  },

  saveTemplateSessionFlow(templateId: string, flow: SaveTemplateSessionFlowInput, authSessionToken: string) {
    return request<TemplateSessionFlowDefinition>(`/api/templates/${templateId}/session-flow`, {
      method: "PUT",
      headers: { Authorization: `Bearer ${authSessionToken}` },
      body: JSON.stringify(flow),
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

  startShare(roomId: string, publisherParticipantId: string, appliedPresetId: string | null, label: string) {
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
