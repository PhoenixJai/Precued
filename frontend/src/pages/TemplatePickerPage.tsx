import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { api } from "../lib/api";
import {
  customTemplateLaunchCards,
  hasHostRole,
  roomTemplateTitle,
  type CustomTemplateLaunchCard,
} from "../lib/customTemplateLaunch";
import { findHostRole } from "../lib/roleSetup";
import { getAuthSession, saveParticipant } from "../lib/session";
import {
  filterLibraryCards,
  PUBLIC_TEMPLATE_CARDS,
  type LibraryTemplateCard,
} from "../lib/templateLibrary";
import type { TemplateRoleDefinition } from "../types/precued";

type LibraryCollection = "public" | "private";

export default function TemplatePickerPage() {
  const navigate = useNavigate();
  const auth = getAuthSession();
  const [activeCollection, setActiveCollection] = useState<LibraryCollection>("public");
  const [searchQuery, setSearchQuery] = useState("");
  const [creatingTemplateId, setCreatingTemplateId] = useState<string | null>(null);
  const [customTemplates, setCustomTemplates] = useState<CustomTemplateLaunchCard[]>([]);
  const [customRoles, setCustomRoles] = useState<Record<string, TemplateRoleDefinition[]>>({});
  const [customTemplatesLoaded, setCustomTemplatesLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const currentAuth = getAuthSession();
    if (!currentAuth) {
      setCustomTemplatesLoaded(true);
      return;
    }

    const authToken = currentAuth.sessionToken;
    let cancelled = false;

    async function loadCustomTemplates() {
      try {
        const summaries = await api.listMyTemplates(authToken);
        const cards = customTemplateLaunchCards(summaries);
        const roleEntries = await Promise.all(
          cards.map(async (card) => [card.id, await api.getTemplateRoles(card.id, authToken)] as const),
        );
        if (cancelled) return;
        setCustomTemplates(cards);
        setCustomRoles(Object.fromEntries(roleEntries));
        setCustomTemplatesLoaded(true);
      } catch (err) {
        if (!cancelled) {
          setCustomTemplatesLoaded(true);
          setError(err instanceof Error ? err.message : "Unable to load your private templates");
        }
      }
    }

    void loadCustomTemplates();
    return () => { cancelled = true; };
  }, []);

  async function createAndJoinSession(templateId: string, displayName: string, isCustom = false) {
    const currentAuth = getAuthSession();
    if (!currentAuth) {
      navigate("/login");
      return;
    }

    setCreatingTemplateId(templateId);
    setError(null);
    try {
      if (isCustom) {
        const sourceRoles = await api.getTemplateRoles(templateId, currentAuth.sessionToken);
        if (!hasHostRole(sourceRoles)) {
          throw new Error(`Add a host role to ${displayName} before starting a session.`);
        }
      }

      const room = await api.createRoom(templateId, currentAuth.sessionToken);
      const roles = await api.getRoomRoles(room.id);
      const hostRole = findHostRole(roles);
      if (!hostRole) {
        throw new Error(`No host role was created for this ${roomTemplateTitle(templateId, displayName)} session.`);
      }

      const participant = await api.joinRoom(room.id, currentAuth.displayName, currentAuth.userId, currentAuth.sessionToken);
      saveParticipant({
        id: participant.id,
        roomId: room.id,
        roomRoleId: hostRole.id,
        roleKey: hostRole.roleKey,
        roleName: hostRole.name,
        isHost: true,
        displayName: participant.displayName,
        userId: currentAuth.userId,
        sessionToken: participant.sessionToken,
      });
      await api.assignRole(participant.id, hostRole.id);

      navigate(`/rooms/${room.id}/setup`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to create session");
    } finally {
      setCreatingTemplateId(null);
    }
  }

  const privateCards = useMemo<LibraryTemplateCard[]>(() => (
    customTemplates.map((template) => ({
      id: template.id,
      title: template.title,
      description: template.description,
      roles: (customRoles[template.id] ?? []).map((role) => role.name),
      visibility: "PRIVATE",
      icon: "✦",
      context: "Your custom workflow",
      highlights: ["Private to your account", "Custom role configuration", "Reusable session structure"],
    }))
  ), [customRoles, customTemplates]);

  const visiblePublicTemplates = useMemo(
    () => filterLibraryCards(PUBLIC_TEMPLATE_CARDS, searchQuery),
    [searchQuery],
  );
  const visiblePrivateTemplates = useMemo(
    () => filterLibraryCards(privateCards, searchQuery),
    [privateCards, searchQuery],
  );
  const creating = creatingTemplateId !== null;

  function selectCollection(collection: LibraryCollection) {
    setActiveCollection(collection);
    setSearchQuery("");
    setError(null);
  }

  return (
    <AppShell showTaglines={false}>
      <section className="template-library-page page-center-wide">
        <div className="template-library-hero">
          <div className="template-library-heading">
            <span className="eyebrow">TEMPLATE LIBRARY</span>
            <h1>Choose the structure for your next session.</h1>
            <p>Start from a Precued workflow or return to one of your private templates.</p>
          </div>
          <aside className="template-library-promise" aria-label="Precued product promise">
            <span className="template-library-promise-mark" aria-hidden="true">→</span>
            <div>
              <strong>Structure turns conversation into progress.</strong>
              <p>Clear roles and intentional visibility keep complex sessions moving.</p>
            </div>
          </aside>
        </div>

        <div className="template-library-controls">
          <div className="template-library-tabs" role="tablist" aria-label="Template collections">
            <button
              type="button"
              role="tab"
              aria-selected={activeCollection === "public"}
              className={activeCollection === "public" ? "active" : ""}
              onClick={() => selectCollection("public")}
            >
              Public Templates
              <span>{PUBLIC_TEMPLATE_CARDS.length}</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeCollection === "private"}
              className={activeCollection === "private" ? "active" : ""}
              onClick={() => selectCollection("private")}
            >
              My Private Templates
              {auth && <span>{customTemplates.length}</span>}
            </button>
          </div>

          <label className="template-library-search">
            <span className="sr-only">Search templates</span>
            <span aria-hidden="true">⌕</span>
            <input
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder={activeCollection === "public" ? "Search public templates" : "Search my templates"}
            />
          </label>
        </div>

        {activeCollection === "public" ? (
          <div className="template-library-section" role="tabpanel">
            <div className="template-library-section-heading">
              <div>
                <h2>Public Templates</h2>
                <p>Built and maintained by Precued. These are the only public templates in the current release.</p>
              </div>
              {auth && (
                <button className="secondary-button" onClick={() => navigate("/templates/custom/new")}>
                  + Create private template
                </button>
              )}
            </div>

            {visiblePublicTemplates.length === 0 ? (
              <div className="template-library-empty surface-card">
                <strong>No public templates match “{searchQuery}”.</strong>
                <button className="text-button" onClick={() => setSearchQuery("")}>Clear search</button>
              </div>
            ) : (
              <div className="template-library-grid">
                {visiblePublicTemplates.map((template) => (
                  <article key={template.id} className="library-template-card surface-card">
                    <div className="library-template-card-header">
                      <div className="library-template-icon" aria-hidden="true">{template.icon}</div>
                      <div className="library-template-statuses">
                        <span className="library-visibility-badge public">Public</span>
                        {template.badge && <span className="library-featured-badge">{template.badge}</span>}
                      </div>
                    </div>
                    <div className="library-template-copy">
                      <p className="library-template-context">{template.context}</p>
                      <h3>{template.title}</h3>
                      <p>{template.description}</p>
                    </div>
                    <div className="library-template-roles" aria-label={`${template.title} roles`}>
                      {template.roles.map((role) => <span key={role}>{role}</span>)}
                    </div>
                    <ul className="library-template-meta">
                      {template.highlights.map((highlight) => <li key={highlight}>{highlight}</li>)}
                    </ul>
                    <button
                      className="primary-button wide"
                      onClick={() => createAndJoinSession(template.id, template.title)}
                      disabled={creating}
                    >
                      {creatingTemplateId === template.id ? "Creating session…" : "Use template"}
                    </button>
                  </article>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className="template-library-section" role="tabpanel">
            <div className="template-library-section-heading">
              <div>
                <h2>My Private Templates</h2>
                <p>Custom templates are visible only to you. Public publishing is not available yet.</p>
              </div>
              {auth && (
                <button className="primary-button" onClick={() => navigate("/templates/custom/new")}>
                  + New template
                </button>
              )}
            </div>

            {!auth ? (
              <div className="template-library-auth surface-card">
                <div className="library-template-icon" aria-hidden="true">✦</div>
                <div>
                  <h3>Sign in to access your private templates.</h3>
                  <p>Your custom templates stay private to your account.</p>
                </div>
                <button className="primary-button" onClick={() => navigate("/login")}>Sign in</button>
              </div>
            ) : !customTemplatesLoaded ? (
              <div className="template-library-empty surface-card">Loading your private templates…</div>
            ) : visiblePrivateTemplates.length === 0 && customTemplates.length === 0 ? (
              <div className="template-library-empty template-library-empty-create surface-card">
                <div className="library-template-icon" aria-hidden="true">✦</div>
                <strong>You haven't created a private template yet.</strong>
                <p>Build reusable roles and session structure for the way you work.</p>
                <button className="primary-button" onClick={() => navigate("/templates/custom/new")}>Create your first template</button>
              </div>
            ) : visiblePrivateTemplates.length === 0 ? (
              <div className="template-library-empty surface-card">
                <strong>No private templates match “{searchQuery}”.</strong>
                <button className="text-button" onClick={() => setSearchQuery("")}>Clear search</button>
              </div>
            ) : (
              <div className="template-library-grid">
                {visiblePrivateTemplates.map((template) => {
                  const roles = customRoles[template.id];
                  const launchReady = Boolean(roles && hasHostRole(roles));
                  return (
                    <article key={template.id} className="library-template-card surface-card">
                      <div className="library-template-card-header">
                        <div className="library-template-icon private" aria-hidden="true">{template.icon}</div>
                        <span className="library-visibility-badge private">Private</span>
                      </div>
                      <div className="library-template-copy">
                        <p className="library-template-context">{template.context}</p>
                        <h3>{template.title}</h3>
                        <p>{template.description}</p>
                      </div>
                      <div className="library-template-roles" aria-label={`${template.title} roles`}>
                        {!roles ? (
                          <span>Loading roles…</span>
                        ) : roles.length === 0 ? (
                          <span>No roles yet</span>
                        ) : (
                          roles.map((role) => <span key={role.id}>{role.name}</span>)
                        )}
                      </div>
                      <ul className="library-template-meta">
                        <li>Private to your account</li>
                        <li>{launchReady ? "Host role configured" : "Host role required before launch"}</li>
                        <li>Editable custom structure</li>
                      </ul>
                      <div className="library-template-actions">
                        <button
                          className="primary-button"
                          disabled={creating || !launchReady}
                          onClick={() => createAndJoinSession(template.id, template.title, true)}
                        >
                          {creatingTemplateId === template.id ? "Creating session…" : "Use template"}
                        </button>
                        <button
                          className="secondary-button"
                          disabled={creating}
                          onClick={() => navigate(`/templates/custom/${template.id}`)}
                        >
                          Edit
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {error && <div className="error-banner template-library-error">{error}</div>}
      </section>
    </AppShell>
  );
}
