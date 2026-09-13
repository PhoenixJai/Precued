-- PR 9: make Invite the enforced pre-assignment layer instead of leaving it dormant.

ALTER TABLE participant_role_assignment
    ADD COLUMN invite_id UUID REFERENCES invite(id) ON DELETE SET NULL;

CREATE INDEX idx_pra_invite_id ON participant_role_assignment(invite_id);

ALTER TABLE invite
    ADD CONSTRAINT invite_max_uses_positive CHECK (max_uses > 0),
    ADD CONSTRAINT invite_uses_count_nonnegative CHECK (uses_count >= 0),
    ADD CONSTRAINT invite_uses_within_limit CHECK (uses_count <= max_uses);
