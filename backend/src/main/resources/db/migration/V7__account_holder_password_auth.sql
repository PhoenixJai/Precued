-- Auth & Account Overhaul (supersedes Issue 1's hybrid magic-link decision
-- for Account Holders): password auth becomes the login path for real
-- accounts. Magic-link stays, forked to guest-room-join only (unchanged
-- for now). Nullable — existing magic-link-created User rows have no
-- password and simply can't log in via password until they set one
-- (no such flow exists yet; out of scope for this chunk).
ALTER TABLE app_user ADD COLUMN password_hash TEXT;
