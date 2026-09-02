-- Precued MVP schema — Sales Call · Mock Trial · LD Debate
-- Mirrors Precued_DataModel.md exactly. 11 tables, one visibility engine.

-- ===================== CONFIG-TIME (seeded once, read-only at runtime) =====================

CREATE TABLE template (
    id          VARCHAR(64) PRIMARY KEY,     -- 'sales_call' | 'mock_trial' | 'ld_debate'
    name        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE template_role (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     VARCHAR(64) NOT NULL REFERENCES template(id),
    role_key        TEXT NOT NULL,
    name            TEXT NOT NULL,
    is_host_role    BOOLEAN NOT NULL DEFAULT false,
    is_guest_role   BOOLEAN NOT NULL DEFAULT false,
    max_members     INTEGER,                  -- null = unlimited
    sort_order      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_template_role_template_id ON template_role(template_id);

CREATE TABLE template_preset (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     VARCHAR(64) NOT NULL REFERENCES template(id),
    name            TEXT NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_template_preset_template_id ON template_preset(template_id);

CREATE TABLE template_preset_role (
    preset_id           UUID NOT NULL REFERENCES template_preset(id),
    template_role_id    UUID NOT NULL REFERENCES template_role(id),
    PRIMARY KEY (preset_id, template_role_id)
);

-- ===================== RUNTIME (created/modified during a live call) =====================

CREATE TABLE app_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL UNIQUE,
    display_name    TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE room (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id                     VARCHAR(64) NOT NULL REFERENCES template(id),
    created_by_user_id              UUID NOT NULL REFERENCES app_user(id),
    livekit_room_name               TEXT NOT NULL UNIQUE,
    status                          VARCHAR(16) NOT NULL DEFAULT 'CREATED'
                                        CHECK (status IN ('CREATED','ACTIVE','ENDED')),
    host_disconnect_policy          VARCHAR(32) NOT NULL DEFAULT 'END_CALL'
                                        CHECK (host_disconnect_policy IN
                                            ('END_CALL','PERSIST_INDEFINITELY','PERSIST_FOR_DURATION')),
    host_disconnect_grace_seconds   INTEGER,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at                        TIMESTAMPTZ
);
CREATE INDEX idx_room_template_id ON room(template_id);
CREATE INDEX idx_room_created_by ON room(created_by_user_id);

CREATE TABLE room_role (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id                     UUID NOT NULL REFERENCES room(id),
    source_template_role_id     UUID REFERENCES template_role(id), -- traceability only
    role_key                    TEXT NOT NULL,
    name                        TEXT NOT NULL,
    is_host_role                BOOLEAN NOT NULL DEFAULT false,
    max_members                 INTEGER
);
CREATE INDEX idx_room_role_room_id ON room_role(room_id);

CREATE TABLE invite (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_role_id    UUID NOT NULL REFERENCES room_role(id),
    invitee_email   TEXT,                     -- null = pool link
    token           TEXT NOT NULL UNIQUE,
    max_uses        INTEGER NOT NULL,
    uses_count      INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','USED','EXPIRED')),
    mode            VARCHAR(16) NOT NULL CHECK (mode IN ('NAMED','POOL')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ               -- null = expires at room.ended_at
);
CREATE INDEX idx_invite_room_role_id ON invite(room_role_id);
CREATE INDEX idx_invite_token ON invite(token);

CREATE TABLE room_participant (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id             UUID NOT NULL REFERENCES room(id),
    user_id             UUID REFERENCES app_user(id),  -- nullable: guest joins
    livekit_identity    TEXT NOT NULL,
    display_name        TEXT NOT NULL,
    access_level        VARCHAR(16) NOT NULL DEFAULT 'MEMBER'
                            CHECK (access_level IN ('HOST','MEMBER')),
    joined_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at             TIMESTAMPTZ,
    UNIQUE (room_id, livekit_identity)
);
CREATE INDEX idx_room_participant_room_id ON room_participant(room_id);
CREATE INDEX idx_room_participant_user_id ON room_participant(user_id);

CREATE TABLE participant_role_assignment (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_participant_id     UUID NOT NULL REFERENCES room_participant(id),
    room_role_id            UUID NOT NULL REFERENCES room_role(id),
    assigned_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at              TIMESTAMPTZ               -- null = active
);
CREATE INDEX idx_pra_participant_id ON participant_role_assignment(room_participant_id);
CREATE INDEX idx_pra_room_role_id ON participant_role_assignment(room_role_id);
-- Partial index: fast "what is this participant's current active role" lookup.
-- This is the Postgres-specific win cited in ADR-001 (MySQL has no equivalent).
CREATE UNIQUE INDEX idx_pra_one_active_per_participant
    ON participant_role_assignment(room_participant_id)
    WHERE revoked_at IS NULL;

CREATE TABLE share (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id                     UUID NOT NULL REFERENCES room(id),
    publisher_participant_id    UUID NOT NULL REFERENCES room_participant(id),
    applied_preset_id           UUID REFERENCES template_preset(id),  -- reference only
    label                       TEXT NOT NULL,
    status                      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                                    CHECK (status IN ('ACTIVE','ENDED')),
    started_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at                    TIMESTAMPTZ
);
CREATE INDEX idx_share_room_id ON share(room_id);
CREATE INDEX idx_share_publisher_id ON share(publisher_participant_id);

CREATE TABLE share_track (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    share_id            UUID NOT NULL REFERENCES share(id),
    livekit_track_sid   TEXT NOT NULL UNIQUE,
    kind                VARCHAR(8) NOT NULL CHECK (kind IN ('VIDEO','AUDIO')),
    published_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    unpublished_at      TIMESTAMPTZ
);
CREATE INDEX idx_share_track_share_id ON share_track(share_id);

CREATE TABLE share_role_grant (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    share_id        UUID NOT NULL REFERENCES share(id),
    room_role_id    UUID NOT NULL REFERENCES room_role(id),
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ               -- null = active
);
CREATE INDEX idx_srg_share_id ON share_role_grant(share_id);
-- Partial index: fast "does this role currently see this share" lookup —
-- the hot path the VisibilityEngine calls on every recompile.
CREATE INDEX idx_srg_active ON share_role_grant(share_id, room_role_id)
    WHERE revoked_at IS NULL;
