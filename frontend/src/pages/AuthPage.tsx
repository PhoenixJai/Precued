import { FormEvent, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { AppShell, Brand } from "../components/AppShell";
import { api, describeMagicLinkVerifyError } from "../lib/api";
import { saveAuthSession, saveParticipant } from "../lib/session";

export default function AuthPage() {
  const navigate = useNavigate();
  const { roomId, roomRoleId } = useParams();
  const [searchParams] = useSearchParams();
  const isInviteRoute = Boolean(roomId && roomRoleId);
  const [email, setEmail] = useState("");
  const [guestName, setGuestName] = useState("");
  const [linkRequested, setLinkRequested] = useState(false);
  const [loading, setLoading] = useState(false);
  // lib/api.ts's request() redirects here (full navigation, not a client
  // route change) on any 401, appending this so the reason survives that
  // reload instead of getting lost as unpersisted component state.
  const [error, setError] = useState<string | null>(
    searchParams.get("sessionExpired") ? "Your session has expired. Please sign in again." : null,
  );

  // AuthService emails a link to precued.auth.magic-link.base-url + "?token=...",
  // which points back at this exact route — clicking it lands here with the
  // token already in the URL, no manual copy/paste step.
  const magicLinkToken = searchParams.get("token");
  const [verifying, setVerifying] = useState(Boolean(magicLinkToken));

  useEffect(() => {
    if (!magicLinkToken) return;
    let cancelled = false;
    setVerifying(true);
    setError(null);
    api.verifyMagicLink(magicLinkToken)
      .then((session) => {
        if (cancelled) return;
        saveAuthSession(session);
        navigate("/templates", { replace: true });
      })
      .catch((err) => {
        if (cancelled) return;
        const message = err instanceof Error ? err.message : "This sign-in link is invalid or has expired.";
        setError(describeMagicLinkVerifyError(message));
        setVerifying(false);
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [magicLinkToken]);

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

  if (verifying) {
    return (
      <AppShell>
        <section className="page-center-narrow">
          <div className="surface-card connecting-card">
            <Brand />
            <div className="spinner" aria-label="Signing in" />
            <h1>Signing you in...</h1>
            <p>Verifying your sign-in link.</p>
          </div>
        </section>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <section className="auth-page page-center-narrow">
        <div className="page-heading centered">
          <h1>Join a Precued session</h1>
          <p>Role-based visibility for live collaborative calls.</p>
        </div>

        <div className="auth-stack">
          {linkRequested ? (
            <div className="surface-card auth-card check-email-card">
              <div className="card-heading-row">
                <div className="icon-tile">✉</div>
                <div>
                  <h2>Check your email</h2>
                  <p>We sent a sign-in link to {email}. Click it to continue — this page picks it up automatically.</p>
                </div>
              </div>
              <button type="button" className="text-button" onClick={() => setLinkRequested(false)}>
                Use a different email
              </button>
            </div>
          ) : (
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
            </form>
          )}

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
