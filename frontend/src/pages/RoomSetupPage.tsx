import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { api } from "../lib/api";
import { initials } from "../lib/initials";
import { buildRoleSetupRows } from "../lib/roleSetup";
import { getParticipant } from "../lib/session";
import { templateName } from "../lib/templates";
import type { Room, RoomParticipantWithGrants, RoomRole } from "../types/precued";

export default function RoomSetupPage() {
  const { roomId = "" } = useParams();
  const navigate = useNavigate();
  const me = getParticipant();
  const [room, setRoom] = useState<Room | null>(null);
  const [roles, setRoles] = useState<RoomRole[]>([]);
  const [participants, setParticipants] = useState<RoomParticipantWithGrants[]>([]);
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
        const [nextRoom, nextRoles, nextParticipants] = await Promise.all([
          api.getRoom(roomId),
          api.getRoomRoles(roomId),
          api.getRoomParticipants(roomId),
        ]);
        if (!cancelled) {
          setRoom(nextRoom);
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

  const rows = useMemo(
    () => buildRoleSetupRows(roles, participants, roomId, window.location.origin),
    [roles, participants, roomId],
  );

  async function copyInvite(url: string, roleId: string) {
    await navigator.clipboard.writeText(url);
    setCopiedRoleId(roleId);
    window.setTimeout(() => setCopiedRoleId(null), 1400);
  }

  const templateTitle = room ? templateName(room.templateId) : "session";

  return (
    <AppShell showTaglines={false}>
      <section className="setup-page page-center-wide">
        <button className="text-button back-button" onClick={() => navigate("/templates")}>← Back to templates</button>
        <div className="page-heading setup-heading">
          <h1>Set up your {templateTitle}</h1>
          <p>Assign roles and send invite links before starting the session.</p>
        </div>

        <div className="setup-layout">
          <div className="surface-card setup-main-card">
            <div className="card-heading-row setup-card-heading">
              <div className="icon-tile">♙</div>
              <div>
                <h2>Participants & invite links</h2>
                <p>This session includes {rows.map((row) => row.role.name).join(", ")}.</p>
              </div>
            </div>

            {rows.map((row) => (
              <RoleSetupRow
                key={row.role.id}
                role={row.role}
                isMe={row.role.isHostRole}
                meDisplayName={me?.displayName}
                inviteUrl={row.inviteUrl}
                joinedParticipants={row.joinedParticipants}
                copied={copiedRoleId === row.role.id}
                onCopy={() => row.inviteUrl && copyInvite(row.inviteUrl, row.role.id)}
              />
            ))}
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
                    <small>
                      {row.joinedParticipants.length > 0
                        ? row.joinedParticipants.map((p) => p.displayName).join(", ")
                        : "Not yet joined"}
                    </small>
                  </div>
                </div>
              ))}
            </div>
            <button className="primary-button wide" onClick={() => navigate(`/rooms/${roomId}/call`)}>▣ Start Call</button>
          </aside>
        </div>
        <p className="demo-contract-note">Demo join links use the temporary direct room-role route because formal Invite token consumption is intentionally deferred.</p>
        {error && <div className="error-banner">{error}</div>}
      </section>
    </AppShell>
  );
}

function RoleSetupRow(props: {
  role: RoomRole;
  isMe: boolean;
  meDisplayName?: string;
  inviteUrl: string | null;
  joinedParticipants: RoomParticipantWithGrants[];
  copied: boolean;
  onCopy: () => void;
}) {
  const joinedNames = props.joinedParticipants.map((p) => p.displayName).join(", ");
  return (
    <div className="role-setup-row">
      <div className="role-summary">
        <div className="round-role-icon">♙</div>
        <div><h3>{props.role.name} {props.isMe && <span className="host-badge">Host</span>}</h3></div>
      </div>
      {props.isMe ? (
        <div className="joined-person"><span className="avatar-placeholder">{initials(props.meDisplayName ?? props.role.name)}</span><div><strong>{props.meDisplayName ?? "You"}</strong><small>{props.role.name}</small></div></div>
      ) : props.joinedParticipants.length > 0 ? (
        <div className="joined-person"><span className="avatar-placeholder">{initials(joinedNames)}</span><div><strong>{joinedNames}</strong><small>{props.role.name}</small></div></div>
      ) : props.inviteUrl ? (
        <div className="invite-controls">
          <div className="invite-url">🔗 {props.inviteUrl}</div>
          <button className="primary-button compact" onClick={props.onCopy}>{props.copied ? "Copied" : "Copy link"}</button>
        </div>
      ) : null}
      <div className="row-status">
        {props.isMe ? (
          <span className="status-pill ready">● Ready (Host)</span>
        ) : (
          <span className={`status-pill ${props.joinedParticipants.length > 0 ? "joined" : "waiting"}`}>
            ● {props.joinedParticipants.length > 0 ? "Joined" : "Not joined"}
          </span>
        )}
      </div>
    </div>
  );
}
