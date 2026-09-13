-- PR 5: first polished Precued-owned Session Flow.
-- Configuration only: no Mock Trial stage names/logic belong in Java or React.

UPDATE template
SET session_flow_enabled = true
WHERE id = 'mock_trial';

INSERT INTO template_stage
    (id, template_id, stage_key, name, sort_order, duration_seconds)
VALUES
    (CAST('50000000-0000-0000-0000-000000000001' AS UUID), 'mock_trial', 'prosecution_opening', 'Prosecution Opening', 0, 120),
    (CAST('50000000-0000-0000-0000-000000000002' AS UUID), 'mock_trial', 'defense_opening', 'Defense Opening', 1, 120),
    (CAST('50000000-0000-0000-0000-000000000003' AS UUID), 'mock_trial', 'judge_questions', 'Judge Questions', 2, NULL),
    (CAST('50000000-0000-0000-0000-000000000004' AS UUID), 'mock_trial', 'evidence_review', 'Evidence Review', 3, NULL),
    (CAST('50000000-0000-0000-0000-000000000005' AS UUID), 'mock_trial', 'prosecution_closing', 'Prosecution Closing', 4, 120),
    (CAST('50000000-0000-0000-0000-000000000006' AS UUID), 'mock_trial', 'defense_closing', 'Defense Closing', 5, 120),
    (CAST('50000000-0000-0000-0000-000000000007' AS UUID), 'mock_trial', 'jury_deliberation', 'Jury Deliberation', 6, 300),
    (CAST('50000000-0000-0000-0000-000000000008' AS UUID), 'mock_trial', 'verdict', 'Verdict', 7, NULL);

-- Active/floor roles per stage. These are descriptive v1 Session Flow roles;
-- they do not automatically mute anyone or change LiveKit publication rights.
INSERT INTO template_stage_role (template_stage_id, template_role_id)
SELECT s.id, r.id
FROM template_stage s
JOIN template_role r ON r.template_id = s.template_id
WHERE s.template_id = 'mock_trial'
  AND s.stage_key = 'prosecution_opening'
  AND r.role_key = 'prosecution';

INSERT INTO template_stage_role (template_stage_id, template_role_id)
SELECT s.id, r.id
FROM template_stage s
JOIN template_role r ON r.template_id = s.template_id
WHERE s.template_id = 'mock_trial'
  AND s.stage_key = 'defense_opening'
  AND r.role_key = 'defense';

INSERT INTO template_stage_role (template_stage_id, template_role_id)
SELECT s.id, r.id
FROM template_stage s
JOIN template_role r ON r.template_id = s.template_id
WHERE s.template_id = 'mock_trial'
  AND s.stage_key = 'judge_questions'
  AND r.role_key = 'judge';

INSERT INTO template_stage_role (template_stage_id, template_role_id)
SELECT s.id, r.id
FROM template_stage s
JOIN template_role r ON r.template_id = s.template_id
WHERE s.template_id = 'mock_trial'
  AND s.stage_key = 'evidence_review'
  AND r.role_key IN ('judge', 'defense', 'prosecution');

INSERT INTO template_stage_role (template_stage_id, template_role_id)
SELECT s.id, r.id
FROM template_stage s
JOIN template_role r ON r.template_id = s.template_id
WHERE s.template_id = 'mock_trial'
  AND s.stage_key = 'prosecution_closing'
  AND r.role_key = 'prosecution';

INSERT INTO template_stage_role (template_stage_id, template_role_id)
SELECT s.id, r.id
FROM template_stage s
JOIN template_role r ON r.template_id = s.template_id
WHERE s.template_id = 'mock_trial'
  AND s.stage_key = 'defense_closing'
  AND r.role_key = 'defense';

INSERT INTO template_stage_role (template_stage_id, template_role_id)
SELECT s.id, r.id
FROM template_stage s
JOIN template_role r ON r.template_id = s.template_id
WHERE s.template_id = 'mock_trial'
  AND s.stage_key = 'jury_deliberation'
  AND r.role_key = 'jury';

INSERT INTO template_stage_role (template_stage_id, template_role_id)
SELECT s.id, r.id
FROM template_stage s
JOIN template_role r ON r.template_id = s.template_id
WHERE s.template_id = 'mock_trial'
  AND s.stage_key = 'verdict'
  AND r.role_key IN ('judge', 'jury');
