-- Seed data for the 3 MVP templates. Config-time only — read-only at runtime.

INSERT INTO template (id, name) VALUES
    ('sales_call', 'Sales Call'),
    ('mock_trial', 'Mock Trial'),
    ('ld_debate', 'Lincoln-Douglas Debate');

-- ===================== Sales Call =====================
INSERT INTO template_role (template_id, role_key, name, is_host_role, is_guest_role, max_members, sort_order) VALUES
    ('sales_call', 'sales_rep', 'Sales Rep', true, false, 1, 0),
    ('sales_call', 'sales_engineer', 'Sales Engineer', false, false, 1, 1),
    ('sales_call', 'client', 'Client', false, false, NULL, 2);

INSERT INTO template_preset (template_id, name, sort_order) VALUES
    ('sales_call', 'All Roles', 0),
    ('sales_call', 'Rep + Engineer Only', 1),
    ('sales_call', 'Rep Only', 2);

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'sales_call' AND p.name = 'All Roles' AND r.template_id = 'sales_call';

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'sales_call' AND p.name = 'Rep + Engineer Only'
  AND r.template_id = 'sales_call' AND r.role_key IN ('sales_rep', 'sales_engineer');

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'sales_call' AND p.name = 'Rep Only'
  AND r.template_id = 'sales_call' AND r.role_key = 'sales_rep';

-- ===================== Mock Trial =====================
INSERT INTO template_role (template_id, role_key, name, is_host_role, is_guest_role, max_members, sort_order) VALUES
    ('mock_trial', 'judge', 'Judge', true, false, 1, 0),
    ('mock_trial', 'jury', 'Jury', false, false, NULL, 1),
    ('mock_trial', 'defense', 'Defense', false, false, NULL, 2),
    ('mock_trial', 'prosecution', 'Prosecution', false, false, NULL, 3);
    -- Note: no is_guest_role=true row seeded for Mock Trial yet.
    -- If/when an Observer role is added (see one-pager, "Roadmap"), insert it
    -- here with is_guest_role = true and zero default ShareRoleGrant.

INSERT INTO template_preset (template_id, name, sort_order) VALUES
    ('mock_trial', 'All Roles', 0),
    ('mock_trial', 'Judge + Jury Only', 1),
    ('mock_trial', 'Judge Only', 2),
    ('mock_trial', 'Defense Only', 3),
    ('mock_trial', 'Prosecution Only', 4);

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'mock_trial' AND p.name = 'All Roles' AND r.template_id = 'mock_trial';

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'mock_trial' AND p.name = 'Judge + Jury Only'
  AND r.template_id = 'mock_trial' AND r.role_key IN ('judge', 'jury');

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'mock_trial' AND p.name = 'Judge Only'
  AND r.template_id = 'mock_trial' AND r.role_key = 'judge';

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'mock_trial' AND p.name = 'Defense Only'
  AND r.template_id = 'mock_trial' AND r.role_key = 'defense';

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'mock_trial' AND p.name = 'Prosecution Only'
  AND r.template_id = 'mock_trial' AND r.role_key = 'prosecution';

-- ===================== Lincoln-Douglas Debate =====================
-- No guest role: Audience already serves that function (see one-pager notes).
INSERT INTO template_role (template_id, role_key, name, is_host_role, is_guest_role, max_members, sort_order) VALUES
    ('ld_debate', 'judge', 'Judge', true, false, 1, 0),
    ('ld_debate', 'affirmative', 'Affirmative', false, false, 1, 1),
    ('ld_debate', 'negative', 'Negative', false, false, 1, 2),
    ('ld_debate', 'audience', 'Audience', false, false, NULL, 3);

INSERT INTO template_preset (template_id, name, sort_order) VALUES
    ('ld_debate', 'All Roles', 0),
    ('ld_debate', 'Judge Only', 1),
    ('ld_debate', 'Affirmative Only', 2),
    ('ld_debate', 'Negative Only', 3);

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'ld_debate' AND p.name = 'All Roles' AND r.template_id = 'ld_debate';

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'ld_debate' AND p.name = 'Judge Only'
  AND r.template_id = 'ld_debate' AND r.role_key = 'judge';

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'ld_debate' AND p.name = 'Affirmative Only'
  AND r.template_id = 'ld_debate' AND r.role_key = 'affirmative';

INSERT INTO template_preset_role (preset_id, template_role_id)
SELECT p.id, r.id FROM template_preset p, template_role r
WHERE p.template_id = 'ld_debate' AND p.name = 'Negative Only'
  AND r.template_id = 'ld_debate' AND r.role_key = 'negative';
