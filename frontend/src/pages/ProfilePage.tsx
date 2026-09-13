import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { api } from "../lib/api";
import { getAuthSession } from "../lib/session";
import type { TemplateSummary } from "../types/precued";

/**
 * Auth & Account Overhaul: the logged-in Account Holder's home. "Your
 * templates" lists real data via GET /api/templates/mine — ownership
 * already works today via Template.createdBy (set at creation in
 * TemplateService#createCustomTemplate), so this does not depend on
 * Step 3's owner_user_id/is_builtin/visibility migration, which is a
 * column rename/addition for a separate, not-yet-built concern (public
 * template sharing). "Your rooms" stays a placeholder — no "list my
 * rooms" endpoint exists yet.
 *
 * Log out lives only in AppShell's account menu now, reachable from every
 * authenticated page — not duplicated here.
 */
export default function ProfilePage() {
  const navigate = useNavigate();
  const auth = getAuthSession();
  const [templates, setTemplates] = useState<TemplateSummary[]>([]);
  const [templatesError, setTemplatesError] = useState<string | null>(null);

  // getAuthSession() re-parses sessionStorage into a new object every
  // call, so it's never referentially stable across renders — both effects
  // below depend on the token itself, not on auth (matches
  // CallPage/RoomSetupPage's me?.id convention for the same reason with
  // getParticipant()). Depending on auth directly here previously caused
  // an actual infinite GET /api/templates/mine loop: each render re-read a
  // new auth object, which re-ran the effect, which called setTemplates,
  // which triggered the next render.
  useEffect(() => {
    if (!auth) navigate("/login", { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth?.sessionToken, navigate]);

  useEffect(() => {
    if (!auth) return;
    api.listMyTemplates(auth.sessionToken)
      .then(setTemplates)
      .catch((err) => setTemplatesError(err instanceof Error ? err.message : "Unable to load your templates"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth?.sessionToken]);

  if (!auth) return null;

  return (
    <AppShell showTaglines={false}>
      <section className="profile-page page-center-wide">
        <div className="page-heading">
          <h1>Welcome, {auth.displayName}</h1>
          <p>{auth.email}</p>
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
            {templatesError ? (
              <p className="empty-state-note">{templatesError}</p>
            ) : templates.length === 0 ? (
              <p className="empty-state-note">You haven't created any templates yet.</p>
            ) : (
              <ul className="profile-template-list">
                {templates.map((template) => (
                  <li key={template.id}>
                    <button className="text-button" onClick={() => navigate(`/templates/custom/${template.id}`)}>
                      {template.name}
                    </button>
                  </li>
                ))}
              </ul>
            )}
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
