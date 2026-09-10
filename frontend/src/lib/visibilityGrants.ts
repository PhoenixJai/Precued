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
 */
export const VISIBILITY_GRANTS_TOPIC = "precued.visibility-grants";

export function isServerVisibilityGrant(
  topic: string | undefined,
  sender: { identity: string } | undefined,
): boolean {
  return topic === VISIBILITY_GRANTS_TOPIC && sender === undefined;
}
