import { FormEvent, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { api } from "../lib/api";
import { saveAuthSession, saveParticipant } from "../lib/session";

export default function AuthPage() {
  const navigate = useNavigate();
  const { roomId, roomRoleId } = useParams();
  const isInviteRoute = Boolean(roomId && roomRoleId);
  const [email, setEmail] = useState("");
  const [guestName, setGuestName] = useState("");
  const [linkRequested, setLinkRequested] = useState(false);
  const [magicToken, setMagicToken] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const guestHelper = useMemo(
    () => isInviteRoute ? "Your invite is ready. Enter your name to join." : "Open a role-specific invite link to join as a guest.",
    [isInviteRoute],
  );

  async function requestMagicLink(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await api.requestMagicLink(email);
      setLinkRequested(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to request magic link");
    } finally {
      setLoading(false);
    }
  }

  async function verifyAndContinue() {
    if (!magicToken.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const session = await api.verifyMagicLink(magicToken);
      saveAuthSession(session);
      navigate("/templates");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to verify magic link");
    } finally {
      setLoading(false);
    }
  }

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
          <p>Role-based visibility for live collaborative calls.</p>
        </div>

        <div className="auth-stack">
          <form className="surface-card auth-card" onSubmit={requestMagicLink}>
            <div className="card-heading-row">
              <div className="icon-tile">✉</div>
              <div>
                <h2>Request a magic link</h2>
                <p>We’ll email you a secure link to join your session.</p>
              </div>
            </div>
            <label>
              Work email
              <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@company.com" required />
            </label>
            <button className="primary-button wide" disabled={loading}>{loading ? "Working..." : "Email me a magic link  →"}</button>
            <small>For session hosts.</small>
            {linkRequested && (
              <div className="demo-token-box">
                <strong>Check your email for the magic link</strong>
                <label>
                  Paste the token from the link
                  <input value={magicToken} onChange={(event) => setMagicToken(event.target.value)} placeholder="Magic link token" />
                </label>
                <button type="button" className="secondary-button" onClick={verifyAndContinue} disabled={loading || !magicToken.trim()}>Verify & continue</button>
              </div>
            )}
          </form>

          <div className="or-divider"><span />or<span /></div>

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
