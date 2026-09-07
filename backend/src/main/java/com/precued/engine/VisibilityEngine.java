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
 * The engine is therefore split into two parts:
 *
 * Part A — {@link #computeGrantsForShare}: pure backend compute, DB-only,
 * no LiveKit call. Reads Share -> Room -> connected RoomParticipants ->
 * each participant's active ParticipantRoleAssignment -> active
 * ShareRoleGrants for the share -> the share's ShareTracks, and joins them
 * per the Runtime Rule. Idempotent and safe to call as often as needed.
 *
 * Part B — {@link #pushGrantsToPublisher}: sends Part A's output to the
 * Share's publisher_participant_id's client over our own channel (LiveKit
 * data message or existing websocket — not a new external dependency). The
 * publisher's client is the one that actually calls
 * setTrackSubscriptionPermissions(false, ...) against LiveKit; our backend's
 * responsibility ends at the push. If that client is disconnected,
 * backgrounded, or slow, the permission change does not take effect until
 * it reconnects/resumes — a real dependency, not an edge case to hand-wave.
 *
 * Triggers (event-driven, never poll or recompute-on-read):
 *   - ParticipantRoleAssignment created/revoked -> every active Share in the room
 *   - ShareRoleGrant created/revoked             -> that one Share
 *   - Share started                              -> that one Share
 *   - Share ended                                -> none (tracks unpublished, permissions moot)
 *   - LiveKit webhook participant_joined/left    -> every active Share in the room
 *   - LiveKit webhook track_published            -> the Share that track belongs to
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
     * Part A. Pure function of current DB state for one Share: no LiveKit
     * call, no side effects. Returns one entry per connected RoomParticipant
     * in the Share's room, with {@code allowed} true iff their currently
     * active RoomRole has an active ShareRoleGrant for this share.
     */
    List<ParticipantTrackPermission> computeGrantsForShare(UUID shareId);

    /**
     * Part B. Sends a computed permission list to the Share's publisher's
     * client over our own channel. Does not call LiveKit itself — the
     * publisher's client SDK is what calls
     * setTrackSubscriptionPermissions(false, ...) on receipt. Returns once
     * the push is handed off; delivery and application are the publisher
     * client's responsibility.
     */
    void pushGrantsToPublisher(UUID shareId, List<ParticipantTrackPermission> grants);

    /**
     * Runs Part A then Part B for a single Share. Use for triggers scoped
     * to one share: ShareRoleGrant created/revoked, Share started, or a
     * ShareTrack published under it.
     */
    default void recomputeAndPushForShare(UUID shareId) {
        pushGrantsToPublisher(shareId, computeGrantsForShare(shareId));
    }

    /**
     * Runs Part A+B for every active Share in a room. Use for triggers
     * scoped to the whole room: a ParticipantRoleAssignment change, or a
     * LiveKit participant_joined/participant_left webhook.
     */
    void recomputeAndPushForRoom(UUID roomId);
}
