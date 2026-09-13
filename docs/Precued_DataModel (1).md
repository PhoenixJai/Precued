# Precued — Data Model

Current schema + planned optional Session Flow extension.

Precued is template-driven: Sales Call, Mock Trial, Lincoln-Douglas Debate, and user-created templates are configuration, not separate application architectures.

**Current database:** 16 tables through migration V7.

**Planned Session Flow extension:** 4 additional tables plus small additions to `Template`, `Room`, and potentially `RoomRole`.

---

## Model Status Legend

- **CURRENT** — exists in the repository/database now.
- **PLANNED** — approved near-future design; not yet implemented.
- **PENDING ENFORCEMENT** — schema/model exists, but the full runtime behavior is not yet wired.

---

# Design Decisions

## 1. Template configuration is snapshotted into a Room

A live Room must not depend on mutable template configuration.

Current role behavior:

    TemplateRole → RoomRole

When a Room is created, its TemplateRoles are copied into RoomRoles.

Editing the template later must not change an existing Room.

The same pattern will be used for Session Flow:

    TemplateStage → RoomStage

A Room therefore contains the runtime snapshot required to continue operating even if its source template is later edited.

---

## 2. Session Flow is optional

**PLANNED**

Not every Precued interaction requires formal stages.

A Template may contain:

- roles only;
- roles + visibility behavior;
- roles + visibility + Session Flow;
- or increasingly sophisticated combinations later.

`Template.session_flow_enabled` determines whether structured stage progression is enabled by default.

Stage configuration may remain saved while Session Flow is disabled.

At Room creation, the Template's setting is copied to:

`Room.session_flow_enabled`

This prevents later edits to the Template from changing an existing Room.

The Room-level field also leaves room for a future per-session override.

---

## 3. A Stage does not inherently require a timer

**PLANNED**

A stage may be timed or untimed.

`duration_seconds = NULL`

means:

> Untimed stage.

Examples:

- Defense Opening — 120 seconds
- Judge Questions — untimed
- Jury Deliberation — 600 seconds

Timing is therefore an optional property of a Stage, not the definition of a Stage.

---

## 4. Runtime stage progression is manual in v1

**PLANNED**

The host explicitly starts Session Flow and advances between stages.

A timer reaching zero does **not** automatically advance the Room.

A timed stage remains `ACTIVE` after expiration until the host advances.

Initial state:

    PENDING → ACTIVE → COMPLETED

v1 does not support:

- automatic advancement;
- branching;
- arbitrary skipping;
- going backward;
- pause/resume;
- conditional transitions.

Those remain future workflow capabilities.

---

## 5. Participant identity is stable across role changes

A `RoomParticipant` represents one person's membership in a Room for that Room's lifetime.

Their current role is separate:

    RoomParticipant
        ↓
    ParticipantRoleAssignment
        ↓
    RoomRole

Changing a person's role revokes the old assignment and creates a new assignment rather than creating a new participant identity.

This preserves role history.

---

## 6. A Share is a business concept, not a LiveKit track

One Share action may produce multiple LiveKit tracks.

For example, one screen share may publish:

- a video track;
- an audio track.

`Share` contains the business/policy state.

`ShareTrack` contains the actual LiveKit track SID records underneath it.

---

## 7. Visibility is an allow-list

`ShareRoleGrant` defines which RoomRoles may receive shared content.

No qualifying grant means the participant is not permitted to receive the Share.

Precued does not rely on merely hiding unauthorized content after delivery.

The `VisibilityEngine` computes permissions from Room state and pushes the resulting track-subscription permissions to the publisher's LiveKit client.

---

## 8. Slide visibility refines the same allow-list

A presentation uses one presenter-controlled current slide:

`Share.current_slide_index`

Participants do not navigate independently.

For a presentation Share, a role may have:

- a whole-share grant: `share_slide_id IS NULL`; or
- a slide-specific grant for the currently active `ShareSlide`.

The participant either sees the presenter's current slide or sees the locked/unavailable state.

---

## 9. Runtime Session Flow uses Room state, not Template state

**PLANNED**

After Room creation, progression uses only:

- `Room`
- `RoomStage`
- `RoomStageRole`
- `RoomRole`

The runtime engine does not need to re-read TemplateStage configuration to operate the live session.

`source_template_stage_id` exists only for traceability.

---

## 10. At most one RoomStage may be active

**PLANNED**

A Room may have at most one:

`RoomStage.status = ACTIVE`

at a time.

This should be protected by a Postgres partial unique index.

Conceptually:

    CREATE UNIQUE INDEX idx_room_stage_one_active
        ON room_stage(room_id)
        WHERE status = 'ACTIVE';

The application therefore does not need a duplicated `Room.current_stage_id` field.

The active stage is derived directly from RoomStage state.

---

# CURRENT TABLES

# Identity / Authentication

## User (`app_user`)

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| email | string | unique |
| display_name | string | |
| password_hash | string \| null | bcrypt/encoded account password; nullable for older/magic-link-created users |
| created_at | timestamp | |

Account holders currently use email/password signup and login.

---

## AuthSession

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| user_id | fk → User | |
| token | string | unique opaque bearer token |
| created_at | timestamp | |
| expires_at | timestamp | |

Both successful password authentication and magic-link verification issue an `AuthSession`.

This is the authenticated account session used by account-scoped APIs such as Room creation and custom Template ownership.

---

## MagicLinkToken

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| email | string | |
| token | string | unique, single-use bearer credential |
| created_at | timestamp | |
| expires_at | timestamp | |
| used_at | timestamp \| null | null until successfully consumed |

Magic-link support still exists alongside account/password authentication.

---

# Config-Time Template Model

## Template

| Field | Type | Notes |
|---|---|---|
| id | string | PK. Built-ins use stable slugs such as `sales_call`, `mock_trial`, `ld_debate`; custom templates currently use generated UUID strings |
| name | string | display label |
| created_by_user_id | fk → User \| null | `NULL` = built-in template; non-null = private custom template owned by that User |
| session_flow_enabled | bool | **PLANNED**, default false for new custom templates |
| created_at | timestamp | |

### Template ownership

Built-in templates:

    created_by_user_id = NULL

They are public/read-only configuration.

Custom templates:

    created_by_user_id = <owner User>

They are private-by-default and mutable only by their creator.

Custom Template + custom role creation already exists.

Before custom Templates are exposed as launchable Rooms everywhere, `RoomService#create` must apply the same ownership/visibility rule as `TemplateService`; it currently loads the Template directly by ID.

---

## TemplateRole

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| template_id | fk → Template | |
| role_key | string | stable key, e.g. `judge`, `jury`, `client` |
| name | string | display label |
| is_host_role | bool | host/facilitator role |
| is_guest_role | bool | default false; descriptive/UI hint |
| max_members | int \| null | null = unlimited |
| sort_order | int | display/order position |

Current custom-template behavior allows creators to define roles.

A custom Template may contain only one host role.

### Planned RoomRole consistency change

If `is_guest_role` is intended to remain visible in runtime role-assignment UI, it should also be copied into `RoomRole` when the Room is created.

---

## TemplatePreset

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| template_id | fk → Template | |
| name | string | e.g. `Judge + Jury Only` |
| sort_order | int | |

---

## TemplatePresetRole

Join table defining which TemplateRoles belong to a TemplatePreset.

| Field | Type | Notes |
|---|---|---|
| preset_id | fk → TemplatePreset | composite PK |
| template_role_id | fk → TemplateRole | composite PK |

---

# Runtime Room Model

## Room

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| template_id | fk → Template | source Template |
| created_by_user_id | fk → User | Room creator |
| livekit_room_name | string | unique |
| status | enum | `CREATED` \| `ACTIVE` \| `ENDED` |
| host_disconnect_policy | enum | `END_CALL` \| `PERSIST_INDEFINITELY` \| `PERSIST_FOR_DURATION` |
| host_disconnect_grace_seconds | int \| null | meaningful only for `PERSIST_FOR_DURATION` |
| session_flow_enabled | bool | **PLANNED**, copied from Template at Room creation |
| created_at | timestamp | |
| ended_at | timestamp \| null | |

### Host-disconnect implementation status

`host_disconnect_policy` and `host_disconnect_grace_seconds` exist in the schema/model.

**PENDING ENFORCEMENT:** the current LiveKit `participant_left` webhook does not yet implement the full host-disconnect policy state machine.

---

## RoomRole

Snapshot of a TemplateRole.

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| room_id | fk → Room | |
| source_template_role_id | fk → TemplateRole \| null | traceability only |
| role_key | string | copied at Room creation |
| name | string | copied at Room creation |
| is_host_role | bool | copied at Room creation |
| is_guest_role | bool | **PLANNED consistency addition** if runtime UI needs the TemplateRole hint |
| max_members | int \| null | copied at Room creation |

### Snapshot FK cleanup

The model says `source_template_role_id` is traceability-only.

To make the database actually honor that contract, the FK should use:

    ON DELETE SET NULL

Deleting/editing the source TemplateRole must never corrupt or delete a RoomRole snapshot.

---

## Invite

Invite schema exists as the planned pre-assignment layer.

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| room_role_id | fk → RoomRole | role granted by the invite |
| invitee_email | string \| null | named invite if set; null for pool/open link |
| token | string | unique |
| mode | enum | `NAMED` \| `POOL` |
| max_uses | int | usage cap |
| uses_count | int | successful uses |
| status | enum | `PENDING` \| `USED` \| `EXPIRED` |
| created_at | timestamp | |
| expires_at | timestamp \| null | |

**PENDING ENFORCEMENT / PRODUCT FLOW:** the table and repository exist, but the full invite issuance/consumption/expiration workflow is not yet wired into the current frontend/runtime join path.

A future invite-consumption implementation may add:

`ParticipantRoleAssignment.source_invite_id`

if Precued wants durable historical traceability from an assignment back to the Invite that created it.

---

## RoomParticipant

A person's stable membership in a Room.

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| room_id | fk → Room | |
| user_id | fk → User \| null | nullable for guest participants |
| livekit_identity | string | unique within Room |
| display_name | string | |
| access_level | enum | `HOST` \| `MEMBER` — administrative access, separate from content role |
| joined_at | timestamp | |
| left_at | timestamp \| null | null while considered connected |
| session_token | string \| null | opaque participant-session bearer token; application code issues one for current joins |

The participant session token protects Room/participant-scoped APIs independently of account-level `AuthSession`.

---

## ParticipantRoleAssignment

Tracks which RoomRole a RoomParticipant holds over time.

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| room_participant_id | fk → RoomParticipant | |
| room_role_id | fk → RoomRole | |
| assigned_at | timestamp | |
| revoked_at | timestamp \| null | null = active |

MVP invariant:

> At most one active assignment per RoomParticipant.

Implemented through a Postgres partial unique index over:

`room_participant_id WHERE revoked_at IS NULL`

A participant temporarily holding no active assignment fails closed for Share visibility.

---

# Share / Visibility Model

## Share

One logical sharing action.

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| room_id | fk → Room | |
| publisher_participant_id | fk → RoomParticipant | current implementation requires publisher to hold host role |
| applied_preset_id | fk → TemplatePreset \| null | reference to selected preset |
| label | string | e.g. `Exhibit A` |
| kind | enum | `SCREEN` \| `PRESENTATION` |
| status | enum | `ACTIVE` \| `ENDED` |
| current_slide_index | int | default 0; meaningful for `PRESENTATION` |
| started_at | timestamp | |
| ended_at | timestamp \| null | |

### Publisher disconnect rule

Intended rule:

> If the publisher disconnects, the active Share should end and its active ShareTracks should receive `unpublished_at`.

**PENDING ENFORCEMENT:** the current `participant_left` webhook marks the RoomParticipant as left and recomputes visibility, but does not yet automatically transition all Shares published by that participant to `ENDED`.

Explicit host-driven Share ending is implemented.

---

## ShareSlide

One visual slide belonging to a `PRESENTATION` Share.

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| share_id | fk → Share | |
| slide_index | int | unique within Share |
| image_url | string \| null | storage key/path for rendered slide image |
| created_at | timestamp | |

Current PDF presentation upload renders pages into images, stores them, and creates ShareSlide rows.

No ShareSlide rows exist for normal screen Shares.

---

## ShareTrack

LiveKit tracks underneath a Share.

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| share_id | fk → Share | |
| livekit_track_sid | string | unique |
| kind | enum | `VIDEO` \| `AUDIO` |
| published_at | timestamp | |
| unpublished_at | timestamp \| null | |

---

## ShareRoleGrant

Allow-list policy.

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| share_id | fk → Share | |
| room_role_id | fk → RoomRole | |
| share_slide_id | fk → ShareSlide \| null | null = whole-share grant; set = slide-specific grant |
| granted_at | timestamp | |
| revoked_at | timestamp \| null | null = active |

---

# Current Visibility Rule

A connected RoomParticipant receives a Share's permitted tracks iff:

1. the participant has an active `ParticipantRoleAssignment`;
2. that assignment points to a RoomRole;
3. that RoomRole has a qualifying active ShareRoleGrant.

No qualifying grant means access is denied.

For `SCREEN` Shares:

> any active grant for the participant's RoomRole qualifies.

For `PRESENTATION` Shares:

> a role qualifies if it has either an active whole-share grant (`share_slide_id IS NULL`) or an active grant matching the ShareSlide at `Share.current_slide_index`.

A participant with only slide-specific grants receives the locked state on slides outside those grants.

---

# VisibilityEngine — Current Architecture

Precued's fine-grained visibility cannot be implemented solely with the backend's coarse room-wide LiveKit subscription flag.

The VisibilityEngine therefore has two responsibilities:

## Compute

`computeGrantsForShare(shareId)` evaluates:

- the Share;
- active ShareTracks;
- RoomParticipants where `left_at IS NULL`;
- each participant's active ParticipantRoleAssignment;
- active ShareRoleGrants;
- current slide state for presentations.

It produces viewer identities and the Share track SIDs each viewer is allowed to receive.

The current implementation also computes a **publisher-wide union** across all active Shares from the same publisher before permissions are pushed.

This is necessary because LiveKit's publisher-side subscription-permission call represents the publisher's overall permission set rather than one independent permission object per Precued Share.

Base camera/microphone track SIDs are fetched from LiveKit and unioned into the publisher's permission set so normal call media remains available independently of Share visibility.

## Push

The backend sends the computed permission payload to the Share publisher using a targeted LiveKit reliable data message.

The publisher's client applies:

    room.localParticipant.setTrackSubscriptionPermissions(...)

Precued always uses explicit participant/track permission state rather than relying on default-allow for protected Shares.

Because LiveKit reliable data delivery is not a durable message queue, Precued re-pushes current permission state when a publisher reconnects.

---

# VisibilityEngine Triggers

Recompute/push currently occurs for events including:

| Trigger | Scope |
|---|---|
| ParticipantRoleAssignment create/revoke | active Shares in Room |
| ShareRoleGrant create/revoke | affected publisher/Share |
| Share started | new Share |
| Share.current_slide_index changed | affected presentation Share |
| LiveKit participant joined | Room; publisher reconnects receive explicit re-push |
| LiveKit participant left | Room |
| LiveKit track published | associated Share |
| LiveKit track unpublished | associated Share |

The engine remains event-driven rather than polling media state.

---

# PLANNED — Optional Session Flow

Session Flow adds the third major Template dimension:

    WHO  → Roles
    WHAT → Visibility / content access
    WHEN → Session Flow

It is optional.

A Template without Session Flow continues to behave exactly like Precued does today.

---

## Template.session_flow_enabled

**PLANNED field**

| Field | Type | Notes |
|---|---|---|
| session_flow_enabled | bool | default false; enables structured stage progression by default |

A Template may retain TemplateStage rows while this flag is false.

This lets a creator temporarily disable structured progression without deleting their configuration.

---

## TemplateStage

**PLANNED NEW TABLE**

Defines a reusable stage in a Template.

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| template_id | fk → Template | |
| stage_key | string | stable machine key, e.g. `defense_opening` |
| name | string | display label |
| sort_order | int | progression order |
| duration_seconds | int \| null | null = untimed |

Recommended constraints:

    UNIQUE(template_id, stage_key)
    UNIQUE(template_id, sort_order)

and:

    duration_seconds IS NULL OR duration_seconds > 0

A TemplateStage may have zero, one, or multiple active roles.

Zero-role stages remain useful for concepts such as intermission or general transition periods.

---

## TemplateStageRole

**PLANNED NEW TABLE**

Join table defining which roles have the floor / are active during a stage.

| Field | Type | Notes |
|---|---|---|
| template_stage_id | fk → TemplateStage | composite PK |
| template_role_id | fk → TemplateRole | composite PK |

This relationship is descriptive session structure in v1.

It does **not** automatically mute every other role or modify LiveKit publication permissions.

Those behaviors may become separate stage actions later.

If a custom TemplateRole is referenced by a TemplateStage, deleting the role should be rejected until the creator removes or changes its stage references.

---

## Room.session_flow_enabled

**PLANNED field**

| Field | Type | Notes |
|---|---|---|
| session_flow_enabled | bool | copied from Template at Room creation |

This is the Room's independent runtime setting.

Later, Room creation UI may allow the host to override the Template default for one session.

---

## RoomStage

**PLANNED NEW TABLE**

Runtime snapshot of TemplateStage.

| Field | Type | Notes |
|---|---|---|
| id | uuid | PK |
| room_id | fk → Room | |
| source_template_stage_id | fk → TemplateStage \| null | traceability only; use `ON DELETE SET NULL` |
| stage_key | string | copied |
| name | string | copied |
| sort_order | int | copied |
| duration_seconds | int \| null | copied |
| status | enum | `PENDING` \| `ACTIVE` \| `COMPLETED` |
| started_at | timestamp \| null | set when stage becomes active |
| completed_at | timestamp \| null | set when host advances |

Recommended constraints:

    UNIQUE(room_id, stage_key)
    UNIQUE(room_id, sort_order)

and:

    CREATE UNIQUE INDEX idx_room_stage_one_active
        ON room_stage(room_id)
        WHERE status = 'ACTIVE';

No `Room.current_stage_id` is required.

---

## RoomStageRole

**PLANNED NEW TABLE**

Runtime snapshot of TemplateStageRole.

| Field | Type | Notes |
|---|---|---|
| room_stage_id | fk → RoomStage | composite PK |
| room_role_id | fk → RoomRole | composite PK |

At Room creation:

    TemplateRole.id → RoomRole.id

is mapped first.

Then each TemplateStageRole is translated into the matching RoomStageRole relationship.

Runtime Session Flow never needs to depend on TemplateRole.

---

# Session Flow Snapshot Behavior

When a Room is created:

1. create Room;
2. copy `Template.session_flow_enabled` → `Room.session_flow_enabled`;
3. copy TemplateRoles → RoomRoles;
4. create a TemplateRole → RoomRole mapping;
5. copy TemplateStages → RoomStages;
6. copy TemplateStageRoles → RoomStageRoles using the role mapping;
7. leave all RoomStages `PENDING`.

Stages may be snapshotted even when Session Flow is disabled.

That preserves the configured flow while allowing the Room-level setting to determine whether it is used.

---

# Session Flow Runtime Behavior — v1

If Session Flow is disabled:

    no stage is activated

and the Room behaves like the current Precued experience.

If enabled, the host starts the flow.

Example initial state:

    Prosecution Opening     PENDING
    Defense Opening         PENDING
    Judge Questions         PENDING

Host presses **Start Session Flow**:

    Prosecution Opening     ACTIVE
    Defense Opening         PENDING
    Judge Questions         PENDING

The ACTIVE stage receives:

`started_at = server time`

When the host presses **Next Stage**, one transaction performs:

    current ACTIVE stage → COMPLETED
    completed_at = now

and:

    next PENDING stage → ACTIVE
    started_at = now

After the final stage completes:

- no RoomStage remains ACTIVE;
- all stages are COMPLETED.

---

# Session Flow Timer Rule

A countdown is derived rather than written every second.

For:

    duration_seconds = 120
    started_at = 10:00:00

deadline is:

    10:02:00

Clients calculate the displayed countdown from authoritative server timestamps.

Refreshing or reconnecting therefore does not reset the timer.

At or after the deadline the UI displays:

    TIME EXPIRED

but the RoomStage remains:

    ACTIVE

until the host manually advances.

---

# Derived Session Flow Status

A future Session Flow API may derive the overall state as:

| Status | Meaning |
|---|---|
| `DISABLED` | `Room.session_flow_enabled = false` |
| `NOT_CONFIGURED` | enabled but Room has zero RoomStages |
| `NOT_STARTED` | stages exist, none completed/active |
| `IN_PROGRESS` | exactly one RoomStage is ACTIVE |
| `COMPLETED` | all RoomStages are COMPLETED |

No additional Room flow-status column is required for v1.

---

# Session Flow Example — Mock Trial

One Precued-created Mock Trial Template could define:

| Order | Stage | Active Role(s) | Duration |
|---:|---|---|---:|
| 1 | Prosecution Opening | Prosecution | 2 min |
| 2 | Defense Opening | Defense | 2 min |
| 3 | Judge Questions | Judge | Untimed |
| 4 | Evidence Review | Judge, Defense, Prosecution | Untimed |
| 5 | Prosecution Closing | Prosecution | 2 min |
| 6 | Defense Closing | Defense | 2 min |
| 7 | Jury Deliberation | Jury | 5 min |
| 8 | Verdict | Judge, Jury | Untimed |

This is Precued configuration, not Mock-Trial-specific application code.

A creator can construct a different flow for a different Template.

---

# Session Flow v1 Non-Goals

The first implementation intentionally does not include:

- auto-advance when a timer reaches zero;
- pause/resume;
- add-time controls;
- backward progression;
- arbitrary stage skipping;
- branching;
- conditional logic;
- automatic microphone muting;
- automatic breakout rooms;
- stage-triggered ShareRoleGrant mutations;
- stage-triggered slide changes;
- generic IF/THEN rule JSON;
- voting or scoring.

The goal of v1 is to prove:

> Precued can guide participants through a reusable structured live interaction.

---

# Future Stage Actions

After Session Flow v1 is proven, a Stage may eventually gain actions.

Example:

    WHEN Evidence Review becomes ACTIVE
    THEN reveal Exhibit A to Judge, Defense, and Prosecution

or:

    WHEN Jury Deliberation becomes ACTIVE
    THEN reveal Jury Instructions to Jury

At that point Precued's core building blocks compose as:

    WHO
    RoomRole

    +

    WHAT
    ShareRoleGrant / ShareSlide

    +

    WHEN
    RoomStage

    =

    reusable interaction protocol

This automation layer is deliberately not part of Session Flow v1.

---

# Built-In Template Configuration

## Sales Call

Roles:

- Sales Rep — host
- Sales Engineer
- Client

Existing presets:

- All Roles
- Rep + Engineer Only
- Rep Only

Session Flow does not need to be enabled by default.

---

## Mock Trial

Roles:

- Judge — host
- Jury — multi-member
- Defense
- Prosecution

Existing presets:

- All Roles
- Judge + Jury Only
- Judge Only
- Defense Only
- Prosecution Only

Mock Trial is a strong candidate for a Precued-provided default Session Flow.

---

## Lincoln-Douglas Debate

Roles:

- Judge — host
- Affirmative
- Negative
- Audience — multi-member

Existing presets:

- All Roles
- Judge Only
- Affirmative Only
- Negative Only

LD Debate is also a natural candidate for a Precued-provided timed Session Flow.

---

# Presentation Feature Status

Current repository status:

- ✅ Share `kind` supports `SCREEN` and `PRESENTATION`
- ✅ `Share.current_slide_index`
- ✅ `ShareSlide`
- ✅ slide-scoped `ShareRoleGrant`
- ✅ PDF validation/rendering
- ✅ slide image storage
- ✅ presentation upload API
- ✅ slide image retrieval
- ✅ presenter navigation
- ✅ per-slide role visibility controls
- ✅ participant locked/unavailable state
- ⬜ PPTX import/conversion
- ⬜ native slide creation/editor

The old “Chunk 1 only” description is no longer current.

---

# Current / Near-Future Implementation Notes

## Custom Templates

CURRENT:

- custom Template creation;
- user ownership/private visibility;
- custom TemplateRole creation/removal.

Still needed before custom Templates are fully equivalent to built-ins:

- enforce custom Template ownership in Room creation;
- allow custom Templates to launch Rooms through the frontend;
- custom visibility/preset configuration;
- optional Session Flow builder.

---

## Invites

CURRENT:

- schema/entity/repository.

Still pending:

- host invite creation UI/API flow;
- token consumption;
- usage-counter/status transitions;
- expiration processing;
- optional historical assignment → Invite traceability.

---

## Host Disconnect

CURRENT:

- Room policy fields.

Still pending:

- webhook/service enforcement of `END_CALL`;
- grace-period behavior;
- indefinite-persist behavior.

---

## Publisher Disconnect

CURRENT:

- explicit Share end path.

Still pending:

- automatic Share termination when its publisher leaves.

---

# Diagram Guidance

## Diagram 1 — Config-Time Schema

Update the existing diagram to show:

### Template

    id
    name
    created_by_user_id
    created_at

Do not describe Template IDs as only:

`sales_call | mock_trial | ld_debate`

because custom Templates now use generated UUID strings.

### TemplateRole

Add:

`is_guest_role`

### Footer

Replace the old:

> seeded once (3 templates), read-only at runtime

with something closer to:

> Config-time tables. Built-in templates are seeded/public/read-only; custom templates are private to their creator and editable. Runtime Rooms use snapshots rather than live template references.

Do not add Session Flow tables directly to Diagram 1 if Diagram 4 is used.

---

## Diagram 2 — Runtime Schema

Update:

### User

Add:

`password_hash (string, nullable)`

### RoomParticipant

Add:

`session_token (string, unique/nullable at DB level)`

### Invite

Add the current Invite entity connected:

    RoomRole 1 → many Invite

AuthSession and MagicLinkToken may be intentionally omitted from this diagram to keep it focused on live-call state, but note:

> Account/magic-link auth support tables omitted for clarity.

Do not add Session Flow directly here if Diagram 4 is used.

---

## Diagram 3 — Slide-Level Visibility Extension

Keep the same overall structure.

Update the Share box to show:

    kind (enum: SCREEN | PRESENTATION)
    current_slide_index (int, default 0)

Remove the `NEW` labels if this is meant to represent current architecture rather than historical change.

Update the caption to indicate slide-level presentation visibility is now implemented.

---

## Diagram 4 — Optional Session Flow Extension

Add a fourth diagram containing:

### Existing references

- Template
- TemplateRole
- Room
- RoomRole

### Planned fields

- `Template.session_flow_enabled`
- `Room.session_flow_enabled`

### New tables

- TemplateStage
- TemplateStageRole
- RoomStage
- RoomStageRole

### Relationships

    Template 1 → many TemplateStage
    TemplateStage many ↔ many TemplateRole via TemplateStageRole

    Room 1 → many RoomStage
    RoomStage many ↔ many RoomRole via RoomStageRole

and show:

    TemplateStage --snapshot--> RoomStage
    TemplateStageRole --snapshot--> RoomStageRole

Include callouts:

> Session Flow is optional.

> duration_seconds = NULL means untimed.

> At most one ACTIVE RoomStage per Room.

> Timer expiration does not auto-advance in v1.

> Runtime progression uses RoomStage/RoomStageRole only; Template references are traceability/configuration, not live dependencies.

---

# Planned Schema Count

Current:

    16 tables

After Session Flow v1:

    + template_stage
    + template_stage_role
    + room_stage
    + room_stage_role

Planned total:

    20 tables

No template-specific tables are required.

Adding a new use case remains configuration rather than schema duplication.
