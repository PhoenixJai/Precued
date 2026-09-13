import { FormEvent, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { api } from "../lib/api";
import { getAuthSession } from "../lib/session";
import { validateNewTemplateRole } from "../lib/templateRoleBuilder";
import type { TemplateRoleDefinition, TemplateSummary } from "../types/precued";

/**
 * Precued_Issues_Update_3.md, M-Templates: the custom role builder AC.
 * Deliberately just the template + its role definitions, wired to
 * persistence — not yet reachable from room creation (TemplatePickerPage
 * still only creates rooms from the three built-in templates) and no
 * visibility-permission-matrix editing, both explicitly out of scope for
 * this milestone's first chunk.
 */
export default function CustomTemplateBuilderPage() {
  const navigate = useNavigate();
  const { templateId } = useParams();
  const auth = getAuthSession();

  useEffect(() => {
    if (!auth) navigate("/");
  }, [auth, navigate]);

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
    <AppShell>
      <section className="custom-template-page page-center-narrow">
        <div className="page-heading centered">
          <h1>Build a Custom Template</h1>
          <p>Name it, then add the roles it needs. You can always come back and add more.</p>
        </div>
        <form className="surface-card auth-card" onSubmit={createTemplate}>
          <label>
            Template name
            <input value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. Panel Interview" required />
          </label>
          <button className="primary-button wide" disabled={loading || !name.trim()}>
            {loading ? "Creating..." : "Create and add roles  →"}
          </button>
        </form>
        {error && <div className="error-banner">{error}</div>}
      </section>
    </AppShell>
  );
}

function RoleBuilder({ templateId, authSessionToken }: { templateId: string; authSessionToken: string }) {
  const navigate = useNavigate();
  const [template, setTemplate] = useState<TemplateSummary | null>(null);
  const [roles, setRoles] = useState<TemplateRoleDefinition[]>([]);
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
  }

  useEffect(() => {
    refresh().catch((err) => setError(err instanceof Error ? err.message : "Unable to load this template"));
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

  return (
    <AppShell showTaglines={false}>
      <section className="custom-template-page page-center-wide">
        <button className="text-button back-button" onClick={() => navigate("/templates")}>← Back to templates</button>
        <div className="page-heading">
          <h1>{template?.name ?? "Loading..."}</h1>
          <p>Add the roles this template needs. At most one can be the host role.</p>
        </div>

        <div className="setup-layout">
          <div className="surface-card setup-main-card">
            <div className="card-heading-row setup-card-heading">
              <div className="icon-tile">♙</div>
              <div>
                <h2>Roles</h2>
                <p>{roles.length ? `${roles.length} role${roles.length === 1 ? "" : "s"} so far.` : "No roles yet."}</p>
              </div>
            </div>

            {roles.map((role) => (
              <div className="role-setup-row" key={role.id}>
                <div className="role-summary">
                  <div className="round-role-icon">♙</div>
                  <div>
                    <h3>{role.name} {role.isHostRole && <span className="host-badge">Host</span>} {role.isGuestRole && <span className="host-badge">Guest</span>}</h3>
                    <small>key: {role.roleKey}{role.maxMembers !== null ? ` · max ${role.maxMembers}` : ""}</small>
                  </div>
                </div>
                <div className="row-status">
                  <button className="text-button" disabled={loading} onClick={() => removeRole(role.id)}>Remove</button>
                </div>
              </div>
            ))}
          </div>

          <aside className="surface-card readiness-card">
            <div className="card-heading-row">
              <div className="icon-tile">＋</div>
              <div><h2>Add a role</h2><p>Role key is a stable identifier (e.g. "juror") — it can't be changed later.</p></div>
            </div>
            <form onSubmit={addRole} className="auth-card">
              <label>
                Role key
                <input value={roleKey} onChange={(event) => setRoleKey(event.target.value)} placeholder="juror" required />
              </label>
              <label>
                Display name
                <input value={roleName} onChange={(event) => setRoleName(event.target.value)} placeholder="Juror" required />
              </label>
              <label>
                Max members (optional)
                <input
                  type="number"
                  min={1}
                  value={maxMembers}
                  onChange={(event) => setMaxMembers(event.target.value)}
                  placeholder="Unlimited"
                />
              </label>
              <label className="checkbox-label">
                <input type="checkbox" checked={isHostRole} onChange={(event) => setIsHostRole(event.target.checked)} />
                Host role
              </label>
              <label className="checkbox-label">
                <input type="checkbox" checked={isGuestRole} onChange={(event) => setIsGuestRole(event.target.checked)} />
                Guest role
              </label>
              <button className="primary-button wide" disabled={loading}>{loading ? "Adding..." : "Add role"}</button>
            </form>
          </aside>
        </div>
        {error && <div className="error-banner">{error}</div>}
      </section>
    </AppShell>
  );
}
