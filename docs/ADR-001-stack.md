# ADR-001: Technical Stack

**Status:** Accepted

## Decision

- **Backend:** Java 21 + Spring Boot
- **Frontend:** React + LiveKit React SDK (`@livekit/components-react`)
- **Database:** PostgreSQL
- **Realtime media:** LiveKit Cloud
- **Hosting:** Railway (backend + Postgres), frontend host TBD (Railway or Vercel)

## Context

Precued is a role-based visibility control engine for video calls, built on
LiveKit rather than as a Zoom/Meet/Teams plugin (those SDKs don't expose
per-participant render control — see `Precued_OnePager.docx`, Architecture
Snapshot). The schema (`Precued_DataModel.md`) is relational, 11 tables, with
several fields whose semantics depend on the database's feature set.

## Backend: Java + Spring Boot

**Considered:** Node.js, Go, Python — all first-class in LiveKit's SDK
ecosystem; Java is not.

**Chosen because:**
- Existing language proficiency — fastest path to shipping for a
  single-engineer bootcamp build.
- Spring Boot gives webhook endpoints, JPA/Hibernate for the relational
  schema, and scheduled/event-driven recompute for the VisibilityEngine with
  minimal boilerplate.
- LiveKit's server-side surface (REST + JWT signing + webhook payloads) is
  plain HTTP — the SDK gap is a convenience loss, not a capability gap.

**Trade-off accepted:** more ceremony than Node for webhook-driven glue code;
judged worth it for JPA mapping the schema 1:1 and long-term maintainability.

## Frontend: React

**Considered:** Vue, Svelte.

**Chosen because:** existing preference, and LiveKit's React SDK has mature
hooks for track subscription that map directly onto per-role visibility
rendering (see Images 3/5 — host view vs. non-host restricted view).

## Database: PostgreSQL

**Considered:** MySQL.

| Factor | Postgres | MySQL |
|---|---|---|
| Composite PKs (`TemplatePresetRole`) | Native | Clunkier with JPA/Hibernate |
| Partial indexes (`WHERE revoked_at IS NULL`) | Yes — fast "current active row" lookups | Not supported |
| Enums (`status`, `access_level`, `kind`) | Native type, easy to evolve | Painful to alter value list later |
| Future: custom template builder (Stage 2) storing arbitrary role/permission configs | `jsonb`, indexable | Weaker JSON support |
| Free-tier hosting | Supabase/Neon/Railway default here | Fewer bundled options |

This isn't a generic "Postgres is better" call — the partial-index case is
concrete and immediate: `ParticipantRoleAssignment` and `ShareRoleGrant` both
rely on "at most one active row" as a business rule (MVP rule in the data
model doc), and Postgres enforces + indexes that natively
(`idx_pra_one_active_per_participant` in `V1__init_schema.sql`). MySQL would
need application-level enforcement with no equivalent index speedup.

Not pre-optimizing toward AWS — highest setup tax, lowest payoff until an
actual deal forces it.

## Consequences

- LiveKit Java SDK (`io.livekit:livekit-server`) is used for JWT generation
  and REST calls; webhook signature verification is handled manually against
  plain HTTP if the SDK's webhook helpers prove incomplete — flag this in M0
  if it becomes a blocker.
- Hibernate's `ddl-auto` is set to `validate`, not `update` — Flyway
  (`V1__init_schema.sql`, `V2__seed_templates.sql`) is the single source of
  truth for schema, matching the "config tables are read-only at runtime,
  seeded once" design decision.
