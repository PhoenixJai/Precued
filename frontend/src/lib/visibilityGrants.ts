import type { ActiveShare, RoomParticipantWithGrants, VisibilityGrantMessage } from "../types/precued";

/**
 * The visibility-grants push is server-only: VisibilityEngineImpl (backend)
 * sends it via RoomServiceClient's server API, which every LiveKit data
 * packet from a real connected participant can never produce — the SFU
 * stamps each client-published packet with that connection's own
 * authenticated identity (participant tokens can't set canPublishData
 * anyway; see LiveKitTokenService), while a server-API push carries no
 * participant identity at all. livekit-client resolves DataReceived's
 * sender by looking that identity up among connected remote participants,
 * so a genuine push always arrives with `sender` undefined, and a forged
 * message from any connected guest or host always arrives with a real,
 * resolvable `sender`. That's the actual trust boundary here — not a
 * reserved identity string, which would require the backend to hold a
 * LiveKit participant connection it doesn't have.
 *
 * That said, "sender undefined" isn't purely a server-origin signal: it's
 * also what a genuine participant's message would look like during a brief
 * window where this client's local participant list lags the SFU (a
 * reconnect grace period, or a race right at another participant's initial
 * join) — see computeLocalVisibilityGrants below for how that residual gap
 * is closed regardless of which case it is.
 */
export const VISIBILITY_GRANTS_TOPIC = "precued.visibility-grants";

export function isServerVisibilityGrant(
  topic: string | undefined,
  sender: { identity: string } | undefined,
): boolean {
  return topic === VISIBILITY_GRANTS_TOPIC && sender === undefined;
}

/**
 * Independently re-derives the same permissions VisibilityEngineImpl
 * computes, from data this client already polls every POLL_MS over the
 * authenticated REST API (unforgeable — behind the session-token
 * interceptor), plus the local participant's own track sids. Call this
 * after every poll, regardless of whether a data-message push was also
 * received: it makes any push purely a latency optimization rather than a
 * trust decision — a push that were somehow accepted from anyone other
 * than the server (forged, or a same-tick race on isServerVisibilityGrant)
 * self-corrects within one poll interval instead of persisting.
 *
 * Mirrors VisibilityEngineImpl.computeGrantsForPublisher: base (camera/mic)
 * tracks are always allowed to every still-connected participant; the
 * current share's tracks are added only for a participant whose active
 * role is one of that share's granted roles.
 */
export function computeLocalVisibilityGrants(
  participants: RoomParticipantWithGrants[],
  currentShare: ActiveShare | null,
  baseTrackSids: string[],
  shareTrackSids: string[],
): VisibilityGrantMessage[] {
  return participants
    .filter((participant) => !participant.leftAt)
    .map((participant) => {
      const canSeeShare = Boolean(
        currentShare
          && participant.activeRoomRoleId
          && currentShare.roomRoleIds.includes(participant.activeRoomRoleId),
      );
      const trackSids = [...baseTrackSids, ...(canSeeShare ? shareTrackSids : [])];
      return { livekitIdentity: participant.livekitIdentity, allowed: trackSids.length > 0, trackSids };
    });
}

export function toTrackSubscriptionPermissions(grants: VisibilityGrantMessage[]) {
  return grants.map((grant) => ({
    participantIdentity: grant.livekitIdentity,
    allowAll: false,
    allowedTrackSids: grant.allowed ? grant.trackSids : [],
  }));
}
