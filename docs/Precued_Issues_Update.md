# Precued — Issue Board Update (paste-ready)
 
New label needed first: `decision` (if not already created from Issue 1's original setup)
 
---
 
## UPDATE — Issue 1 (existing)
 
```
Title: Decide: auth/join flow
[Add as a comment, then close the issue]
 
RESOLVED: Hybrid.
 
- Magic link required for is_host_role participants (accountability — matches
  Share.publisher_participant_id "must hold a host role" constraint).
- Guest-only allowed for all other roles (user_id null, livekit_identity is
  the persistent handle for that room).
- Schema already supports this as designed — user_id nullable on RoomParticipant
  is intentional, not a gap.
 
Scope for M0: email magic link (no password), User row created on first login,
guest join generates RoomParticipant with user_id = null. Out of scope for M0:
social login, account merging, guest-to-user upgrade path (deferred, tracked
separately — see new issue below).
```
 
---
 
## UPDATE — Issue 3 (existing: "User table + minimal auth")
 
```
Title: User table + minimal auth
 
Body:
Implement User (id, email, display_name, created_at). Auth is hybrid (see
Issue 1, resolved): magic link required for host-role participants, optional
for all others.
 
AC:
- A user can request a magic link, click it, and get a stable user_id.
- A guest can join without any account creation.
- Host-role RoomParticipant rows always have user_id populated; non-host rows
  may have user_id null.
```
Labels: `engine` · Milestone: M0
 
---
 
## UPDATE — Issue 5 (existing: "RoomParticipant + livekit_identity wiring")
 
```
Title: RoomParticipant + livekit_identity wiring
 
Body:
Implement RoomParticipant, map to LiveKit participant identity. Guest joins
(user_id null) are an expected, permanent state for non-host roles per the
hybrid auth decision (Issue 1) — not a temporary/unauthenticated placeholder.
 
AC: Reconnecting a participant preserves identity/history, doesn't create a
duplicate row. Guest RoomParticipant rows behave identically to user-backed
rows for all role-assignment and visibility logic.
```
Labels: `engine` · Milestone: M0
 
---
 
## NEW — M1 additions (Core Engine)
 
- [ ] **Invite table + token generation** `engine`
  - Implement `Invite` (id, room_role_id, invitee_email nullable, token, max_uses,
    uses_count, status, mode, created_at, expires_at). Sits in front of
    ParticipantRoleAssignment as a pre-assignment layer.
  - AC: Creating an Invite in `named` mode sets max_uses=1; in `pool` mode sets
    max_uses = RoomRole.max_members (or host-set cap if null). Token is unique
    and resolves to the correct room_role_id.
- [ ] **Invite consumption on join** `engine`
  - On successful join via a valid token: increment uses_count, create the
    ParticipantRoleAssignment pointing at Invite.room_role_id, flip status to
    `used` when uses_count reaches max_uses.
  - AC: A pool link with max_uses=6 accepts exactly 6 joins then rejects a 7th
    with a clear "slot full" state. A named invite is single-use. Reassigning
    a participant's role later via ParticipantRoleAssignment does not modify
    or revoke the originating Invite row.
- [ ] **TemplateRole.is_guest_role field** `config`
  - Add boolean field, default false. Descriptive/UI-hint only — no runtime
    logic change (ShareRoleGrant already defaults to no-visibility for every
    role).
  - AC: Seed data marks Mock Trial's Observer role (if added) as
    is_guest_role=true; LD Debate has no guest role (Audience serves that
    function). Role-assignment screen visually dims/marks guest roles as
    optional.
- [ ] **Room.host_disconnect_policy** `engine`
  - Add `host_disconnect_policy` (enum: end_call | persist_indefinitely |
    persist_for_duration, default end_call) and `host_disconnect_grace_seconds`
    (int, nullable) to Room. Host sets this at room creation.
  - AC: When the connected RoomParticipant with access_level=host disconnects:
    end_call → Room.status = ended immediately; persist_indefinitely → room
    stays open, no auto-transition; persist_for_duration → room stays open for
    grace_seconds, then auto-transitions to ended if no host reconnects.
- [ ] **Share auto-end on publisher disconnect** `engine`
  - When the RoomParticipant holding publisher_participant_id for an active
    Share disconnects, transition that Share to status=ended and set
    unpublished_at on all its ShareTrack rows.
  - AC: No orphaned "active" Share persists after its publisher leaves. A new
    Share must be explicitly started to resume — no auto-reassign to a
    different host-role participant.
---
 
## NEW — M2 additions (Sales Template — role-assignment UI)
 
- [ ] **Per-role invite mode toggle (named vs. pool)** `ui`
  - On the role-assignment screen, each RoomRole row defaults to named or pool
    based on TemplateRole.max_members (max_members=1 → named default;
    multi-seat/null → pool default). Host can flip any individual row.
  - AC: Sales Call's Client/Sales Engineer default to named; Mock Trial's Jury
    and LD Debate's Audience default to pool. Flipping the toggle changes
    Invite.mode and recalculates max_uses accordingly.
---
 
## NEW — standalone decision-gate issue (resolved on creation)
 
```
Title: Decide: guest-to-user upgrade path
 
Body:
Deferred, not solved. A guest RoomParticipant (user_id null) currently has no
path to later claim/merge into a User account. Flagging as an explicit gap,
not silently dropped.
 
Not a blocker for M0–M4 core build. Revisit at S1/S2 if repeat-guest patterns
emerge (e.g. the same person guest-joining multiple Sales Call rooms without
ever getting a persistent identity).
```
Labels: `decision` · No milestone (or a lightweight backlog/S2 tag)