# Precued

Role-based visibility control for video calls. See `Precued_OnePager.docx`
for the pitch and `docs/Precued_DataModel.md` for the full schema.

## Structure

```
backend/    Java 21 + Spring Boot. Entities, Flyway migrations, VisibilityEngine.
frontend/   React + LiveKit React SDK.
docs/       ADRs and reference docs (data model, one-pager, diagrams).
```

## Stack

See `docs/ADR-001-stack.md` for the full reasoning. Summary: Java/Spring Boot
+ Postgres + LiveKit Cloud + React, hosted on Railway.

## Backend setup

```bash
cd backend
cp .env.example .env   # fill in LiveKit + SMTP credentials
# Requires local Postgres running, or point DATABASE_URL at a hosted instance
mvn spring-boot:run
```

Flyway runs automatically on startup (`V1__init_schema.sql` creates all 11
tables, `V2__seed_templates.sql` seeds Sales Call / Mock Trial / LD Debate).

## Frontend setup

```bash
cd frontend
npm install
npm run dev
```

## Where things stand (M0 start)

- [x] Repo scaffolded — entities, migrations, seed data, VisibilityEngine
      interface, application config.
- [ ] LiveKit Cloud project provisioned (get API keys, add to `.env`).
- [ ] Auth (hybrid magic-link/guest) implementation — see kanban Issue 1
      (resolved) and Issues 3/5.
- [ ] `VisibilityEngine` implementation — interface + Javadoc are in place
      (`engine/VisibilityEngine.java`); recompile logic is the M1 core build.
- [ ] `Invite` consumption flow, `Room.host_disconnect_policy` enforcement,
      `Share` publisher-disconnect handler — all schema-ready, service layer
      not yet implemented. See kanban M1 additions.

## Key design decisions carried into this scaffold

1. **RoomRole is a snapshot, not a live reference** — copied from
   `TemplateRole` at room creation (`RoomRole.sourceTemplateRoleId` is
   traceability-only). Editing a template never corrupts an in-progress call.
2. **Participant identity is stable across role changes** —
   `RoomParticipant` persists for the room's lifetime; `ParticipantRoleAssignment`
   tracks role history separately, enforced via a Postgres partial unique
   index (`idx_pra_one_active_per_participant`).
3. **Visibility is allow-list, compiled at runtime** — no `ShareRoleGrant`
   row means a role never subscribes, full stop. This must be enforced via
   LiveKit server-side track permissions, not client-side hiding — see
   `VisibilityEngine` Javadoc.
4. **Invite is a one-time-use join artifact** — consumed on first successful
   join, inert afterward. Mid-call role reassignment never touches the
   originating `Invite` row.
5. **Auth is hybrid** — magic link required for host-role participants,
   guest joins allowed for everyone else (`RoomParticipant.userId` nullable
   by design).

## License

All rights reserved. See [LICENSE](./LICENSE) for details. This code is shared for portfolio purposes only — no reuse permitted without written consent.