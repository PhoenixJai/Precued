-- Session/auth enforcement: every RoomParticipant (host or guest) gets an
-- opaque session token at join time. Requests scoped to a Room or to acting
-- as a specific participant must present it — previously nothing did.
-- Nullable at the column level only because existing rows predate this
-- column; application code always sets it going forward (RoomParticipantService.join).

ALTER TABLE room_participant ADD COLUMN session_token TEXT;
CREATE UNIQUE INDEX idx_room_participant_session_token ON room_participant(session_token);
