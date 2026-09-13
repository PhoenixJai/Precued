import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { api } from "../lib/api";
import { findHostRole } from "../lib/roleSetup";
import { getAuthSession, saveParticipant } from "../lib/session";
import { templateName } from "../lib/templates";
import type { TemplateId } from "../types/precued";

const templateCards = [
  {
    id: "sales_call",
    title: "Sales Call",
    description: "Control what internal and external participants can see in real time.",
    roles: ["Sales Rep", "Sales Engineer", "Client"],
    bullets: ["Role-based content visibility", "Built-in collaboration tools", "Optimized for client conversations"],
    functional: true,
    badge: "POPULAR",
  },
  {
    id: "mock_trial",
    title: "Mock Trial",
    description: "Structure a realistic trial experience with controlled visibility for each role.",
    roles: ["Judge", "Jury", "Defense", "Prosecution"],
    bullets: ["Role-specific information access", "Support for exhibits and evidence", "Designed for legal teams and education"],
    functional: true,
    badge: undefined,
  },
  {
    id: "ld_debate",
    title: "Lincoln-Douglas Debate",
    description: "Facilitate structured academic debates with role-based visibility.",
    roles: ["Judge", "Affirmative", "Negative", "Audience"],
    bullets: ["Separate materials for each side", "Timed rounds and structure", "Ideal for education and competitions"],
    functional: true,
    badge: undefined,
  },
] as const;

export default function TemplatePickerPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function createAndJoinRoom(templateId: TemplateId) {
    const auth = getAuthSession();
    if (!auth) {
      navigate("/");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const room = await api.createRoom(templateId, auth.sessionToken);
      const roles = await api.getRoomRoles(room.id);
      const hostRole = findHostRole(roles);
      if (!hostRole) throw new Error(`No host role was created for this ${templateName(templateId)} room.`);

      const displayName = auth.email.split("@")[0] || "Host";
      const participant = await api.joinRoom(room.id, displayName, auth.userId, auth.sessionToken);
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
      setLoading(false);
    }
  }

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
                className={template.functional ? "primary-button wide" : "secondary-button wide"}
                onClick={template.functional ? () => createAndJoinRoom(template.id) : undefined}
                disabled={!template.functional || loading}
              >
                {template.functional ? (loading ? "Creating room..." : "Use template  →") : "Coming soon"}
              </button>
            </article>
          ))}

          <article className="surface-card template-card custom-card">
            <div className="template-card-top">
              <div className="icon-tile large">✦</div>
              <div className="template-title-block">
                <h2>Custom Template</h2>
                <p>Build your own template with the roles your session needs.</p>
              </div>
            </div>
            <div className="template-divider" />
            <ul className="feature-list">
              <li>✓ Fully customizable roles</li>
              <li>✓ Define your own host and guest roles</li>
              <li>✓ Private to your account</li>
            </ul>
            <button className="secondary-button wide" onClick={() => navigate("/templates/custom/new")}>
              ✦ Build a custom template
            </button>
          </article>
        </div>
        {error && <div className="error-banner">{error}</div>}
      </section>
    </AppShell>
  );
}
