-- PR 2: RoomRole snapshot hygiene before Session Flow.
--
-- 1) source_template_role_id is traceability only. Existing rooms must remain
--    valid if a custom TemplateRole is later deleted, so the FK nulls the
--    traceability pointer instead of blocking deletion.
-- 2) is_guest_role is part of the TemplateRole snapshot contract and must be
--    preserved independently at runtime just like is_host_role/max_members.

ALTER TABLE room_role
    ADD COLUMN is_guest_role BOOLEAN;

-- Backfill existing RoomRole snapshots from their source TemplateRole before
-- making the column independent. Rows whose source has already disappeared
-- (or was never present) fail closed to false.
UPDATE room_role rr
SET is_guest_role = tr.is_guest_role
FROM template_role tr
WHERE rr.source_template_role_id = tr.id;

UPDATE room_role
SET is_guest_role = false
WHERE is_guest_role IS NULL;

ALTER TABLE room_role
    ALTER COLUMN is_guest_role SET DEFAULT false,
    ALTER COLUMN is_guest_role SET NOT NULL;

ALTER TABLE room_role
    DROP CONSTRAINT IF EXISTS room_role_source_template_role_id_fkey;

ALTER TABLE room_role
    ADD CONSTRAINT room_role_source_template_role_id_fkey
        FOREIGN KEY (source_template_role_id)
        REFERENCES template_role(id)
        ON DELETE SET NULL;
