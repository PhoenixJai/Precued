package com.precued.engine;

import java.util.List;
import java.util.UUID;

/**
 * Compiles the ShareRoleGrant allow-list (Runtime Rule, Precued_DataModel.md)
 * into live LiveKit track-subscription permissions.
 *
 * This is NOT a pure backend service — see "VisibilityEngine — Interface
 * Spec" in Precued_DataModel.md for the full rationale. In short: LiveKit's
 * server SDK (RoomServiceClient.updateParticipant) only exposes a coarse,
 * room-wide canSubscribe toggle per participant — it cannot express
 * "participant X may see track Y but not track Z". Fine-grained per-track,
 * per-viewer permission only exists via
 * LocalParticipant.setTrackSubscriptionPermissions(...), which is a
 * CLIENT-SIDE call made by the publisher's own client, not something our
 * backend can invoke directly against LiveKit.
 *
 * Critically, that client-side call REPLACES the publisher's entire
 * subscription-permission matrix — it is not additive. A publisher can have
 * more than one active Share at once, plus their own base camera/mic
 * tracks, all subscribed to by potentially different viewers. So the unit
 * of compute-and-push is the PUBLISHER, not the Share: every push must
 * carry the publisher's complete, current permission set (every viewer's
 * access to every one of that publisher's active Shares, plus their base
 * tracks), or it will silently revoke access this backend never intended to
 * touch. The engine is therefore split into two parts:
 *
 * Part A — {@link #computeGrantsForPublisher}: for one publisher
 * (RoomParticipant), unions together, per connected viewer, every
 * currently-allowed track across all of that publisher's active Shares
 * (each Share's slice computed by {@link #computeGrantsForShare}) plus the
 * publisher's base camera/mic tracks, which are always allowed to anyone
 * still connected to the room. {@link #computeGrantsForShare} itself
 * remains DB-only and Share-scoped — it is a building block, not something
 * safe to push to LiveKit on its own. Base-track lookup is a live
 * RoomServiceClient.getParticipant call keyed by TrackInfo.source (CAMERA /
 * MICROPHONE): nothing in this schema persists a publisher's non-Share
 * tracks, and LiveKit itself is the only authoritative source for "what is
 * this participant currently publishing outside of a Share" — so, unlike
 * the Share-scoped slice, this one step of Part A is not DB-only.
 *
 * Part B — {@link #pushGrantsToPublisher}: sends Part A's publisher-wide
 * output to that publisher_participant_id's client over our own channel
 * (LiveKit data message — not a new external dependency). The publisher's
 * client is the one that actually calls
 * setTrackSubscriptionPermissions(false, ...) against LiveKit; our backend's
 * responsibility ends at the push. If that client is disconnected,
 * backgrounded, or slow, the permission change does not take effect until
 * it reconnects/resumes — a real dependency, not an edge case to hand-wave.
 *
 * Triggers (event-driven, never poll or recompute-on-read) — every one of
 * these now resolves to a publisher-wide recompute+push, even the ones
 * historically scoped to a single Share:
 *   - ParticipantRoleAssignment created/revoked -> every active publisher in the room
 *   - ShareRoleGrant created/revoked             -> that Share's publisher
 *   - Share started                              -> that Share's publisher
 *   - Share ended                                -> none (tracks unpublished, permissions moot)
 *   - LiveKit webhook participant_joined/left    -> every active publisher in the room
 *   - LiveKit webhook track_published            -> the publisher of the Share that track belongs to
 *
 * Failure/race handling is fail-closed per participant, not per room: a
 * RoomParticipant with no currently active ParticipantRoleAssignment (e.g.
 * the gap between a revoke and the next assign) is treated as allowed=false
 * for every Share, without touching any other participant's permissions.
 * This requires reassignment to be implemented as two writes — revoke, then
 * assign — with Part A+B re-run after each, not one atomic swap.
 */
public interface VisibilityEngine {

    /**
     * Pure function of current DB state for one Share: no LiveKit call, no
     * side effects. Returns one entry per connected RoomParticipant in the
     * Share's room, with {@code allowed} true iff their currently active
     * RoomRole has an active ShareRoleGrant for this share. A building block
     * for {@link #computeGrantsForPublisher} — its output alone is never
     * safe to push to LiveKit, since doing so would wipe out any other
     * active Share's (or base track's) grants for the same publisher.
     */
    List<ParticipantTrackPermission> computeGrantsForShare(UUID shareId);

    /**
     * Part A. The complete, current permission set for one publisher: for
     * every connected RoomParticipant in the publisher's room, the union of
     * every track that participant is allowed across all of the publisher's
     * active Shares, plus the publisher's base camera/mic tracks (always
     * allowed to anyone still connected). This — never
     * {@link #computeGrantsForShare} alone — is what must be pushed, since
     * LiveKit's client-side setTrackSubscriptionPermissions call replaces
     * the publisher's entire permission matrix rather than patching it.
     */
    List<ParticipantTrackPermission> computeGrantsForPublisher(UUID publisherParticipantId);

    /**
     * Part B. Sends a publisher-wide computed permission list to that
     * publisher's client over our own channel. Does not call LiveKit
     * itself — the publisher's client SDK is what calls
     * setTrackSubscriptionPermissions(false, ...) on receipt. Returns once
     * the push is handed off; delivery and application are the publisher
     * client's responsibility.
     */
    void pushGrantsToPublisher(UUID publisherParticipantId, List<ParticipantTrackPermission> grants);

    /**
     * Runs Part A then Part B for one publisher.
     */
    default void recomputeAndPushForPublisher(UUID publisherParticipantId) {
        pushGrantsToPublisher(publisherParticipantId, computeGrantsForPublisher(publisherParticipantId));
    }

    /**
     * Resolves the given Share to its publisher and runs a full
     * publisher-wide recompute+push — never just that Share's slice. Use
     * for triggers historically scoped to one share: ShareRoleGrant
     * created/revoked, Share started, or a ShareTrack published under it.
     */
    void recomputeAndPushForShare(UUID shareId);

    /**
     * Runs a publisher-wide recompute+push for every distinct publisher
     * with an active Share in a room. Use for triggers scoped to the whole
     * room: a ParticipantRoleAssignment change, or a LiveKit
     * participant_joined/participant_left webhook.
     */
    void recomputeAndPushForRoom(UUID roomId);
}
