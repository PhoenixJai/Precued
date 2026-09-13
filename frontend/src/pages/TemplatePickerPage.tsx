import { useEffect, useState } from "react";
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
import type { TemplateRoleDefinition } from "../types/precued";

const templateCards = [
  {
    id: "sales_call",
    title: "Sales Call",
    description: "Control what internal and external participants can see in real time.",
    roles: ["Sales Rep", "Sales Engineer", "Client"],
    bullets: ["Role-based content visibility", "Built-in collaboration tools", "Optimized for client conversations"],
    badge: "POPULAR",
  },
  {
    id: "mock_trial",
    title: "Mock Trial",
    description: "Structure a realistic trial experience with controlled visibility for each role.",
    roles: ["Judge", "Jury", "Defense", "Prosecution"],
    bullets: ["Role-specific information access", "Support for exhibits and evidence", "Designed for legal teams and education"],
    badge: undefined,
  },
  {
    id: "ld_debate",
    title: "Lincoln-Douglas Debate",
    description: "Facilitate structured academic debates with role-based visibility.",
    roles: ["Judge", "Affirmative", "Negative", "Audience"],
    bullets: ["Separate materials for each side", "Timed rounds and structure", "Ideal for education and competitions"],
    badge: undefined,
  },
] as const;

export default function TemplatePickerPage() {
  const navigate = useNavigate();
  const [creatingTemplateId, setCreatingTemplateId] = useState<string | null>(null);
  const [customTemplates, setCustomTemplates] = useState<CustomTemplateLaunchCard[]>([]);
  const [customRoles, setCustomRoles] = useState<Record<string, TemplateRoleDefinition[]>>({});
  const [customTemplatesLoaded, setCustomTemplatesLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const auth = getAuthSession();
    if (!auth) return;
    let cancelled = false;

    async function loadCustomTemplates() {
      try {
        const summaries = await api.listMyTemplates(auth.sessionToken);
        const cards = customTemplateLaunchCards(summaries);
        const roleEntries = await Promise.all(
          cards.map(async (card) => [card.id, await api.getTemplateRoles(card.id, auth.sessionToken)] as const),
        );
        if (cancelled) return;
        setCustomTemplates(cards);
        setCustomRoles(Object.fromEntries(roleEntries));
        setCustomTemplatesLoaded(true);
      } catch (err) {
        if (!cancelled) {
          setCustomTemplatesLoaded(true);
          setError(err instanceof Error ? err.message : "Unable to load your custom templates");
        }
      }
    }

    void loadCustomTemplates();
    return () => { cancelled = true; };
  }, []);

  async function createAndJoinRoom(templateId: string, displayName: string, isCustom = false) {
    const auth = getAuthSession();
    if (!auth) {
      navigate("/login");
      return;
    }

    setCreatingTemplateId(templateId);
    setError(null);
    try {
      // Custom templates are user-authored and can legitimately be unfinished.
      // Check for a host before creating the Room so a half-built template does
      // not leave behind an orphan Room that nobody can host.
      if (isCustom) {
        const sourceRoles = await api.getTemplateRoles(templateId, auth.sessionToken);
        if (!hasHostRole(sourceRoles)) {
          throw new Error(`Add a host role to ${displayName} before starting a room.`);
        }
      }

      const room = await api.createRoom(templateId, auth.sessionToken);
      const roles = await api.getRoomRoles(room.id);
      const hostRole = findHostRole(roles);
      if (!hostRole) {
        throw new Error(`No host role was created for this ${roomTemplateTitle(templateId, displayName)} room.`);
      }

      const participant = await api.joinRoom(room.id, auth.displayName, auth.userId, auth.sessionToken);
      // Save before assignRole: that call requires the session this join
      // just issued (ParticipantSessionInterceptor, backend), read from
      // storage on every request via lib/api.ts's request().
      saveParticipant({
        id: participant.id,
        roomId: room.id,
        roomRoleId: hostRole.id,
        roleKey: hostRole.roleKey,
        roleName: hostRole.name,
        isHost: true,
        displayName: participant.displayName,
        userId: auth.userId,
        sessionToken: participant.sessionToken,
      });
      await api.assignRole(participant.id, hostRole.id);

      navigate(`/rooms/${room.id}/setup`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to create room");
    } finally {
      setCreatingTemplateId(null);
    }
  }

  const creating = creatingTemplateId !== null;

  return (
    <AppShell>
      <section className="templates-page page-center-wide">
        <div className="page-heading centered">
          <h1>Choose a Template</h1>
          <p>Start with a structured workflow for your next session.</p>
        </div>

        <div className="template-grid">
          {templateCards.map((template, index) => (
            <article key={template.id} className={`surface-card template-card ${index === 0 ? "selected-template" : ""}`}>
              <div className="template-card-top">
                <div className="icon-tile large">{index === 0 ? "♙" : index === 1 ? "⚖" : "◫"}</div>
                <div className="template-title-block">
                  <h2>{template.title}</h2>
                  <p>{template.description}</p>
                </div>
                {template.badge && <span className="popular-badge">{template.badge}</span>}
              </div>
              <div className="role-pills">
                {template.roles.map((role, roleIndex) => <span key={role} className={`role-pill role-${roleIndex}`}>♙ {role}</span>)}
              </div>
              <div className="template-divider" />
              <ul className="feature-list">
                {template.bullets.map((bullet) => <li key={bullet}>✓ {bullet}</li>)}
              </ul>
              <button
                className="primary-button wide"
                onClick={() => createAndJoinRoom(template.id, template.title)}
                disabled={creating}
              >
                {creatingTemplateId === template.id ? "Creating room..." : "Use template  →"}
              </button>
            </article>
          ))}

          {customTemplates.map((template) => {
            const roles = customRoles[template.id];
            const launchReady = Boolean(roles && hasHostRole(roles));
            return (
              <article key={template.id} className="surface-card template-card custom-card">
                <div className="template-card-top">
                  <div className="icon-tile large">✦</div>
                  <div className="template-title-block">
                    <h2>{template.title}</h2>
                    <p>{template.description}</p>
                  </div>
                  <span className="coming-badge">YOUR TEMPLATE</span>
                </div>
                <div className="role-pills">
                  {!roles ? (
                    <span className="role-pill">Loading roles…</span>
                  ) : roles.length === 0 ? (
                    <span className="role-pill">No roles yet</span>
                  ) : (
                    roles.map((role, roleIndex) => (
                      <span key={role.id} className={`role-pill role-${roleIndex % 4}`}>♙ {role.name}</span>
                    ))
                  )}
                </div>
                <div className="template-divider" />
                <ul className="feature-list">
                  <li>✓ Uses your custom role configuration</li>
                  <li>✓ Private to your account</li>
                  <li>{launchReady ? "✓ Ready to launch" : "• Add a host role before launching"}</li>
                </ul>
                <button
                  className="primary-button wide"
                  disabled={creating || !launchReady}
                  onClick={() => createAndJoinRoom(template.id, template.title, true)}
                >
                  {creatingTemplateId === template.id ? "Creating room..." : "Use template  →"}
                </button>
                <button
                  className="secondary-button wide"
                  style={{ marginTop: 8 }}
                  disabled={creating}
                  onClick={() => navigate(`/templates/custom/${template.id}`)}
                >
                  Edit template
                </button>
              </article>
            );
          })}

          <article className="surface-card template-card custom-card">
            <div className="template-card-top">
              <div className="icon-tile large">✦</div>
              <div className="template-title-block">
                <h2>Build a Custom Template</h2>
                <p>Create another reusable template with the roles your session needs.</p>
              </div>
            </div>
            <div className="template-divider" />
            <ul className="feature-list">
              <li>✓ Fully customizable roles</li>
              <li>✓ Define your own host and guest roles</li>
              <li>✓ Private to your account</li>
            </ul>
            <button className="secondary-button wide" disabled={creating} onClick={() => navigate("/templates/custom/new")}>
              ✦ Build a custom template
            </button>
          </article>
        </div>
        {customTemplatesLoaded && customTemplates.length === 0 && !error && (
          <p className="empty-state-note centered">You haven't created a custom template yet.</p>
        )}
        {error && <div className="error-banner">{error}</div>}
      </section>
    </AppShell>
  );
}
