**PRECUED**

*Role-based visibility control for video calls  ·  MVP One-Pager*

**The Problem**

Zoom, Meet, and Teams give every call exactly two visibility tiers: host and audience. Real calls have more roles than that, and no way to control who sees what content on a shared screen. A sales engineer's pricing model, a therapist's session notes, a judge's exhibit — platforms have no concept of role-scoped content. The result: constant risk of accidental exposure (see companion market research) and zero native way to run a multi-role call by design.

**The Solution**

Precued is a video conferencing app where visibility is a first-class, per-role setting — not a workaround. Hosts assign roles at setup; each shared window, doc, or app surface is tagged with which roles can see it. Verticalized templates (Sales Call, Mock Trial, Interview, Classroom) ship with roles and default visibility rules pre-configured, so the host doesn't build a permission model from scratch — they pick a template and go.

**MVP Scope — One Engine, Three Templates**

The build is a single role-visibility engine (Template → Role → Share → ShareVisibility). Verticals are config on top of that engine, not separate builds — demo day shows the same primitive wearing three outfits.

- **Sales Call — Client, Sales Rep (host), Sales Engineer. Pricing sheets and battlecards scoped to internal roles only.**

- **Mock Trial — Judge, Jury, Defense, Prosecution. Exhibits and sidebar material scoped to Judge/Jury independent of the arguing sides.**

- **Lincoln-Douglas Debate — Affirmative, Negative, Judge, Audience. Prep notes visible only to each side; Judge-only flow sheet/timer.**

- **Host creates a room, assigns participants to roles at entry, picks a template**

- **Host shares content and tags visibility per share via role presets**

- **Each participant's view renders only content tagged for their role — in real time, per-track**

- **No chat, recording, or custom template builder in v1 — templates are hardcoded, not user-built**

**Why Now / Why This Wedge**

Existing screen-privacy tools (Muzzle, Hush, Stealthly) patch the symptom — they hide notifications or blur a desktop, but still mirror one screen to everyone. None address the actual gap: differentiated visibility across more than two roles, live, during the call. Nobody owns this at the conferencing-platform level. Three templates across three unrelated domains — sales, legal, competitive debate — demonstrate the same engine generalizes to any context with more than “host” and “audience.”

**Architecture Snapshot**

Standalone app built on LiveKit (open-source WebRTC SFU) rather than a plugin on existing platforms — Zoom/Meet/Teams SDKs don't expose per-participant render control, which is the exact capability this product needs. LiveKit handles media routing; Precued owns the room/role/visibility logic on top.

Data model: 10 tables across Template/TemplateRole/TemplatePreset (config), Room/RoomRole/RoomParticipant/ParticipantRoleAssignment (runtime), and Share/ShareTrack/ShareRoleGrant (content + policy). Two design decisions matter most: (1) a Room's roles are copied from its Template at creation, not referenced live, so editing a template never corrupts an in-progress call; (2) a participant's identity is stable even as their role changes mid-call — role assignment is tracked separately with a timestamped history, not baked into the participant record. A single VisibilityEngine compiles stored ShareRoleGrant policy into live LiveKit track permissions.

**Roadmap**

| **Stage 1** | Sales, Mock Trial, LD Debate templates — demo day build |
| - | - |
| **Stage 2** | Custom role/visibility builder; classroom, therapy, interview verticals |
| **Stage 3** | Compliance-heavy verticals (healthcare, licensed legal proceedings) once core reliability is proven |

**Open Risks**

- **Media infra (LiveKit) still requires real engineering effort — not zero-code**

- **If Zoom/Teams ship native multi-role visibility, this compresses fast**

- **Three live demo flows on demo day = more surface area for something to break — rehearse all three, not just one**

- **Template-market-fit unproven beyond the three shown — validate before generalizing further**
