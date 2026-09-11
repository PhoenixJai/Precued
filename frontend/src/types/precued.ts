export type TemplateId = "sales_call" | "mock_trial" | "ld_debate";

export type HostDisconnectPolicy =
  | "END_CALL"
  | "PERSIST_INDEFINITELY"
  | "PERSIST_FOR_DURATION";

export type RoomStatus = "CREATED" | "ACTIVE" | "ENDED";
export type AccessLevel = "HOST" | "MEMBER";
export type ShareStatus = "ACTIVE" | "ENDED";
export type ShareKind = "SCREEN" | "PRESENTATION";

export interface MagicLinkResponse {
  expiresAt: string;
}

export interface SessionResponse {
  sessionToken: string;
  userId: string;
  email: string;
  expiresAt: string;
}

export interface Room {
  id: string;
  templateId: TemplateId;
  createdByUserId: string;
  livekitRoomName: string;
  status: RoomStatus;
  hostDisconnectPolicy: HostDisconnectPolicy;
  createdAt: string;
}

export interface RoomRole {
  id: string;
  roomId: string;
  roleKey: string;
  name: string;
  isHostRole: boolean;
  maxMembers: number | null;
}

export interface RoomParticipant {
  id: string;
  roomId: string;
  userId: string | null;
  livekitIdentity: string;
  displayName: string;
  accessLevel: AccessLevel;
  joinedAt: string;
  /** Bearer credential for every subsequent request scoped to this participant or their Room. */
  sessionToken: string;
}

/**
 * Omits userId: this lists OTHER participants in the room (no host
 * restriction on that endpoint), and another participant's internal User
 * id has no legitimate reason to be visible to you — see
 * RoomParticipantWithGrantsResponse (backend) for why leaking it mattered.
 */
export type RoomParticipantWithGrants = Omit<RoomParticipant, "userId"> & {
  leftAt: string | null;
  activeRoomRoleId: string | null;
  activeShareRoleGrantIds: string[];
};

export interface ParticipantRoleAssignment {
  id: string;
  roomParticipantId: string;
  roomRoleId: string;
  assignedAt: string;
  revokedAt: string | null;
}

export interface TemplatePreset {
  id: string;
  templateId: TemplateId;
  name: string;
  sortOrder: number;
  roleKeys: string[];
}

export interface Share {
  id: string;
  roomId: string;
  publisherParticipantId: string;
  appliedPresetId: string | null;
  label: string;
  kind: ShareKind;
  currentSlideIndex: number;
  status: ShareStatus;
  startedAt: string;
  endedAt: string | null;
}

export interface ActiveShare {
  id: string;
  label: string;
  kind: ShareKind;
  currentSlideIndex: number;
  roomRoleIds: string[];
}

export interface ShareRoleGrant {
  id: string;
  shareId: string;
  roomRoleId: string;
  /** null = whole-share grant; a set value scopes this grant to only that slide. */
  shareSlideId: string | null;
  grantedAt: string;
  revokedAt: string | null;
}

/** imageUrl is this backend's own proxy path (e.g. /api/shares/{shareId}/slides/{index}/image), never a raw storage URL. */
export interface ShareSlide {
  id: string;
  slideIndex: number;
  imageUrl: string;
}

export interface PresentationUploadResponse {
  shareId: string;
  roomId: string;
  publisherParticipantId: string;
  label: string;
  kind: ShareKind;
  currentSlideIndex: number;
  slides: ShareSlide[];
}

export interface LiveKitTokenResponse {
  token: string;
  livekitUrl: string;
  roomName: string;
  identity: string;
}

export interface StoredParticipant {
  id: string;
  roomId: string;
  roomRoleId: string;
  roleKey: string;
  roleName: string;
  isHost: boolean;
  displayName: string;
  userId: string | null;
  /** Sent as "Authorization: Bearer <sessionToken>" on every request — see lib/api.ts. */
  sessionToken: string;
}

export interface VisibilityGrantMessage {
  livekitIdentity: string;
  allowed: boolean;
  trackSids: string[];
}
