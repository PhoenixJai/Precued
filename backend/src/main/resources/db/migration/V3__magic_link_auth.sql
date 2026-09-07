-- Hybrid auth (Issue #1, resolved): magic link required for host-role
-- participants, guest join (room_participant.user_id IS NULL) unchanged for
-- everyone else. Two tables, matching the two deliberately separate steps in
-- AuthService: generate (magic_link_token) and verify (auth_session).

CREATE TABLE magic_link_token (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL,
    token           TEXT NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ               -- null = not yet consumed
);
CREATE INDEX idx_magic_link_token_token ON magic_link_token(token);

CREATE TABLE auth_session (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES app_user(id),
    token           TEXT NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_auth_session_user_id ON auth_session(user_id);
CREATE INDEX idx_auth_session_token ON auth_session(token);
