import { FormEvent, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell, WorkspaceShell } from "../components/AppShell";
import { TemplateSessionFlowBuilder } from "../components/TemplateSessionFlowBuilder";
import { api } from "../lib/api";
import { getAuthSession } from "../lib/session";
import {
  TEMPLATE_BUILDER_SECTIONS,
  roleSummaryLabels,
  type TemplateBuilderSection,
} from "../lib/templateBuilderUi";
import { validateNewTemplateRole } from "../lib/templateRoleBuilder";
import type { TemplateRoleDefinition, TemplateSummary } from "../types/precued";

/**
 * Custom Template authoring: an agenda-first structure editor backed by the
 * existing TemplateRole + TemplateSessionFlow APIs. Role changes persist
 * immediately; Session Structure is saved explicitly by the flow builder.
 */
export default function CustomTemplateBuilderPage() {
  const navigate = useNavigate();
  const { templateId } = useParams();
  const auth = getAuthSession();

  useEffect(() => {
    if (!auth) navigate("/login");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth?.sessionToken, navigate]);

  if (!auth) return null;

  return templateId
    ? <RoleBuilder templateId={templateId} authSessionToken={auth.sessionToken} />
    : <NewTemplateForm authSessionToken={auth.sessionToken} />;
}

function NewTemplateForm({ authSessionToken }: { authSessionToken: string }) {
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function createTemplate(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const template = await api.createTemplate(name.trim(), authSessionToken);
      navigate(`/templates/custom/${template.id}`, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to create template");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AppShell showTaglines={false}>
      <WorkspaceShell>
        <section className="template-builder-page template-builder-create page-center-wide workspace-page">
          <button className="text-button builder-back-link" onClick={() => navigate("/templates")}>← Back to Template Library</button>
          <div className="builder-create-layout">
            <div className="builder-create-copy">
              <span className="eyebrow">PRIVATE TEMPLATE</span>
              <h1>Build a reusable session structure.</h1>
              <p>Name the template first. Then define its agenda and the roles that participate in each stage.</p>
              <div className="builder-principles">
                <span>1. Structure the session</span>
                <span>2. Define the roles</span>
                <span>3. Reuse it whenever you need it</span>
              </div>
            </div>
            <form className="surface-card builder-create-card" onSubmit={createTemplate}>
              <div>
                <span className="builder-card-kicker">START WITH A NAME</span>
                <h2>New private template</h2>
                <p>Only you can see user-created templates right now.</p>
              </div>
              <label>
                Template name
                <input value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. Panel Interview" required />
              </label>
              <button className="primary-button wide" disabled={loading || !name.trim()}>
                {loading ? "Creating..." : "Create template  →"}
              </button>
              {error && <div className="error-banner">{error}</div>}
            </form>
          </div>
        </section>
      </WorkspaceShell>
    </AppShell>
  );
}

function RoleBuilder({ templateId, authSessionToken }: { templateId: string; authSessionToken: string }) {
  const navigate = useNavigate();
  const [template, setTemplate] = useState<TemplateSummary | null>(null);
  const [roles, setRoles] = useState<TemplateRoleDefinition[]>([]);
  const [rolesLoaded, setRolesLoaded] = useState(false);
  const [activeSection, setActiveSection] = useState<TemplateBuilderSection>("Structure");
  const [roleKey, setRoleKey] = useState("");
  const [roleName, setRoleName] = useState("");
  const [isHostRole, setIsHostRole] = useState(false);
  const [isGuestRole, setIsGuestRole] = useState(false);
  const [maxMembers, setMaxMembers] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function refresh() {
    const [nextTemplate, nextRoles] = await Promise.all([
      api.getTemplate(templateId, authSessionToken),
      api.getTemplateRoles(templateId, authSessionToken),
    ]);
    setTemplate(nextTemplate);
    setRoles(nextRoles);
    setRolesLoaded(true);
  }

  useEffect(() => {
    refresh().catch((err) => {
      setRolesLoaded(true);
      setError(err instanceof Error ? err.message : "Unable to load this template");
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [templateId]);

  async function addRole(event: FormEvent) {
    event.preventDefault();
    const parsedMaxMembers = maxMembers.trim() ? Number(maxMembers) : null;
    const draft = { roleKey: roleKey.trim(), name: roleName.trim(), isHostRole, isGuestRole, maxMembers: parsedMaxMembers };
    const validationError = validateNewTemplateRole(draft, roles);
    if (validationError) {
      setError(validationError);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await api.addTemplateRole(templateId, draft, authSessionToken);
      setRoleKey("");
      setRoleName("");
      setIsHostRole(false);
      setIsGuestRole(false);
      setMaxMembers("");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to add role");
    } finally {
      setLoading(false);
    }
  }

  async function removeRole(roleId: string) {
    setLoading(true);
    setError(null);
    try {
      await api.removeTemplateRole(templateId, roleId, authSessionToken);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to remove role");
    } finally {
      setLoading(false);
    }
  }

  const hostRole = roles.find((role) => role.isHostRole);

  return (
    <AppShell showTaglines={false}>
      <WorkspaceShell>
        <section className="template-builder-page page-center-wide workspace-page">
          <div className="template-builder-toolbar">
            <button className="text-button builder-back-link" onClick={() => navigate("/templates")}>← Template Library</button>
            <div className="template-builder-toolbar-actions">
              <span className="status-pill builder-private-pill">Private</span>
              <button className="secondary-button compact" onClick={() => navigate("/profile")}>Done</button>
            </div>
          </div>

          <header className="template-builder-heading">
            <div>
              <span className="eyebrow">TEMPLATE BUILDER</span>
              <h1>{template?.name ?? "Loading..."}</h1>
              <p>Design the structure first, then define who participates in each part of the session.</p>
            </div>
            <div className="builder-heading-summary" aria-label="Template summary">
              <div><strong>{roles.length}</strong><span>Roles</span></div>
              <div><strong>{hostRole ? hostRole.name : "—"}</strong><span>Host role</span></div>
            </div>
          </header>

          <nav className="template-builder-tabs" aria-label="Template builder sections">
            {TEMPLATE_BUILDER_SECTIONS.map((section) => (
              <button
                key={section}
                type="button"
                className={activeSection === section ? "builder-tab builder-tab-active" : "builder-tab"}
                aria-current={activeSection === section ? "page" : undefined}
                onClick={() => setActiveSection(section)}
              >
                {section}
              </button>
            ))}
          </nav>

          {activeSection === "Structure" ? (
            <div className="builder-structure-layout">
              <div className="builder-main-column">
                <div className="surface-card builder-section-card builder-structure-intro">
                  <div>
                    <span className="builder-card-kicker">SESSION STRUCTURE</span>
                    <h2>Build the agenda participants move through.</h2>
                    <p>Each stage can have a duration and active roles. New sessions take a snapshot of this structure when they are created.</p>
                  </div>
                  <button className="secondary-button compact" disabled title="Agenda import is planned for a future release">
                    Import agenda or rules · Coming soon
                  </button>
                </div>

                <TemplateSessionFlowBuilder
                  templateId={templateId}
                  authSessionToken={authSessionToken}
                  roles={roles}
                  rolesLoaded={rolesLoaded}
                />
              </div>

              <aside className="surface-card builder-role-summary-card">
                <div className="builder-aside-heading">
                  <div>
                    <span className="builder-card-kicker">ROLES</span>
                    <h2>Who participates</h2>
                  </div>
                  <button className="text-button" onClick={() => setActiveSection("Roles")}>Edit roles →</button>
                </div>
                {roles.length === 0 ? (
                  <div className="builder-empty-panel">
                    <strong>No roles yet</strong>
                    <p>Add roles before assigning participants to stages.</p>
                    <button className="secondary-button compact" onClick={() => setActiveSection("Roles")}>Add roles</button>
                  </div>
                ) : (
                  <div className="builder-role-summary-list">
                    {roles.map((role) => (
                      <div className="builder-role-summary-row" key={role.id}>
                        <div className="builder-role-avatar" aria-hidden="true">{role.name.slice(0, 1).toUpperCase()}</div>
                        <div className="builder-role-summary-copy">
                          <strong>{role.name}</strong>
                          <small>{role.roleKey}</small>
                          <div className="builder-role-labels">
                            {roleSummaryLabels(role).map((label) => <span key={label}>{label}</span>)}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
                <div className="builder-visibility-note">
                  <span aria-hidden="true">◈</span>
                  <div>
                    <strong>Visibility controls come alive in sessions.</strong>
                    <p>This PR does not invent template-level content or visibility rules that the API does not support yet.</p>
                  </div>
                </div>
              </aside>
            </div>
          ) : (
            <div className="builder-roles-layout">
              <div className="surface-card builder-section-card">
                <div className="builder-section-heading">
                  <div>
                    <span className="builder-card-kicker">ROLE DEFINITIONS</span>
                    <h2>Roles</h2>
                    <p>Roles control who can join a session and become the basis for runtime visibility.</p>
                  </div>
                  <span className="builder-count-badge">{roles.length} total</span>
                </div>

                {roles.length === 0 ? (
                  <div className="builder-empty-panel large">
                    <strong>No roles yet.</strong>
                    <p>Add at least one host role before this template can launch a session.</p>
                  </div>
                ) : (
                  <div className="builder-role-table">
                    {roles.map((role) => (
                      <div className="builder-role-row" key={role.id}>
                        <div className="builder-role-avatar" aria-hidden="true">{role.name.slice(0, 1).toUpperCase()}</div>
                        <div className="builder-role-row-main">
                          <strong>{role.name}</strong>
                          <small>Stable key: {role.roleKey}</small>
                        </div>
                        <div className="builder-role-labels builder-role-row-labels">
                          {roleSummaryLabels(role).map((label) => <span key={label}>{label}</span>)}
                        </div>
                        <button className="text-button builder-remove-role" disabled={loading} onClick={() => { void removeRole(role.id); }}>Remove</button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <aside className="surface-card builder-add-role-card">
                <div className="builder-aside-heading">
                  <div>
                    <span className="builder-card-kicker">ADD ROLE</span>
                    <h2>New role</h2>
                  </div>
                </div>
                <p className="builder-aside-copy">Role keys are stable identifiers and cannot be changed later.</p>
                <form onSubmit={addRole} className="builder-role-form">
                  <label>
                    Role key
                    <input value={roleKey} onChange={(event) => setRoleKey(event.target.value)} placeholder="interviewer" required />
                  </label>
                  <label>
                    Display name
                    <input value={roleName} onChange={(event) => setRoleName(event.target.value)} placeholder="Interviewer" required />
                  </label>
                  <label>
                    Max members <span>(optional)</span>
                    <input
                      type="number"
                      min={1}
                      value={maxMembers}
                      onChange={(event) => setMaxMembers(event.target.value)}
                      placeholder="Unlimited"
                    />
                  </label>
                  <div className="builder-role-switches">
                    <label className="checkbox-label">
                      <input type="checkbox" checked={isHostRole} onChange={(event) => setIsHostRole(event.target.checked)} />
                      Host role
                    </label>
                    <label className="checkbox-label">
                      <input type="checkbox" checked={isGuestRole} onChange={(event) => setIsGuestRole(event.target.checked)} />
                      Guest role
                    </label>
                  </div>
                  <button className="primary-button wide" disabled={loading}>{loading ? "Adding..." : "Add role"}</button>
                </form>
              </aside>
            </div>
          )}

          {error && <div className="error-banner builder-page-error">{error}</div>}
        </section>
      </WorkspaceShell>
    </AppShell>
  );
}
