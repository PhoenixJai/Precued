import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { api } from "../lib/api";
import { roomTemplateTitle } from "../lib/customTemplateLaunch";
import { initials } from "../lib/initials";
import { buildInviteJoinUrl, inviteStatusLabel } from "../lib/inviteReadiness";
import { buildRoleSetupRows } from "../lib/roleSetup";
import {
  formatSessionStageDuration,
  sessionRoleState,
  sessionRoleStatusLabel,
} from "../lib/sessionSetupUi";
import { getParticipant } from "../lib/session";
import type {
  Room,
  RoomInvite,
  RoomParticipantWithGrants,
  RoomRole,
  SessionFlow,
} from "../types/precued";
import "../inviteSetup.css";

export default function RoomSetupPage() {
  const { roomId = "" } = useParams();
  const navigate = useNavigate();
  const me = getParticipant();
  const [room, setRoom] = useState<Room | null>(null);
  const [roles, setRoles] = useState<RoomRole[]>([]);
  const [participants, setParticipants] = useState<RoomParticipantWithGrants[]>([]);
  const [invites, setInvites] = useState<RoomInvite[]>([]);
  const [sessionFlow, setSessionFlow] = useState<SessionFlow | null>(null);
  const [namedEmails, setNamedEmails] = useState<Record<string, string>>({});
  const [poolUses, setPoolUses] = useState<Record<string, string>>({});
  const [creatingForRole, setCreatingForRole] = useState<string | null>(null);
  const [copiedInviteId, setCopiedInviteId] = useState<string | null>(null);
  const [expiringInviteId, setExpiringInviteId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!roomId || !me || !me.isHost || me.roomId !== roomId) {
      navigate("/");
      return;
    }

    let cancelled = false;
    async function refresh() {
      try {
        const [nextRoom, nextRoles, nextParticipants, nextInvites, nextSessionFlow] = await Promise.all([
          api.getRoom(roomId),
          api.getRoomRoles(roomId),
          api.getRoomParticipants(roomId),
          api.listRoomInvites(roomId),
          api.getSessionFlow(roomId),
        ]);
        if (!cancelled) {
          setRoom(nextRoom);
          setRoles(nextRoles);
          setParticipants(nextParticipants);
          setInvites(nextInvites);
          setSessionFlow(nextSessionFlow);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Unable to load session setup");
      }
    }

    void refresh();
    const timer = window.setInterval(() => { void refresh(); }, 2000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [roomId, me?.id, navigate]);

  const rows = useMemo(
    () => buildRoleSetupRows(roles, participants),
    [roles, participants],
  );

  const invitesByRole = useMemo(() => {
    const grouped: Record<string, RoomInvite[]> = {};
    for (const invite of invites) {
      (grouped[invite.roomRoleId] ??= []).push(invite);
    }
    return grouped;
  }, [invites]);

  const orderedStages = useMemo(
    () => sessionFlow?.stages.slice().sort((a, b) => a.sortOrder - b.sortOrder) ?? [],
    [sessionFlow],
  );

  async function copyInvite(invite: RoomInvite) {
    const url = buildInviteJoinUrl(window.location.origin, invite.token);
    await navigator.clipboard.writeText(url);
    setCopiedInviteId(invite.id);
    window.setTimeout(() => setCopiedInviteId(null), 1400);
  }

  async function expireInvite(invite: RoomInvite) {
    setExpiringInviteId(invite.id);
    setError(null);
    try {
      const expired = await api.expireRoomInvite(roomId, invite.id);
      setInvites((current) => current.map((item) => item.id === expired.id ? expired : item));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to cancel invite");
    } finally {
      setExpiringInviteId(null);
    }
  }

  async function createNamedInvite(role: RoomRole) {
    const email = (namedEmails[role.id] ?? "").trim();
    if (!email) return;
    setCreatingForRole(role.id);
    setError(null);
    try {
      const created = await api.createRoomInvite(roomId, {
        roomRoleId: role.id,
        mode: "NAMED",
        inviteeEmail: email,
      });
      setInvites((current) => [...current, created]);
      setNamedEmails((current) => ({ ...current, [role.id]: "" }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to create invite");
    } finally {
      setCreatingForRole(null);
    }
  }

  async function createPoolInvite(role: RoomRole) {
    const requestedUses = role.maxMembers === null
      ? Number(poolUses[role.id] ?? "")
      : undefined;
    if (role.maxMembers === null && (typeof requestedUses !== "number" || !Number.isInteger(requestedUses) || requestedUses <= 0)) {
      setError(`Enter how many times the ${role.name} shared link may be used.`);
      return;
    }

    setCreatingForRole(role.id);
    setError(null);
    try {
      const created = await api.createRoomInvite(roomId, {
        roomRoleId: role.id,
        mode: "POOL",
        maxUses: requestedUses,
      });
      setInvites((current) => [...current, created]);
      setPoolUses((current) => ({ ...current, [role.id]: "" }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to create shared invite");
    } finally {
      setCreatingForRole(null);
    }
  }

  const templateTitle = room ? roomTemplateTitle(room.templateId, room.templateName) : "session";

  return (
    <AppShell showTaglines={false}>
      <section className="setup-page session-setup-page page-center-wide">
        <button className="text-button back-button" onClick={() => navigate("/templates")}>← Back to templates</button>

        <header className="session-setup-heading">
          <span className="eyebrow">SESSION SETUP</span>
          <h1>Prepare your {templateTitle} session</h1>
          <p>Invite participants by role, track who has joined, and start whenever the session is ready.</p>
        </header>

        <div className="session-setup-layout">
          <main className="surface-card session-roster-card">
            <div className="session-card-heading">
              <div>
                <span className="session-section-kicker">Participants</span>
                <h2>Role roster & invitations</h2>
                <p>Precued creates invitation links here. Named invitations use email as a tracking label and are not emailed automatically.</p>
              </div>
              <span className="session-role-count">{rows.length} role{rows.length === 1 ? "" : "s"}</span>
            </div>

            <div className="session-role-list">
              {rows.map((row) => {
                const roleInvites = invitesByRole[row.role.id] ?? [];
                const pendingUses = roleInvites
                  .filter((invite) => invite.status === "PENDING")
                  .reduce((sum, invite) => sum + invite.remainingUses, 0);
                const availableSeats = row.role.maxMembers === null
                  ? null
                  : Math.max(0, row.role.maxMembers - row.assignedParticipants.length - pendingUses);

                return (
                  <RoleInviteTracker
                    key={row.role.id}
                    role={row.role}
                    isMe={row.role.isHostRole}
                    meDisplayName={me?.displayName}
                    joinedParticipants={row.joinedParticipants}
                    invites={roleInvites}
                    availableSeats={availableSeats}
                    namedEmail={namedEmails[row.role.id] ?? ""}
                    poolUses={poolUses[row.role.id] ?? ""}
                    busy={creatingForRole === row.role.id}
                    copiedInviteId={copiedInviteId}
                    expiringInviteId={expiringInviteId}
                    onNamedEmailChange={(value) => setNamedEmails((current) => ({ ...current, [row.role.id]: value }))}
                    onPoolUsesChange={(value) => setPoolUses((current) => ({ ...current, [row.role.id]: value }))}
                    onCreateNamed={() => { void createNamedInvite(row.role); }}
                    onCreatePool={() => { void createPoolInvite(row.role); }}
                    onCopy={(invite) => { void copyInvite(invite); }}
                    onExpire={(invite) => { void expireInvite(invite); }}
                  />
                );
              })}
            </div>
          </main>

          <aside className="surface-card session-readiness-card">
            <div className="session-card-heading compact-heading">
              <div>
                <span className="session-section-kicker">Readiness</span>
                <h2>Session readiness</h2>
                <p>Participants can join in any order. You decide when to begin.</p>
              </div>
            </div>

            <div className="session-readiness-list">
              {rows.map((row) => {
                const isHost = row.role.isHostRole;
                const state = sessionRoleState(isHost, row.joinedParticipants.length);
                const participantNames = isHost
                  ? (me?.displayName ?? "You")
                  : row.joinedParticipants.map((participant) => participant.displayName).join(", ");
                return (
                  <div className="session-readiness-item" key={row.role.id}>
                    <span className={`session-readiness-dot ${state}`} aria-hidden="true">
                      {state === "ready" ? "✓" : ""}
                    </span>
                    <div>
                      <strong>{row.role.name}</strong>
                      <small>{sessionRoleStatusLabel(isHost, row.joinedParticipants.length, row.role.maxMembers)}</small>
                      {participantNames && <small className="session-readiness-names">{participantNames}</small>}
                    </div>
                  </div>
                );
              })}
            </div>

            <button className="primary-button wide session-start-button" onClick={() => navigate(`/rooms/${roomId}/call`)}>
              Start Session →
            </button>

            <div className="session-structure-preview">
              <div className="session-structure-preview-heading">
                <div>
                  <span className="session-section-kicker">Structure</span>
                  <h3>Session flow</h3>
                </div>
                {sessionFlow?.enabled && orderedStages.length > 0 && (
                  <span>{orderedStages.length} stage{orderedStages.length === 1 ? "" : "s"}</span>
                )}
              </div>

              {!sessionFlow ? (
                <p className="session-structure-empty">Loading session structure…</p>
              ) : !sessionFlow.enabled ? (
                <p className="session-structure-empty">This session does not use a guided Session Structure.</p>
              ) : orderedStages.length === 0 ? (
                <p className="session-structure-empty">Session Structure is enabled, but no stages are configured.</p>
              ) : (
                <ol className="session-structure-stage-list">
                  {orderedStages.slice(0, 5).map((stage, index) => (
                    <li key={stage.id}>
                      <span className="session-stage-index">{index + 1}</span>
                      <div>
                        <strong>{stage.name}</strong>
                        <small>{formatSessionStageDuration(stage.durationSeconds)}</small>
                      </div>
                    </li>
                  ))}
                </ol>
              )}
              {orderedStages.length > 5 && (
                <p className="session-structure-more">+ {orderedStages.length - 5} more stage{orderedStages.length - 5 === 1 ? "" : "s"}</p>
              )}
            </div>
          </aside>
        </div>

        {error && <div className="error-banner">{error}</div>}
      </section>
    </AppShell>
  );
}

function RoleInviteTracker(props: {
  role: RoomRole;
  isMe: boolean;
  meDisplayName?: string;
  joinedParticipants: RoomParticipantWithGrants[];
  invites: RoomInvite[];
  availableSeats: number | null;
  namedEmail: string;
  poolUses: string;
  busy: boolean;
  copiedInviteId: string | null;
  expiringInviteId: string | null;
  onNamedEmailChange: (value: string) => void;
  onPoolUsesChange: (value: string) => void;
  onCreateNamed: () => void;
  onCreatePool: () => void;
  onCopy: (invite: RoomInvite) => void;
  onExpire: (invite: RoomInvite) => void;
}) {
  const noBoundedSeats = props.availableSeats !== null && props.availableSeats <= 0;
  const pendingInviteCount = props.invites.filter((invite) => invite.status === "PENDING").length;
  const state = sessionRoleState(props.isMe, props.joinedParticipants.length);

  return (
    <article className="session-role-panel">
      <div className="session-role-heading">
        <div className="session-role-identity">
          <span className="session-role-icon">{props.role.name.slice(0, 1).toUpperCase()}</span>
          <div>
            <div className="session-role-title-line">
              <h3>{props.role.name}</h3>
              {props.role.isHostRole && <span className="host-badge">Host</span>}
              {props.role.isGuestRole && <span className="session-role-badge">Guest</span>}
            </div>
            <small>{props.isMe ? "Session host" : sessionRoleStatusLabel(false, props.joinedParticipants.length, props.role.maxMembers)}</small>
          </div>
        </div>
        <span className={`status-pill ${state}`}>
          {sessionRoleStatusLabel(props.isMe, props.joinedParticipants.length, props.role.maxMembers)}
        </span>
      </div>

      <div className="session-participant-strip">
        {props.isMe ? (
          <div className="session-person-chip">
            <span className="avatar-placeholder">{initials(props.meDisplayName ?? props.role.name)}</span>
            <div><strong>{props.meDisplayName ?? "You"}</strong><small>Joined · Host</small></div>
          </div>
        ) : props.joinedParticipants.length > 0 ? (
          props.joinedParticipants.map((participant) => (
            <div className="session-person-chip" key={participant.id}>
              <span className="avatar-placeholder">{initials(participant.displayName)}</span>
              <div><strong>{participant.displayName}</strong><small>Joined</small></div>
            </div>
          ))
        ) : (
          <p className="session-role-empty">No one has joined this role yet.</p>
        )}
      </div>

      {!props.isMe && props.invites.length > 0 && (
        <div className="session-invite-record-list">
          {props.invites.map((invite) => (
            <div className="session-invite-record" key={invite.id}>
              <div className="session-invite-record-main">
                <span className="session-invite-type">{invite.mode === "NAMED" ? "Named invite" : "Shared role link"}</span>
                <strong>{invite.mode === "NAMED" ? invite.inviteeEmail : "Reusable invitation link"}</strong>
                <small>
                  {inviteStatusLabel(invite.status)}
                  {invite.mode === "POOL" ? ` · ${invite.usesCount}/${invite.maxUses} uses` : ""}
                </small>
              </div>
              {invite.status === "PENDING" && (
                <div className="session-invite-record-actions">
                  <button className="secondary-button compact" onClick={() => props.onCopy(invite)}>
                    {props.copiedInviteId === invite.id ? "Copied" : "Copy link"}
                  </button>
                  <button
                    className="text-button session-cancel-invite"
                    disabled={props.expiringInviteId === invite.id}
                    onClick={() => props.onExpire(invite)}
                  >
                    {props.expiringInviteId === invite.id ? "Cancelling…" : "Cancel"}
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {!props.isMe && (
        <details className="session-invite-tools">
          <summary>
            <span>Invite people to this role</span>
            <small>{pendingInviteCount > 0 ? `${pendingInviteCount} pending invite${pendingInviteCount === 1 ? "" : "s"}` : "Named invite or shared link"}</small>
          </summary>
          <div className="session-invite-create-grid">
            <div className="session-invite-create-block">
              <strong>Invite a specific person</strong>
              <small>Use an email as the tracking label, then copy the generated link to send it.</small>
              <div className="session-invite-create-row">
                <input
                  type="email"
                  aria-label={`Email for ${props.role.name} invitation`}
                  placeholder="candidate@example.com"
                  value={props.namedEmail}
                  disabled={props.busy || noBoundedSeats}
                  onChange={(event) => props.onNamedEmailChange(event.target.value)}
                />
                <button
                  className="primary-button compact"
                  disabled={props.busy || noBoundedSeats || !props.namedEmail.trim()}
                  onClick={props.onCreateNamed}
                >Create invite</button>
              </div>
            </div>

            <div className="session-invite-create-block">
              <strong>Create a shared role link</strong>
              <small>
                {props.role.maxMembers === null
                  ? "Choose how many people may use this link."
                  : `Uses the remaining unreserved seats${props.availableSeats !== null ? ` (${props.availableSeats})` : ""}.`}
              </small>
              <div className="session-invite-create-row">
                {props.role.maxMembers === null && (
                  <input
                    className="invite-uses-input"
                    type="number"
                    min={1}
                    step={1}
                    aria-label={`Uses for ${props.role.name} shared link`}
                    placeholder="Uses"
                    value={props.poolUses}
                    disabled={props.busy}
                    onChange={(event) => props.onPoolUsesChange(event.target.value)}
                  />
                )}
                <button
                  className="secondary-button compact"
                  disabled={props.busy || noBoundedSeats || (props.role.maxMembers === null && !props.poolUses)}
                  onClick={props.onCreatePool}
                >Create shared link</button>
              </div>
            </div>
          </div>
        </details>
      )}
    </article>
  );
}
