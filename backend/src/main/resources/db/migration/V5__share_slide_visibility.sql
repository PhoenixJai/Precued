-- Slide-level visibility, Chunk 1 (schema + Runtime Rule extension only —
-- see Precued_DataModel.md "Presentations Feature — Chunk Status").
-- Existing screen-kind Shares are completely unaffected: kind defaults to
-- 'SCREEN' for every existing row, and share_slide_id stays NULL for every
-- existing grant (whole-share, unchanged behavior).

ALTER TABLE share ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'SCREEN'
    CHECK (kind IN ('SCREEN','PRESENTATION'));
ALTER TABLE share ADD COLUMN current_slide_index INTEGER NOT NULL DEFAULT 0;

CREATE TABLE share_slide (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    share_id        UUID NOT NULL REFERENCES share(id),
    slide_index     INTEGER NOT NULL,
    image_url       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (share_id, slide_index)
);
CREATE INDEX idx_share_slide_share_id ON share_slide(share_id);

ALTER TABLE share_role_grant ADD COLUMN share_slide_id UUID REFERENCES share_slide(id);
-- Partial index: fast "does this slide-specific grant apply" lookup, mirrors
-- idx_srg_active's role for the whole-share case.
CREATE INDEX idx_srg_share_slide_id ON share_role_grant(share_slide_id)
    WHERE share_slide_id IS NOT NULL;
