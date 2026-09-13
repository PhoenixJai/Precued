import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { api } from "../lib/api";
import { roomTemplateTitle } from "../lib/customTemplateLaunch";
import { initials } from "../lib/initials";
import { buildInviteJoinUrl, inviteStatusLabel, roleCapacityLabel } from "../lib/inviteReadiness";
import { buildRoleSetupRows } from "../lib/roleSetup";
import { getParticipant } from "../lib/session";
import type { Room, RoomInvite, RoomParticipantWithGrants, RoomRole } from "../types/precued";
import "../inviteSetup.css";

export default function RoomSetupPage() {
  const { roomId = "" } = useParams();
  const navigate = useNavigate();
  const me = getParticipant();
  const [room, setRoom] = useState<Room | null>(null);
  const [roles, setRoles] = useState<RoomRole[]>([]);
  const [participants, setParticipants] = useState<RoomParticipantWithGrants[]>([]);
  const [invites, setInvites] = useState<RoomInvite[]>([]);
  const [namedEmails, setNamedEmails] = useState<Record<string, string>>({});
  const [poolUses, setPoolUses] = useState<Record<string, string>>({});
  const [creatingForRole, setCreatingForRole] = useState<string | null>(null);
  const [copiedInviteId, setCopiedInviteId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!roomId || !me || !me.isHost || me.roomId !== roomId) {
      navigate("/");
      return;
    }

    let cancelled = false;
    async function refresh() {
      try {
        const [nextRoom, nextRoles, nextParticipants, nextInvites] = await Promise.all([
          api.getRoom(roomId),
          api.getRoomRoles(roomId),
          api.getRoomParticipants(roomId),
          api.listRoomInvites(roomId),
        ]);
        if (!cancelled) {
          setRoom(nextRoom);
          setRoles(nextRoles);
          setParticipants(nextParticipants);
          setInvites(nextInvites);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Unable to load room setup");
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

  async function copyInvite(invite: RoomInvite) {
    const url = buildInviteJoinUrl(window.location.origin, invite.token);
    await navigator.clipboard.writeText(url);
    setCopiedInviteId(invite.id);
    window.setTimeout(() => setCopiedInviteId(null), 1400);
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
    if (role.maxMembers === null && (!Number.isInteger(requestedUses) || requestedUses <= 0)) {
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
      <section className="setup-page page-center-wide">
        <button className="text-button back-button" onClick={() => navigate("/templates")}>← Back to templates</button>
        <div className="page-heading setup-heading">
          <h1>Set up your {templateTitle}</h1>
          <p>Create and track invitation links before starting the session.</p>
        </div>

        <div className="setup-layout">
          <div className="surface-card setup-main-card invite-tracker-card">
            <div className="card-heading-row setup-card-heading">
              <div className="icon-tile">♙</div>
              <div>
                <h2>Participants & invitations</h2>
                <p>Named invitations are tracked by email. Precued creates the link here; it does not email it automatically yet.</p>
              </div>
            </div>

            <div className="invite-role-list">
              {rows.map((row) => {
                const roleInvites = invitesByRole[row.role.id] ?? [];
                const pendingUses = roleInvites
                  .filter((invite) => invite.status === "PENDING")
                  .reduce((sum, invite) => sum + invite.remainingUses, 0);
                const availableSeats = row.role.maxMembers === null
                  ? null
                  : Math.max(0, row.role.maxMembers - row.joinedParticipants.length - pendingUses);

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
                    onNamedEmailChange={(value) => setNamedEmails((current) => ({ ...current, [row.role.id]: value }))}
                    onPoolUsesChange={(value) => setPoolUses((current) => ({ ...current, [row.role.id]: value }))}
                    onCreateNamed={() => { void createNamedInvite(row.role); }}
                    onCreatePool={() => { void createPoolInvite(row.role); }}
                    onCopy={(invite) => { void copyInvite(invite); }}
                  />
                );
              })}
            </div>
          </div>

          <aside className="surface-card readiness-card">
            <div className="card-heading-row">
              <div className="icon-tile">✓</div>
              <div><h2>Room readiness</h2><p>Everyone can join in any order — start whenever you're ready.</p></div>
            </div>
            <div className="readiness-list">
              <div className="readiness-item">
                <span className="readiness-dot done">✓</span>
                <div><strong>Host ready</strong><small>{me?.displayName ?? "You"} — {rows.find((row) => row.role.isHostRole)?.role.name ?? "Host"}</small></div>
              </div>
              {rows.filter((row) => !row.role.isHostRole).map((row) => (
                <div className="readiness-item" key={row.role.id}>
                  <span className={`readiness-dot ${row.joinedParticipants.length > 0 ? "done" : ""}`}>
                    {row.joinedParticipants.length > 0 ? "✓" : ""}
                  </span>
                  <div>
                    <strong>{row.role.name}</strong>
                    <small>{roleCapacityLabel(row.joinedParticipants.length, row.role.maxMembers)}</small>
                    {row.joinedParticipants.length > 0 && (
                      <small>{row.joinedParticipants.map((participant) => participant.displayName).join(", ")}</small>
                    )}
                  </div>
                </div>
              ))}
            </div>
            <button className="primary-button wide" onClick={() => navigate(`/rooms/${roomId}/call`)}>▣ Start Call</button>
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
  onNamedEmailChange: (value: string) => void;
  onPoolUsesChange: (value: string) => void;
  onCreateNamed: () => void;
  onCreatePool: () => void;
  onCopy: (invite: RoomInvite) => void;
}) {
  const joinedNames = props.joinedParticipants.map((participant) => participant.displayName).join(", ");
  const noBoundedSeats = props.availableSeats !== null && props.availableSeats <= 0;

  return (
    <article className="invite-role-panel">
      <div className="invite-role-heading">
        <div className="role-summary">
          <div className="round-role-icon">♙</div>
          <div>
            <h3>{props.role.name} {props.isMe && <span className="host-badge">Host</span>}</h3>
            <small>{props.isMe ? "Room host" : roleCapacityLabel(props.joinedParticipants.length, props.role.maxMembers)}</small>
          </div>
        </div>
        <span className={`status-pill ${props.isMe || props.joinedParticipants.length > 0 ? "ready" : "waiting"}`}>
          ● {props.isMe ? "Ready (Host)" : roleCapacityLabel(props.joinedParticipants.length, props.role.maxMembers)}
        </span>
      </div>

      {props.isMe ? (
        <div className="joined-person invite-host-person">
          <span className="avatar-placeholder">{initials(props.meDisplayName ?? props.role.name)}</span>
          <div><strong>{props.meDisplayName ?? "You"}</strong><small>{props.role.name}</small></div>
        </div>
      ) : (
        <>
          {props.joinedParticipants.length > 0 && (
            <div className="invite-joined-summary">
              <strong>Joined</strong>
              <span>{joinedNames}</span>
            </div>
          )}

          {props.invites.length > 0 && (
            <div className="invite-record-list">
              {props.invites.map((invite) => (
                <div className="invite-record" key={invite.id}>
                  <div className="invite-record-main">
                    <strong>{invite.mode === "NAMED" ? invite.inviteeEmail : "Shared role link"}</strong>
                    <small>
                      {inviteStatusLabel(invite.status)}
                      {invite.mode === "POOL" ? ` · ${invite.usesCount}/${invite.maxUses} uses` : ""}
                    </small>
                  </div>
                  {invite.status === "PENDING" && (
                    <button className="secondary-button compact" onClick={() => props.onCopy(invite)}>
                      {props.copiedInviteId === invite.id ? "Copied" : "Copy link"}
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}

          <div className="invite-create-grid">
            <div className="invite-create-block">
              <strong>Invite a specific person</strong>
              <small>Email is used as a tracking label; copy the generated link to send it.</small>
              <div className="invite-create-row">
                <input
                  type="email"
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

            <div className="invite-create-block">
              <strong>Create a shared role link</strong>
              <small>
                {props.role.maxMembers === null
                  ? "Choose how many people may use this link."
                  : `Uses the remaining unreserved seats${props.availableSeats !== null ? ` (${props.availableSeats})` : ""}.`}
              </small>
              <div className="invite-create-row">
                {props.role.maxMembers === null && (
                  <input
                    className="invite-uses-input"
                    type="number"
                    min={1}
                    step={1}
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
        </>
      )}
    </article>
  );
}
