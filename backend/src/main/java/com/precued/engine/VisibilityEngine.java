package com.precued.engine;

import java.util.UUID;

/**
 * Runtime Rule (from Precued_DataModel.md):
 *
 * A RoomParticipant receives a Share's tracks IFF their currently active
 * ParticipantRoleAssignment points to a RoomRole that has an active
 * ShareRoleGrant for that Share.
 *
 * This is re-evaluated and recompiled into live LiveKit track-subscription
 * permissions whenever any of the following change:
 *   1. A ParticipantRoleAssignment is created or revoked (role reassignment)
 *   2. A ShareRoleGrant is created or revoked (host changes visibility)
 *   3. A participant's connection state changes (join/leave/reconnect)
 *
 * No grant row = never subscribed — this must be enforced server-side via
 * LiveKit's track subscription permissions (io.livekit TrackSubscriptionPermission
 * / participant permission update), NOT by hiding tracks client-side. A
 * client-side hide is not a privacy guarantee; an unsubscribed track that
 * was never sent is.
 *
 * M1 implementation notes:
 * - recompileForRoom() should be called from three triggers: role assignment
 *   service, share-grant service, and LiveKit webhook handlers (participant
 *   connected/disconnected).
 * - Each recompile should be idempotent and diff-based against current LiveKit
 *   state where possible, to avoid unnecessary permission-update calls under
 *   load (e.g. many rapid grant toggles in Mock Trial's Judge + Jury preset
 *   switching).
 * - Debounce recompiles that land within the same event tick (e.g. applying
 *   a preset creates/revokes several ShareRoleGrant rows at once — one
 *   recompile per preset application, not one per row).
 */
public interface VisibilityEngine {

    /**
     * Recompute and push live LiveKit track-subscription permissions for
     * every connected participant in a room, based on current
     * ParticipantRoleAssignment + ShareRoleGrant state.
     */
    void recompileForRoom(UUID roomId);

    /**
     * Narrower recompile scoped to a single Share — use when only that
     * Share's grants changed (host tagged/untagged roles), to avoid a
     * full-room recompile.
     */
    void recompileForShare(UUID shareId);

    /**
     * Narrower recompile scoped to a single participant — use when only
     * their role assignment changed (host reassigned them mid-call).
     */
    void recompileForParticipant(UUID roomParticipantId);
}
