-- PR 3: optional Session Flow persistence + Room snapshots.
-- Runtime progression APIs are intentionally deferred to the next PR.

ALTER TABLE template
    ADD COLUMN session_flow_enabled BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE room
    ADD COLUMN session_flow_enabled BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE template_stage (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id         VARCHAR(64) NOT NULL REFERENCES template(id),
    stage_key           TEXT NOT NULL,
    name                TEXT NOT NULL,
    sort_order          INTEGER NOT NULL,
    duration_seconds    INTEGER,
    CONSTRAINT uq_template_stage_key UNIQUE (template_id, stage_key),
    CONSTRAINT uq_template_stage_order UNIQUE (template_id, sort_order),
    CONSTRAINT chk_template_stage_duration_positive
        CHECK (duration_seconds IS NULL OR duration_seconds > 0)
);
CREATE INDEX idx_template_stage_template_id ON template_stage(template_id);

CREATE TABLE template_stage_role (
    template_stage_id   UUID NOT NULL REFERENCES template_stage(id) ON DELETE CASCADE,
    template_role_id    UUID NOT NULL REFERENCES template_role(id) ON DELETE CASCADE,
    PRIMARY KEY (template_stage_id, template_role_id)
);
CREATE INDEX idx_template_stage_role_role_id ON template_stage_role(template_role_id);

CREATE TABLE room_stage (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id                     UUID NOT NULL REFERENCES room(id),
    source_template_stage_id    UUID REFERENCES template_stage(id) ON DELETE SET NULL,
    stage_key                   TEXT NOT NULL,
    name                        TEXT NOT NULL,
    sort_order                  INTEGER NOT NULL,
    duration_seconds            INTEGER,
    status                      VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING','ACTIVE','COMPLETED')),
    started_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    CONSTRAINT uq_room_stage_key UNIQUE (room_id, stage_key),
    CONSTRAINT uq_room_stage_order UNIQUE (room_id, sort_order),
    CONSTRAINT chk_room_stage_duration_positive
        CHECK (duration_seconds IS NULL OR duration_seconds > 0)
);
CREATE INDEX idx_room_stage_room_id ON room_stage(room_id);
CREATE UNIQUE INDEX idx_room_stage_one_active
    ON room_stage(room_id)
    WHERE status = 'ACTIVE';

CREATE TABLE room_stage_role (
    room_stage_id   UUID NOT NULL REFERENCES room_stage(id) ON DELETE CASCADE,
    room_role_id    UUID NOT NULL REFERENCES room_role(id) ON DELETE CASCADE,
    PRIMARY KEY (room_stage_id, room_role_id)
);
CREATE INDEX idx_room_stage_role_role_id ON room_stage_role(room_role_id);
