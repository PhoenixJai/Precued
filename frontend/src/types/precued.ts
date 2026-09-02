// Mirrors Precued_DataModel.md. Keep in sync with backend entities under
// com.precued.entity — if a field is added/renamed there, update here too.

export type TemplateId = "sales_call" | "mock_trial" | "ld_debate";

export interface TemplateRole {
  id: string;
  templateId: TemplateId;
  roleKey: string;
  name: string;
  isHostRole: boolean;
  isGuestRole: boolean;
  maxMembers: number | null;
  sortOrder: number;
}

export interface TemplatePreset {
  id: string;
  templateId: TemplateId;
  name: string;
  sortOrder: number;
  roleIds: string[]; // resolved from TemplatePresetRole join
}

export type RoomStatus = "CREATED" | "ACTIVE" | "ENDED";
export type HostDisconnectPolicy =
  | "END_CALL"
  | "PERSIST_INDEFINITELY"
  | "PERSIST_FOR_DURATION";

export interface Room {
  id: string;
  templateId: TemplateId;
  createdByUserId: string;
  livekitRoomName: string;
  status: RoomStatus;
  hostDisconnectPolicy: HostDisconnectPolicy;
  hostDisconnectGraceSeconds: number | null;
  createdAt: string;
  endedAt: string | null;
}

export interface RoomRole {
  id: string;
  roomId: string;
  sourceTemplateRoleId: string | null;
  roleKey: string;
  name: string;
  isHostRole: boolean;
  maxMembers: number | null;
}

export type InviteMode = "NAMED" | "POOL";
export type InviteStatus = "PENDING" | "USED" | "EXPIRED";

export interface Invite {
  id: string;
  roomRoleId: string;
  inviteeEmail: string | null;
  token: string;
  maxUses: number;
  usesCount: number;
  status: InviteStatus;
  mode: InviteMode;
  createdAt: string;
  expiresAt: string | null;
}

export type AccessLevel = "HOST" | "MEMBER";

export interface RoomParticipant {
  id: string;
  roomId: string;
  userId: string | null; // null = guest
  livekitIdentity: string;
  displayName: string;
  accessLevel: AccessLevel;
  joinedAt: string;
  leftAt: string | null;
  currentRoomRoleId: string | null; // resolved from active ParticipantRoleAssignment
}

export type ShareStatus = "ACTIVE" | "ENDED";

export interface Share {
  id: string;
  roomId: string;
  publisherParticipantId: string;
  appliedPresetId: string | null;
  label: string;
  status: ShareStatus;
  startedAt: string;
  endedAt: string | null;
  grantedRoomRoleIds: string[]; // resolved from active ShareRoleGrant rows
}

/** What the current participant sees for a given Share — drives the
 * "can see share" / "content not shared with your role" UI states. */
export interface ShareVisibilityForSelf {
  shareId: string;
  visible: boolean;
}
