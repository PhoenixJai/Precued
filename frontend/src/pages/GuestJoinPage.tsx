import { FormEvent, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { api } from "../lib/api";
import { saveParticipant } from "../lib/session";
import type { InvitePreview } from "../types/precued";

/** Guest entry is now backed by an opaque Invite token, not a client-chosen RoomRole id. */
export default function GuestJoinPage() {
  const navigate = useNavigate();
  const { inviteToken } = useParams();
  const [guestName, setGuestName] = useState("");
  const [preview, setPreview] = useState<InvitePreview | null>(null);
  const [resolving, setResolving] = useState(Boolean(inviteToken));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!inviteToken) {
      setResolving(false);
      return;
    }
    let cancelled = false;
    setResolving(true);
    setError(null);
    api.getInvitePreview(inviteToken)
      .then((nextPreview) => {
        if (!cancelled) setPreview(nextPreview);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Unable to resolve invite");
      })
      .finally(() => {
        if (!cancelled) setResolving(false);
      });
    return () => { cancelled = true; };
  }, [inviteToken]);

  const guestHelper = useMemo(() => {
    if (!inviteToken) return "Open an invitation link created by the room host to join as a guest.";
    if (resolving) return "Checking your invitation…";
    if (preview) return `You are joining as ${preview.roleName}.`;
    return "This invitation could not be used.";
  }, [inviteToken, preview, resolving]);

  async function joinGuest(event: FormEvent) {
    event.preventDefault();
    if (!inviteToken || !preview) return;
    setLoading(true);
    setError(null);
    try {
      // The opaque token determines the role on the server. The public role
      // snapshot is fetched only so the client can preserve the readable
      // role key/name in its local participant session after the join.
      const roles = await api.getRoomRoles(preview.roomId);
      const invitedRole = roles.find((role) => role.id === preview.roomRoleId);
      if (!invitedRole || invitedRole.isHostRole) {
        throw new Error("This invitation does not resolve to a guest role.");
      }

      const participant = await api.joinRoom(
        preview.roomId,
        guestName.trim(),
        null,
        undefined,
        inviteToken,
      );

      // InviteJoinService already created the ParticipantRoleAssignment in
      // the same transaction that consumed the Invite — no client-side
      // self-assignment step remains.
      saveParticipant({
        id: participant.id,
        roomId: preview.roomId,
        roomRoleId: invitedRole.id,
        roleKey: invitedRole.roleKey,
        roleName: invitedRole.name,
        isHost: false,
        displayName: participant.displayName,
        userId: null,
        sessionToken: participant.sessionToken,
      });
      navigate(`/rooms/${preview.roomId}/call`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to join room");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AppShell>
      <section className="auth-page page-center-narrow">
        <div className="page-heading centered">
          <h1>Join a Precued session</h1>
          <p>{preview ? `You've been invited as ${preview.roleName}.` : "Enter your name to join as a guest."}</p>
        </div>

        <div className="auth-stack">
          <form className="surface-card auth-card" onSubmit={joinGuest}>
            <div className="card-heading-row">
              <div className="icon-tile">♙</div>
              <div>
                <h2>Join as guest</h2>
                <p>{preview ? `Role: ${preview.roleName}` : "Use a valid invitation link from the room host."}</p>
              </div>
            </div>
            <label>
              Your name
              <input
                value={guestName}
                onChange={(event) => setGuestName(event.target.value)}
                placeholder="Alex Chen"
                disabled={!preview || resolving || loading}
                required
              />
            </label>
            <button
              className="primary-button wide"
              disabled={!preview || !guestName.trim() || resolving || loading}
            >
              {loading ? "Joining..." : "Join session  →"}
            </button>
            <small>{guestHelper}</small>
          </form>
          {error && <div className="error-banner">{error}</div>}
        </div>
      </section>
    </AppShell>
  );
}
