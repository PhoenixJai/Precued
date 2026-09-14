import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AppShell, WorkspaceShell } from "../components/AppShell";
import { api } from "../lib/api";
import { getAuthSession } from "../lib/session";
import type { TemplateSummary } from "../types/precued";

export default function ProfilePage() {
  const navigate = useNavigate();
  const auth = getAuthSession();
  const [templates, setTemplates] = useState<TemplateSummary[]>([]);
  const [templatesError, setTemplatesError] = useState<string | null>(null);

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
      <WorkspaceShell>
        <section className="profile-page page-center-wide workspace-page">
          <div className="page-heading workspace-page-heading">
            <span className="eyebrow">OVERVIEW</span>
            <h1>Welcome, {auth.displayName}</h1>
            <p>{auth.email}</p>
          </div>

          <div className="profile-actions">
            <article className="surface-card profile-action-card">
              <div className="icon-tile large">✦</div>
              <h2>Create a new template</h2>
              <p>Build your own private template with the roles your session needs.</p>
              <button className="primary-button wide" onClick={() => navigate("/templates/custom/new")}>
                Build a template  →
              </button>
            </article>
            <article className="surface-card profile-action-card">
              <div className="icon-tile large">▧</div>
              <h2>Start a session from a template</h2>
              <p>Choose a Precued public template or one of your own private templates.</p>
              <button className="primary-button wide" onClick={() => navigate("/templates")}>
                Browse templates  →
              </button>
            </article>
          </div>

          <div className="profile-lists">
            <div className="surface-card profile-list-card">
              <h2>Your private templates</h2>
              {templatesError ? (
                <p className="empty-state-note">{templatesError}</p>
              ) : templates.length === 0 ? (
                <p className="empty-state-note">You haven't created any private templates yet.</p>
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
              <h2>Your sessions</h2>
              <p className="empty-state-note">You haven't started any sessions yet.</p>
            </div>
          </div>
        </section>
      </WorkspaceShell>
    </AppShell>
  );
}
