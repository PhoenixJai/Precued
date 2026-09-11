-- Chunk 1 manual-testing fixture (Precued_DataModel.md "Presentations
-- Feature — Chunk Status"): one PRESENTATION Share with 3 slides and a mix
-- of whole-share / slide-specific ShareRoleGrants across different Mock
-- Trial roles.
--
-- NOT a Flyway migration — this file lives outside db/migration (which is
-- the only path Flyway scans, see application.yml's spring.flyway.locations)
-- so it never runs automatically against any environment. Run it manually
-- against a local/dev DB:
--   psql "$DATABASE_URL" -f backend/src/main/resources/db/fixtures/chunk1_slide_visibility_fixture.sql
--
-- Grant layout (current_slide_index starts at 0):
--   judge        — host/publisher, not a viewer of its own Share
--   jury         — whole-share grant (share_slide_id NULL): allowed on every slide
--   defense      — slide-specific grant for slide_index=1 only
--   prosecution  — slide-specific grant for slide_index=2 only
-- So at current_slide_index=0: only jury is allowed. Advance the slide to
-- see defense/prosecution flip to allowed and back, e.g.:
--   UPDATE share SET current_slide_index = 1
--     WHERE label = 'Chunk 1 Fixture Slide Deck';
-- (In Chunk 1 there is no REST endpoint for this yet — Chunk 3 owns
-- presenter/viewer navigation UI — so advancing the slide for a manual
-- check means either this direct UPDATE or calling
-- ShareLifecycleService.changeSlide(shareId, newIndex) directly, both of
-- which the VisibilityEngine's compute path reacts to identically.)

WITH fixture_user AS (
    INSERT INTO app_user (email, display_name)
    VALUES ('chunk1-fixture-host@example.com', 'Chunk1 Fixture Host')
    RETURNING id
),
fixture_room AS (
    INSERT INTO room (template_id, created_by_user_id, livekit_room_name, status)
    SELECT 'mock_trial', fixture_user.id, 'chunk1-slide-visibility-fixture', 'ACTIVE'
    FROM fixture_user
    RETURNING id
),
fixture_room_roles AS (
    INSERT INTO room_role (room_id, source_template_role_id, role_key, name, is_host_role, max_members)
    SELECT fixture_room.id, tr.id, tr.role_key, tr.name, tr.is_host_role, tr.max_members
    FROM fixture_room, template_role tr
    WHERE tr.template_id = 'mock_trial'
    RETURNING id, role_key
),
fixture_host_participant AS (
    INSERT INTO room_participant (room_id, user_id, livekit_identity, display_name, access_level, joined_at)
    SELECT fixture_room.id, fixture_user.id, 'chunk1-fixture-judge', 'Fixture Judge', 'HOST', now()
    FROM fixture_room, fixture_user
    RETURNING id
),
fixture_host_assignment AS (
    INSERT INTO participant_role_assignment (room_participant_id, room_role_id, assigned_at)
    SELECT fixture_host_participant.id, fixture_room_roles.id, now()
    FROM fixture_host_participant
    JOIN fixture_room_roles ON fixture_room_roles.role_key = 'judge'
    RETURNING id
),
fixture_share AS (
    INSERT INTO share (room_id, publisher_participant_id, label, kind, current_slide_index, status, started_at)
    SELECT fixture_room.id, fixture_host_participant.id, 'Chunk 1 Fixture Slide Deck',
           'PRESENTATION', 0, 'ACTIVE', now()
    FROM fixture_room, fixture_host_participant
    RETURNING id
),
fixture_slides AS (
    INSERT INTO share_slide (share_id, slide_index)
    SELECT fixture_share.id, slide_index
    FROM fixture_share, generate_series(0, 2) AS slide_index
    RETURNING id, slide_index
),
fixture_grant_jury_whole_share AS (
    INSERT INTO share_role_grant (share_id, room_role_id, share_slide_id, granted_at)
    SELECT fixture_share.id, fixture_room_roles.id, NULL, now()
    FROM fixture_share
    JOIN fixture_room_roles ON fixture_room_roles.role_key = 'jury'
    RETURNING id
),
fixture_grant_defense_slide1 AS (
    INSERT INTO share_role_grant (share_id, room_role_id, share_slide_id, granted_at)
    SELECT fixture_share.id, fixture_room_roles.id, fixture_slides.id, now()
    FROM fixture_share
    JOIN fixture_room_roles ON fixture_room_roles.role_key = 'defense'
    JOIN fixture_slides ON fixture_slides.slide_index = 1
    RETURNING id
)
INSERT INTO share_role_grant (share_id, room_role_id, share_slide_id, granted_at)
SELECT fixture_share.id, fixture_room_roles.id, fixture_slides.id, now()
FROM fixture_share
JOIN fixture_room_roles ON fixture_room_roles.role_key = 'prosecution'
JOIN fixture_slides ON fixture_slides.slide_index = 2;

-- Cleanup (run manually, in this FK order, when done testing):
-- DELETE FROM share_role_grant WHERE share_id IN (SELECT id FROM share WHERE label = 'Chunk 1 Fixture Slide Deck');
-- DELETE FROM share_slide WHERE share_id IN (SELECT id FROM share WHERE label = 'Chunk 1 Fixture Slide Deck');
-- DELETE FROM share WHERE label = 'Chunk 1 Fixture Slide Deck';
-- DELETE FROM participant_role_assignment WHERE room_participant_id IN
--   (SELECT id FROM room_participant WHERE livekit_identity = 'chunk1-fixture-judge');
-- DELETE FROM room_participant WHERE livekit_identity = 'chunk1-fixture-judge';
-- DELETE FROM room_role WHERE room_id IN (SELECT id FROM room WHERE livekit_room_name = 'chunk1-slide-visibility-fixture');
-- DELETE FROM room WHERE livekit_room_name = 'chunk1-slide-visibility-fixture';
-- DELETE FROM app_user WHERE email = 'chunk1-fixture-host@example.com';
