import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { clearSession, getAuthSession } from "../lib/session";

/**
 * Auth & Account Overhaul: the logged-in Account Holder's home. "Your
 * templates"/"your rooms" are placeholders — real data (listing what a
 * User owns) is later work, once Step 3's Template.owner_user_id lands.
 */
export default function ProfilePage() {
  const navigate = useNavigate();
  const auth = getAuthSession();

  useEffect(() => {
    if (!auth) navigate("/login", { replace: true });
  }, [auth, navigate]);

  if (!auth) return null;

  function logOut() {
    clearSession();
    navigate("/", { replace: true });
  }

  return (
    <AppShell showTaglines={false}>
      <section className="profile-page page-center-wide">
        <div className="page-heading-row">
          <div className="page-heading">
            <h1>Welcome, {auth.displayName}</h1>
            <p>{auth.email}</p>
          </div>
          <button className="text-button" onClick={logOut}>Log out</button>
        </div>

        <div className="profile-actions">
          <article className="surface-card profile-action-card">
            <div className="icon-tile large">✦</div>
            <h2>Create a new template</h2>
            <p>Build your own template with the roles your session needs.</p>
            <button className="primary-button wide" onClick={() => navigate("/templates/custom/new")}>
              Build a template  →
            </button>
          </article>
          <article className="surface-card profile-action-card">
            <div className="icon-tile large">▧</div>
            <h2>Start a call from an existing template</h2>
            <p>Choose Sales Call, Mock Trial, Lincoln-Douglas Debate, or one of your own.</p>
            <button className="primary-button wide" onClick={() => navigate("/templates")}>
              Browse templates  →
            </button>
          </article>
        </div>

        <div className="profile-lists">
          <div className="surface-card profile-list-card">
            <h2>Your templates</h2>
            <p className="empty-state-note">You haven't created any templates yet.</p>
          </div>
          <div className="surface-card profile-list-card">
            <h2>Your rooms</h2>
            <p className="empty-state-note">You haven't started any rooms yet.</p>
          </div>
        </div>
      </section>
    </AppShell>
  );
}
