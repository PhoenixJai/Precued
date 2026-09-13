import type { SessionResponse, StoredParticipant } from "../types/precued";

const AUTH_KEY = "precued.auth";
const PARTICIPANT_KEY = "precued.participant";
const GRANT_IDS_KEY = "precued.grantIds";

/**
 * Called on every 401 response (see lib/api.ts's request()) — none of these
 * three are trustworthy once the backend has rejected a request as
 * unauthenticated, and leaving a stale sessionToken in storage would just
 * send it again on the very next request.
 */
export function clearSession() {
  sessionStorage.removeItem(AUTH_KEY);
  sessionStorage.removeItem(PARTICIPANT_KEY);
  sessionStorage.removeItem(GRANT_IDS_KEY);
}

export function saveAuthSession(session: SessionResponse) {
  sessionStorage.setItem(AUTH_KEY, JSON.stringify(session));
}

export function getAuthSession(): SessionResponse | null {
  const raw = sessionStorage.getItem(AUTH_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as SessionResponse;
  } catch {
    return null;
  }
}

export function saveParticipant(participant: StoredParticipant) {
  sessionStorage.setItem(PARTICIPANT_KEY, JSON.stringify(participant));
}

export function getParticipant(): StoredParticipant | null {
  const raw = sessionStorage.getItem(PARTICIPANT_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredParticipant;
  } catch {
    return null;
  }
}

type GrantMap = Record<string, string>;

function grantKey(shareId: string, roomRoleId: string) {
  return `${shareId}:${roomRoleId}`;
}

export function rememberGrantId(shareId: string, roomRoleId: string, grantId: string) {
  const map = getGrantIds();
  map[grantKey(shareId, roomRoleId)] = grantId;
  sessionStorage.setItem(GRANT_IDS_KEY, JSON.stringify(map));
}

export function getGrantId(shareId: string, roomRoleId: string): string | null {
  return getGrantIds()[grantKey(shareId, roomRoleId)] ?? null;
}

export function forgetGrantId(shareId: string, roomRoleId: string) {
  const map = getGrantIds();
  delete map[grantKey(shareId, roomRoleId)];
  sessionStorage.setItem(GRANT_IDS_KEY, JSON.stringify(map));
}

export function clearShareGrantIds(shareId: string) {
  const map = getGrantIds();
  Object.keys(map)
    .filter((key) => key.startsWith(`${shareId}:`))
    .forEach((key) => delete map[key]);
  sessionStorage.setItem(GRANT_IDS_KEY, JSON.stringify(map));
}

function getGrantIds(): GrantMap {
  const raw = sessionStorage.getItem(GRANT_IDS_KEY);
  if (!raw) return {};
  try {
    return JSON.parse(raw) as GrantMap;
  } catch {
    return {};
  }
}
