import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { api } from "../lib/api";
import { getParticipant } from "../lib/session";
import type { RoomParticipantWithGrants, RoomRole } from "../types/precued";

export default function RoomSetupPage() {
  const { roomId = "" } = useParams();
  const navigate = useNavigate();
  const me = getParticipant();
  const [roles, setRoles] = useState<RoomRole[]>([]);
  const [participants, setParticipants] = useState<RoomParticipantWithGrants[]>([]);
  const [skipClient, setSkipClient] = useState(false);
  const [copiedRoleId, setCopiedRoleId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!roomId || !me || !me.isHost || me.roomId !== roomId) {
      navigate("/");
      return;
    }

    let cancelled = false;
    async function refresh() {
      try {
        const [nextRoles, nextParticipants] = await Promise.all([
          api.getRoomRoles(roomId),
          api.getRoomParticipants(roomId),
        ]);
        if (!cancelled) {
          setRoles(nextRoles);
          setParticipants(nextParticipants);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Unable to load room setup");
      }
    }

    refresh();
    const timer = window.setInterval(refresh, 2000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [roomId, me?.id, navigate]);

  const salesRepRole = roles.find((role) => role.roleKey === "sales_rep");
  const engineerRole = roles.find((role) => role.roleKey === "sales_engineer");
  const clientRole = roles.find((role) => role.roleKey === "client");

  const participantForRole = (roleId?: string) => participants.find((participant) => participant.activeRoomRoleId === roleId && !participant.leftAt);
  const engineerParticipant = participantForRole(engineerRole?.id);
  const clientParticipant = participantForRole(clientRole?.id);
  const canStart = Boolean(clientParticipant) || skipClient;

  const inviteUrl = (roleId?: string) => roleId ? `${window.location.origin}/join/${roomId}/${roleId}` : "";

  async function copyInvite(roleId?: string) {
    if (!roleId) return;
    await navigator.clipboard.writeText(inviteUrl(roleId));
    setCopiedRoleId(roleId);
    window.setTimeout(() => setCopiedRoleId(null), 1400);
  }

  const readiness = useMemo(() => [
    { done: true, title: "Sales Call template selected", detail: "Sales Call Session" },
    { done: true, title: "Host ready", detail: `${me?.displayName ?? "Sales Rep"} — Sales Rep` },
    { done: Boolean(engineerRole && clientRole), title: "Invite links generated", detail: "Sales Engineer and Client links are ready." },
    { done: Boolean(clientParticipant), title: clientParticipant ? "Client joined" : "Client not yet joined", detail: clientParticipant ? `${clientParticipant.displayName} is ready.` : "Waiting for your client to join the session." },
  ], [clientParticipant, engineerRole, clientRole, me?.displayName]);

  return (
    <AppShell showTaglines={false}>
      <section className="setup-page page-center-wide">
        <button className="text-button back-button" onClick={() => navigate("/templates")}>← Back to templates</button>
        <div className="page-heading setup-heading">
          <h1>Set up your Sales Call</h1>
          <p>Assign roles and send invite links before starting the session.</p>
        </div>

        <div className="setup-layout">
          <div className="surface-card setup-main-card">
            <div className="card-heading-row setup-card-heading">
              <div className="icon-tile">♙</div>
              <div>
                <h2>Participants & invite links</h2>
                <p>This session includes a Sales Rep, Sales Engineer, and Client.</p>
              </div>
            </div>

            <RoleSetupRow
              title="Sales Rep"
              description="Hosts the call and leads the conversation."
              status={<span className="status-pill ready">● Ready (Host)</span>}
              personName={me?.displayName ?? "Sales Rep"}
              personRole="Sales Rep"
              host
            />
            <RoleSetupRow
              title="Sales Engineer"
              description="Joins to provide technical expertise."
              status={<span className={`status-pill ${engineerParticipant ? "joined" : "waiting"}`}>● {engineerParticipant ? "Joined" : "Not joined"}</span>}
              invite={inviteUrl(engineerRole?.id)}
              onCopy={() => copyInvite(engineerRole?.id)}
              copied={copiedRoleId === engineerRole?.id}
              personName={engineerParticipant?.displayName}
              personRole="Sales Engineer"
            />
            <RoleSetupRow
              title="Client"
              description="Joins as the customer or prospect."
              status={<span className={`status-pill ${clientParticipant ? "joined" : "waiting"}`}>● {clientParticipant ? "Joined" : "Not joined"}</span>}
              invite={inviteUrl(clientRole?.id)}
              onCopy={() => copyInvite(clientRole?.id)}
              copied={copiedRoleId === clientRole?.id}
              personName={clientParticipant?.displayName}
              personRole="Client"
            />
          </div>

          <aside className="surface-card readiness-card">
            <div className="card-heading-row">
              <div className="icon-tile">✓</div>
              <div><h2>Room readiness</h2><p>Make sure everything is set before starting.</p></div>
            </div>
            <div className="readiness-list">
              {readiness.map((item) => (
                <div className="readiness-item" key={item.title}>
                  <span className={`readiness-dot ${item.done ? "done" : ""}`}>{item.done ? "✓" : ""}</span>
                  <div><strong>{item.title}</strong><small>{item.detail}</small></div>
                </div>
              ))}
            </div>
            <button className="primary-button wide" disabled={!canStart} onClick={() => navigate(`/rooms/${roomId}/call`)}>▣ Start Call</button>
            {!canStart && <p className="readiness-note">Start Call is disabled until the Client joins, or you choose to skip.</p>}
            <div className="readiness-divider" />
            <label className="checkbox-row">
              <input type="checkbox" checked={skipClient} onChange={(event) => setSkipClient(event.target.checked)} />
              <span><strong>Skip Client for now</strong><small>Allow start without Client. You can invite them later during the call.</small></span>
            </label>
          </aside>
        </div>
        <p className="demo-contract-note">Demo join links use the temporary direct room-role route because formal Invite token consumption is intentionally deferred.</p>
        {error && <div className="error-banner">{error}</div>}
      </section>
    </AppShell>
  );
}

function RoleSetupRow(props: {
  title: string;
  description: string;
  status: React.ReactNode;
  invite?: string;
  onCopy?: () => void;
  copied?: boolean;
  personName?: string;
  personRole: string;
  host?: boolean;
}) {
  return (
    <div className="role-setup-row">
      <div className="role-summary">
        <div className="round-role-icon">♙</div>
        <div><h3>{props.title} {props.host && <span className="host-badge">Host</span>}</h3><p>{props.description}</p></div>
      </div>
      {props.personName ? (
        <div className="joined-person"><span className="avatar-placeholder">{initials(props.personName)}</span><div><strong>{props.personName}</strong><small>{props.personRole}</small></div></div>
      ) : props.invite ? (
        <div className="invite-controls">
          <div className="invite-url">🔗 {props.invite}</div>
          <button className="primary-button compact" onClick={props.onCopy}>{props.copied ? "Copied" : "Copy link"}</button>
        </div>
      ) : null}
      <div className="row-status">{props.status}</div>
    </div>
  );
}

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("");
}
