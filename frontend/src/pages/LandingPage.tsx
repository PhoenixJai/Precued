import { useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { getAuthSession } from "../lib/session";

/**
 * Auth & Account Overhaul: "/" is now public and unauthenticated only —
 * product pitch + Log In / Sign Up, nothing else. A logged-in Account
 * Holder never sees this again once authenticated; they're bounced
 * straight to /profile. (Guests never land here at all — they arrive via
 * a room invite link at /join/:roomId/:roomRoleId.)
 */
export default function LandingPage() {
  const navigate = useNavigate();
  const auth = getAuthSession();

  useEffect(() => {
    if (auth) navigate("/profile", { replace: true });
    // getAuthSession() re-parses sessionStorage into a new object every
    // call, so it's never referentially stable across renders — depend on
    // the token itself (matches CallPage/RoomSetupPage's me?.id convention
    // for the same reason with getParticipant()).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth?.sessionToken, navigate]);

  if (auth) return null; // avoid flashing landing content while the redirect above runs

  return (
    <AppShell>
      <section className="landing-page page-center-narrow">
        <div className="page-heading centered">
          <h1>Precued</h1>
          <p>Role-based visibility for live collaborative calls. Build a template, invite your roles, control who sees what — live.</p>
        </div>
        <div className="landing-actions">
          <Link to="/signup" className="primary-button wide">Sign Up  →</Link>
          <Link to="/login" className="secondary-button wide">Log In</Link>
        </div>
      </section>
    </AppShell>
  );
}
