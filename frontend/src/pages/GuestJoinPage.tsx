import { FormEvent, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { api } from "../lib/api";
import { saveParticipant } from "../lib/session";

/**
 * Auth & Account Overhaul (supersedes Issue 1's hybrid magic-link decision
 * for hosts): this used to be the combined "/" sign-in page — magic-link
 * request/verify for hosts, guest join below it. Hosts now get real
 * accounts (LoginPage/SignupPage); this page shrinks to exactly what a
 * guest hits via a room invite link, and lives at /join/:roomId/:roomRoleId
 * instead of "/". The invite-link shape (roomId + roomRoleId, not a real
 * Invite token) is unchanged here — that's a separate, later rework.
 */
export default function GuestJoinPage() {
  const navigate = useNavigate();
  const { roomId, roomRoleId } = useParams();
  const isInviteRoute = Boolean(roomId && roomRoleId);
  const [guestName, setGuestName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const guestHelper = useMemo(
    () => isInviteRoute ? "Your invite is ready. Enter your name to join." : "Open a role-specific invite link to join as a guest.",
    [isInviteRoute],
  );

  async function joinGuest(event: FormEvent) {
    event.preventDefault();
    if (!roomId || !roomRoleId) return;
    setLoading(true);
    setError(null);
    try {
      const roles = await api.getRoomRoles(roomId);
      const role = roles.find((item) => item.id === roomRoleId);
      if (!role || role.isHostRole) {
        throw new Error("This invite link does not resolve to a guest role.");
      }
      const participant = await api.joinRoom(roomId, guestName.trim(), null);
      // Save before assignRole: that call requires the session this join
      // just issued (ParticipantSessionInterceptor, backend), read from
      // storage on every request via lib/api.ts's request().
      saveParticipant({
        id: participant.id,
        roomId,
        roomRoleId: role.id,
        roleKey: role.roleKey,
        roleName: role.name,
        isHost: false,
        displayName: participant.displayName,
        userId: null,
        sessionToken: participant.sessionToken,
      });
      await api.assignRole(participant.id, role.id);
      navigate(`/rooms/${roomId}/call`);
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
          <p>Enter your name to join as a guest.</p>
        </div>

        <div className="auth-stack">
          <form className="surface-card auth-card" onSubmit={joinGuest}>
            <div className="card-heading-row">
              <div className="icon-tile">♙</div>
              <div>
                <h2>Join as guest</h2>
                <p>Enter your name and join with an invite link.</p>
              </div>
            </div>
            <label>
              Your name
              <input value={guestName} onChange={(event) => setGuestName(event.target.value)} placeholder="Alex Chen" required />
            </label>
            <button className="primary-button wide" disabled={!isInviteRoute || loading}>{loading ? "Joining..." : "Join with invite link  →"}</button>
            <small>{guestHelper}</small>
          </form>
          {error && <div className="error-banner">{error}</div>}
        </div>
      </section>
    </AppShell>
  );
}
