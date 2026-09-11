# Precued MVP — Data Model (Sales Call · Mock Trial · LD Debate)

14 tables, one visibility engine (interface specified below — see "VisibilityEngine — Interface Spec"). Verticals (Sales Call, Mock Trial, LD Debate) are rows in `Template`/`TemplateRole`/`TemplatePreset` — no template-specific code anywhere in the schema.

## Design Decisions

1. **Roles are copied from Template into Room at creation, not referenced live.** A `RoomRole` is a snapshot of a `TemplateRole` at the moment the room was created. Editing a template later never corrupts a call already in progress.
2. **Participant identity is stable across role changes.** A `RoomParticipant` is one person's membership in a room, for the room's whole lifetime. Their role is tracked separately in `ParticipantRoleAssignment`, which timestamps when a role starts and ends — so reassigning someone mid-call doesn't create a new identity or lose history.
3. **A Share is a business concept, not a LiveKit track.** One share action (e.g. starting a screen share) can produce more than one track (video + audio). `Share` holds the policy; `ShareTrack` holds the actual track SID(s) underneath it.
4. **Visibility is allow-list policy, compiled at runtime.** `ShareRoleGrant` stores which roles are permitted to see a share. No grant row = never subscribed — not hidden client-side, never sent. A single `VisibilityEngine` reads this policy and compiles it into live LiveKit participant/track permissions whenever a grant, role assignment, or share changes.
5. **Slide-level visibility is a same-slide-for-everyone model, not per-participant navigation.** `Share.current_slide_index` is the one live presenter position all connected participants share. A role either sees that slide or gets the lock state — no participant has an independent slide position. `ShareRoleGrant.share_slide_id` is a nullable refinement of Decision #4's allow-list, not a new mechanism: `NULL` means whole-share grant (existing behavior), a set value means the grant applies only when that slide is current.

## Tables

### User
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| email | string | unique |
| display_name | string | |
| created_at | timestamp | |

### Template
| Field | Type | Notes |
|---|---|---|
| id | string | PK — `sales_call` \| `mock_trial` \| `ld_debate` |
| name | string | display label |
| created_at | timestamp | hardcoded seed data for MVP, no versioning |

### TemplateRole
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| template_id | fk → Template | |
| role_key | string | e.g. `judge`, `jury` |
| name | string | display label |
| is_host_role | bool | who runs the room by default |
| is_guest_role | bool | default `false`. Descriptive/UI-hint only — no runtime logic change (`ShareRoleGrant` already defaults to no-visibility for every role regardless of this flag) |
| max_members | int \| null | null = unlimited (e.g. Jury, Audience) |
| sort_order | int | |

### TemplatePreset
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| template_id | fk → Template | |
| name | string | e.g. "Judge + Jury Only" |
| sort_order | int | |

### TemplatePresetRole
*(join: which TemplateRoles a preset grants)*
| Field | Type | Notes |
|---|---|---|
| preset_id | fk → TemplatePreset | composite PK |
| template_role_id | fk → TemplateRole | composite PK |

### Room
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| template_id | fk → Template | which vertical this call uses |
| created_by_user_id | fk → User | |
| livekit_room_name | string | unique, maps to LiveKit SFU room |
| status | enum | `created` \| `active` \| `ended` |
| host_disconnect_policy | enum | `end_call` \| `persist_indefinitely` \| `persist_for_duration` — default `end_call`; host sets at room creation |
| host_disconnect_grace_seconds | int \| null | only meaningful when policy is `persist_for_duration` |
| created_at | timestamp | |
| ended_at | timestamp \| null | |

### RoomRole
*(copied from TemplateRole when Room is created — this is Decision #1)*
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| room_id | fk → Room | |
| source_template_role_id | fk → TemplateRole \| null | traceability only, not a live reference |
| role_key | string | copied at creation time |
| name | string | copied at creation time |
| is_host_role | bool | copied at creation time |
| max_members | int \| null | copied at creation time |

### RoomParticipant
*(a person's stable membership in a room — this is Decision #2)*
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| room_id | fk → Room | |
| user_id | fk → User \| null | nullable for guest joins (e.g. a client with no account) |
| livekit_identity | string | unique per room, stable across reconnects |
| display_name | string | |
| access_level | enum | `host` \| `member` — administrative control, separate from content role |
| joined_at | timestamp | |
| left_at | timestamp \| null | |

### ParticipantRoleAssignment
*(which RoomRole a RoomParticipant currently holds, with history)*
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| room_participant_id | fk → RoomParticipant | |
| room_role_id | fk → RoomRole | |
| assigned_at | timestamp | |
| revoked_at | timestamp \| null | null = currently active |

MVP rule: at most one active (`revoked_at IS NULL`) assignment per participant at a time.

### Invite
*(pre-assignment layer — a token that resolves to a RoomRole; consuming it creates a ParticipantRoleAssignment)*
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| room_role_id | fk → RoomRole | which role this invite grants on consumption |
| invitee_email | string \| null | set for named invites sent to a specific person; null for open pool links |
| token | string | unique, resolves to `room_role_id` |
| mode | enum | `named` \| `pool` |
| max_uses | int | `named` → 1; `pool` → `RoomRole.max_members` or host-set cap if null |
| uses_count | int | increments on each successful join via this token |
| status | enum | `PENDING` \| `USED` \| `EXPIRED` |
| created_at | timestamp | |
| expires_at | timestamp \| null | |

MVP rule: `status` flips `PENDING → USED` when `uses_count` reaches `max_uses`, applied synchronously on the consuming write. `status` flips `PENDING → EXPIRED` via a background job that scans for `status = PENDING AND expires_at < now()` on a schedule (not checked lazily at join-attempt time) — so `status` is always accurate for any reader (host-facing invite lists, join attempts) without each caller needing to separately check `expires_at`. Reassigning a participant's role later via `ParticipantRoleAssignment` does not modify or revoke the originating `Invite` row — the two are decoupled once consumed.

### Share
*(business concept — one instance of the host sharing something)*
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| room_id | fk → Room | |
| publisher_participant_id | fk → RoomParticipant | must hold a host role |
| applied_preset_id | fk → TemplatePreset \| null | which preset the host picked, for reference |
| label | string | host-entered, e.g. "Exhibit A" |
| kind | enum | `screen` \| `presentation` — **NEW**: `presentation` added for slide-based Shares (Chunk 1). Existing `screen` Shares are unaffected by anything below. |
| status | enum | `active` \| `ended` |
| current_slide_index | int | **NEW**, default `0`. Only meaningful when `kind = presentation` — the presenter's single live slide position; all connected participants are evaluated against this one value, not an individual position each (Decision #5). |
| started_at | timestamp | |
| ended_at | timestamp \| null | |

MVP rule: if the `RoomParticipant` holding `publisher_participant_id` for an active `Share` disconnects, that `Share` transitions to `status = ended` and every one of its `ShareTrack` rows gets `unpublished_at` set. No orphaned "active" Share persists after its publisher leaves, and there is no auto-reassign to a different host-role participant — a new `Share` must be explicitly started to resume.

### ShareSlide
*(**NEW** — one slide belonging to a `presentation`-kind Share; introduced in Chunk 1)*
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| share_id | fk → Share | parent Share; only populated for `kind = presentation` |
| slide_index | int | unique per `share_id` — ordering position, matched against `Share.current_slide_index` |
| image_url | string \| null | nullable in Chunk 1 (schema only, no real content); populated by the import pipeline in a later chunk |
| created_at | timestamp | |

No rows exist for `screen`-kind Shares. This table has zero relationship to `ShareTrack` — a `presentation` Share's actual LiveKit media (if any, e.g. presenter audio) is still tracked via `ShareTrack` exactly as today; `ShareSlide` only carries the visual slide content and its per-slide visibility hook.

### ShareTrack
*(actual LiveKit track(s) under a Share — this is Decision #3)*
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| share_id | fk → Share | |
| livekit_track_sid | string | unique |
| kind | enum | `video` \| `audio` |
| published_at | timestamp | |
| unpublished_at | timestamp \| null | |

### ShareRoleGrant
*(the allow-list — this is Decision #4, extended by Decision #5)*
| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| share_id | fk → Share | |
| room_role_id | fk → RoomRole | a role permitted to view this share |
| share_slide_id | fk → ShareSlide \| null | **NEW**, nullable. `NULL` = whole-share grant, unaffected existing behavior. A set value scopes this grant to only that slide being current — see extended Runtime Rule below. |
| granted_at | timestamp | |
| revoked_at | timestamp \| null | null = currently active |

## Runtime Rule

A `RoomParticipant` receives a `Share`'s tracks **iff** their currently active `ParticipantRoleAssignment` points to a `RoomRole` that has an active `ShareRoleGrant` for that `Share`. No grant means the track is never subscribed — not hidden after delivery.

**Extended for slide-level visibility (Chunk 1):** for a `presentation`-kind Share, a role sees the current slide **iff** it holds an active `ShareRoleGrant` with `share_slide_id IS NULL` (whole-share grant — existing behavior, unaffected) **OR** an active `ShareRoleGrant` with `share_slide_id` matching the `ShareSlide` row at `Share.current_slide_index`. A role with only slide-specific grants sees the lock state on any slide those grants don't cover. `screen`-kind Shares never populate `share_slide_id`, so this extension changes nothing for them.

## VisibilityEngine — Interface Spec

### Why this isn't a single backend call

LiveKit's server SDK (`RoomServiceClient.updateParticipant`) can only set a coarse, room-wide `canSubscribe: bool` per participant — it cannot say "participant X may see track Y but not track Z" when X is otherwise allowed to subscribe. Fine-grained per-track, per-viewer permission is only settable via `LocalParticipant.setTrackSubscriptionPermissions(allParticipantsAllowed: false, participantTrackPermissions: [...])`, called **client-side by the publisher** (the person doing the sharing), not by our backend directly against LiveKit.

Consequence: the VisibilityEngine is **backend compute + a required push to the publisher's client**, not a pure backend service. The publisher's client is the one that actually calls into LiveKit. If that client is disconnected, backgrounded, or slow, permission changes don't take effect until it reconnects/resumes — this is a real dependency, not an edge case to hand-wave.

### Architecture: two parts

**Part A — Compute (backend, pure function of DB state)**

```
compute_grants_for_share(share_id) -> List[ParticipantTrackPermission]

Input:  share_id
Reads:  Share.room_id
        → Share.kind, Share.current_slide_index (if kind = presentation)
        → all RoomParticipants in that room with connection state = connected
        → each participant's currently active ParticipantRoleAssignment (revoked_at IS NULL)
        → active ShareRoleGrants (revoked_at IS NULL) for this share_id,
          each evaluated against share_slide_id per the extended Runtime Rule
        → ShareTracks under this share (video/audio track SIDs)
Output: one entry per connected RoomParticipant:
        {
          livekit_identity: string,
          allowed: bool,        // true iff their active role has a qualifying grant (whole-share, or matching current slide)
          track_sids: [string]  // this Share's ShareTrack.livekit_track_sid values, only if allowed
        }
```

Pure DB read + boolean join, matching the Runtime Rule above (including its slide-level extension). No LiveKit call happens here. This is safely callable as often as needed and is idempotent — same DB state in, same permission list out.

**Part B — Push to publisher + apply (backend → publisher's client → LiveKit)**

**Transport decision: LiveKit data message, `RELIABLE` mode, targeted at the publisher's `participant_identity`.** Considered against a custom app-level websocket; rejected the websocket because the publisher's client is *already* connected to LiveKit by definition (they're the one publishing the Share) — a second connection would mean two independently-failing channels to reason about instead of one, for no benefit here (we're not planning to swap out LiveKit).

**This is not "fire and forget."** LiveKit's own docs are explicit that reliable delivery is best-effort, not guaranteed: a receiver that is temporarily disconnected at the moment the packet is sent will not receive it, and packets are not buffered server-side beyond a limited number of retransmissions. There's also a documented edge case where a participant that has *just* connected can miss a reliable message sent immediately after the `participant_joined` event, because the transport isn't fully ready yet. Net effect: if the publisher's client is briefly down or mid-reconnect when we push, that update is simply gone — LiveKit will not queue and retry it for us later.

Because `compute_grants_for_share` is a pure, idempotent function of DB state (Part A), we don't need our own message queue/retry system to compensate — we only need to guarantee we **re-push current state whenever the publisher (re)connects**, which is already a row in the trigger table below.

```
1. Backend calls compute_grants_for_share(share_id) → permission list
2. Backend sends the list to the Share's publisher_participant_id's client
   via a LiveKit data message (RELIABLE mode, targeted at that one
   participant_identity — not room-wide)
3. Publisher's client SDK calls:
     room.localParticipant.setTrackSubscriptionPermissions(
       false,                        // allParticipantsAllowed = false always
       participantTrackPermissions   // the list from step 1, translated to SDK shape
     )
4. LiveKit SFU stops/starts delivering the track(s) per the new list
```

`allParticipantsAllowed` is always `false` — we never rely on LiveKit's default-allow; every viewer's access is explicit, matching Decision #4 ("no grant row = never subscribed").

### Triggers (event-driven — not poll, not recompute-on-read)

Poll is wrong: needless latency/cost tradeoff with no benefit here. Recompute-on-read is wrong: there is no "read" moment in push media — permissions must be correct *before* a track reaches the wire, not when someone happens to check.

The engine runs Part A+B whenever one of these fires:

| Trigger | Source | Scope of recompute |
|---|---|---|
| `ParticipantRoleAssignment` created or revoked | our DB write (role reassignment) | every active Share in that room |
| `ShareRoleGrant` created or revoked | our DB write (host changes visibility) | that one Share |
| `Share` started | our DB write | that one Share (fresh compute, no prior state) |
| `Share` ended | our DB write | none — tracks unpublished, permissions moot |
| `Share.current_slide_index` changed | our DB write (host advances/rewinds a slide) — **NEW**, Chunk 1 | that one Share only (same pattern as the `ShareRoleGrant` row above, just keyed off a different write path) |
| LiveKit webhook `participant_joined` | LiveKit → our webhook endpoint | every active Share in that room, for that one participant |
| LiveKit webhook `participant_left` | LiveKit → our webhook endpoint | every active Share in that room (drop them from the list) |
| LiveKit webhook `track_published` | LiveKit → our webhook endpoint | the Share that ShareTrack belongs to (attach new track_sid to existing grants) |
| LiveKit webhook `participant_joined`, **specifically for a Share's own publisher reconnecting** | LiveKit → our webhook endpoint | re-push (not just recompute) current state for every active Share that participant publishes — covers the "message sent while they were briefly disconnected" gap, since Part A/B is idempotent and safe to re-run |

Each row above is a discrete, already-observable event — no new infrastructure needed beyond a webhook receiver we need anyway for `Room`/`Share` lifecycle bookkeeping. The last row is not a new webhook type — it's the same `participant_joined` event, with the added rule that if the (re)connecting participant is a `publisher_participant_id` on any active `Share`, we re-push rather than assume our last push landed.

### Failure / race handling — fail closed per participant, not per room

Two options considered:
- **Defer to original role until resolved** — rejected: stale-permissive. A participant who was just revoked keeps seeing content during the gap.
- **Revoke all grants for the room until resolved** — rejected as default: safe, but the blast radius is wrong. One participant's mid-reassignment ambiguity shouldn't blank the share for everyone else mid-demo.

**Adopted: fail closed, scoped to the ambiguous participant only.**

- A `RoomParticipant` with **no currently active `ParticipantRoleAssignment`** (the gap between a revoke and the next assign, however short) is treated identically to holding a role with zero grants — i.e., `allowed: false` for every Share, for that participant only.
- This requires no new schema. It falls directly out of the existing Runtime Rule (`revoked_at IS NULL` join) **provided reassignment is implemented as two separate writes — revoke old, then assign new — with Part A+B re-run after each write**, not as a single atomic "swap" that skips the gap.
- Practically: revoking triggers a recompute that drops that participant from every active Share's allowed list; assigning triggers a second recompute that (if the new role has grants) adds them back. Everyone else's already-confirmed permissions are untouched by either write.

This gives the security property of "revoke all" (never permissive during ambiguity) with the blast radius of "per participant" (doesn't visibly disrupt the rest of the room).

## MVP Template Configs

### Sales Call
Roles: **Sales Rep** (host) · Sales Engineer · Client
Presets: All Roles · Rep + Engineer Only · Rep Only

### Mock Trial
Roles: **Judge** (host) · Jury (multi-member) · Defense · Prosecution
Presets: All Roles · Judge + Jury Only · Judge Only · Defense Only · Prosecution Only

### Lincoln-Douglas Debate
Roles: **Judge** (host) · Affirmative · Negative · Audience (multi-member)
Presets: All Roles · Judge Only · Affirmative Only · Negative Only

Adding a fourth template later means adding rows to `Template`/`TemplateRole`/`TemplatePreset`/`TemplatePresetRole` — zero changes to Room, Share, or the VisibilityEngine.

## Presentations Feature — Chunk Status

Slide-level visibility is being built in sequenced chunks (see project decomposition). This document reflects **Chunk 1 (schema + Runtime Rule extension) only**:

- ✅ Chunk 1 — `Share.kind`/`current_slide_index`, `ShareSlide`, `ShareRoleGrant.share_slide_id`, extended Runtime Rule, new trigger row. Reflected above.
- ⬜ Chunk 2 — PDF import pipeline populates `ShareSlide.image_url` with real content. Not yet reflected; `image_url` remains nullable/placeholder until this lands.
- ⬜ Chunk 3 — Presenter/viewer UI for slide navigation and per-slide visibility controls.
- ⬜ Chunk 4 — PPTX import (via PDF conversion).
- ⬜ Parked — native slide creation/editor. Not scoped; revisit only after Chunks 1–3 are live and validated.
