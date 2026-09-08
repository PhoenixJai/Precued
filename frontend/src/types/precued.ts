export type TemplateId = "sales_call" | "mock_trial" | "ld_debate";

export type HostDisconnectPolicy =
  | "END_CALL"
  | "PERSIST_INDEFINITELY"
  | "PERSIST_FOR_DURATION";

export type RoomStatus = "CREATED" | "ACTIVE" | "ENDED";
export type AccessLevel = "HOST" | "MEMBER";
export type ShareStatus = "ACTIVE" | "ENDED";

export interface MagicLinkResponse {
  token: string;
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
}

export interface RoomParticipantWithGrants extends RoomParticipant {
  leftAt: string | null;
  activeRoomRoleId: string | null;
  activeShareRoleGrantIds: string[];
}

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
  status: ShareStatus;
  startedAt: string;
  endedAt: string | null;
}

export interface ActiveShare {
  id: string;
  label: string;
  roomRoleIds: string[];
}

export interface ShareRoleGrant {
  id: string;
  shareId: string;
  roomRoleId: string;
  grantedAt: string;
  revokedAt: string | null;
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
}

export interface VisibilityGrantMessage {
  livekitIdentity: string;
  allowed: boolean;
  trackSids: string[];
}
