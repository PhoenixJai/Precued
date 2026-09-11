import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  LiveKitRoom,
  RoomAudioRenderer,
  StartAudio,
  VideoTrack,
  useConnectionState,
  useRoomContext,
  useTracks,
} from "@livekit/components-react";
import { ConnectionState, RoomEvent, Track } from "livekit-client";
import type { DataPacket_Kind, RemoteParticipant } from "livekit-client";
import { AppShell, Brand } from "../components/AppShell";
import { api } from "../lib/api";
import {
  clearShareGrantIds,
  forgetGrantId,
  getGrantId,
  getParticipant,
  rememberGrantId,
} from "../lib/session";
import { templateName } from "../lib/templates";
import {
  computeLocalVisibilityGrants,
  isServerVisibilityGrant,
  toTrackSubscriptionPermissions,
} from "../lib/visibilityGrants";
import type {
  ActiveShare,
  LiveKitTokenResponse,
  Room,
  RoomParticipantWithGrants,
  RoomRole,
  TemplatePreset,
  VisibilityGrantMessage,
} from "../types/precued";

const POLL_MS = 1500;

export default function CallPage() {
  const { roomId = "" } = useParams();
  const navigate = useNavigate();
  const me = getParticipant();
  const [credentials, setCredentials] = useState<LiveKitTokenResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!me || me.roomId !== roomId) {
      navigate("/");
      return;
    }
    let cancelled = false;
    api.getLiveKitToken(me.id)
      .then((token) => !cancelled && setCredentials(token))
      .catch((err) => !cancelled && setError(err instanceof Error ? err.message : "Unable to connect to LiveKit"));
    return () => { cancelled = true; };
  }, [roomId, me?.id, navigate]);

  if (error) return <AppShell><div className="page-center-narrow"><div className="error-banner call-error">{error}</div></div></AppShell>;
  if (!credentials || !me) return <ConnectingScreen />;

  return (
    <LiveKitRoom
      token={credentials.token}
      serverUrl={credentials.livekitUrl}
      connect
      audio
      video
      options={{ adaptiveStream: true, dynacast: true }}
    >
      <CallExperience roomId={roomId} />
      <RoomAudioRenderer />
      <StartAudio label="Click to allow audio playback" />
    </LiveKitRoom>
  );
}

function ConnectingScreen() {
  return (
    <AppShell>
      <section className="connecting-page page-center-narrow">
        <div className="surface-card connecting-card">
          <Brand />
          <div className="spinner" aria-label="Connecting" />
          <h1>Connecting to call...</h1>
          <p>Setting up audio, video, and role-based visibility.</p>
          <small>This should only take a moment.</small>
        </div>
      </section>
    </AppShell>
  );
}

function CallExperience({ roomId }: { roomId: string }) {
  const navigate = useNavigate();
  const room = useRoomContext();
  const connectionState = useConnectionState();
  const me = getParticipant()!;
  const [roomInfo, setRoomInfo] = useState<Room | null>(null);
  const [roles, setRoles] = useState<RoomRole[]>([]);
  const [participants, setParticipants] = useState<RoomParticipantWithGrants[]>([]);
  const [presets, setPresets] = useState<TemplatePreset[]>([]);
  const [activeShares, setActiveShares] = useState<ActiveShare[]>([]);
  const [selectedPresetId, setSelectedPresetId] = useState<string | null>(null);
  const [moreOpen, setMoreOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const screenTracks = useTracks([Track.Source.ScreenShare], { onlySubscribed: false });
  const cameraTracks = useTracks([Track.Source.Camera], { onlySubscribed: false });
  const currentShare = activeShares[0] ?? null;

  // Re-asserts subscription permissions from the just-polled REST snapshot
  // (authenticated, unforgeable) rather than from whatever the last data
  // message said. Runs on every poll regardless of whether a push also
  // arrived, so the data-channel push is a latency optimization, never the
  // trust decision — see computeLocalVisibilityGrants's doc comment.
  const applyVisibilityPermissions = useCallback(
    (participantsList: RoomParticipantWithGrants[], share: ActiveShare | null) => {
      const baseTrackSids = [
        room.localParticipant.getTrackPublication(Track.Source.Camera)?.trackSid,
        room.localParticipant.getTrackPublication(Track.Source.Microphone)?.trackSid,
      ].filter((sid): sid is string => Boolean(sid));

      const shareTrack = share
        ? screenTracks.find((trackRef: any) => trackRef.publication?.trackName?.startsWith(`${share.id}:`))
        : undefined;
      const shareTrackSids = shareTrack?.publication?.trackSid ? [shareTrack.publication.trackSid] : [];

      const grants = computeLocalVisibilityGrants(participantsList, share, baseTrackSids, shareTrackSids);
      room.localParticipant.setTrackSubscriptionPermissions(false, toTrackSubscriptionPermissions(grants));
    },
    [room, screenTracks],
  );

  useEffect(() => {
    let cancelled = false;
    api.getRoom(roomId)
      .then((nextRoom) => { if (!cancelled) setRoomInfo(nextRoom); })
      .catch((err) => !cancelled && setError(err instanceof Error ? err.message : "Unable to load room"));
    return () => { cancelled = true; };
  }, [roomId]);

  const refresh = useCallback(async () => {
    if (!roomInfo) return;
    try {
      const [nextRoles, nextParticipants, nextPresets, nextShares] = await Promise.all([
        api.getRoomRoles(roomId),
        api.getRoomParticipants(roomId),
        api.getPresets(roomInfo.templateId),
        api.getActiveShares(roomId),
      ]);
      setRoles(nextRoles);
      setParticipants(nextParticipants);
      setPresets([...nextPresets].sort((a, b) => a.sortOrder - b.sortOrder));
      setActiveShares(nextShares);
      setError(null);

      if (me.isHost) applyVisibilityPermissions(nextParticipants, nextShares[0] ?? null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to refresh call state");
    }
  }, [roomId, roomInfo, me.isHost, applyVisibilityPermissions]);

  useEffect(() => {
    if (!roomInfo) return;
    void refresh();
    const timer = window.setInterval(() => void refresh(), POLL_MS);
    return () => window.clearInterval(timer);
  }, [roomInfo, refresh]);

  const roleIdsForPreset = useCallback((preset: TemplatePreset) => {
    return preset.roleKeys
      .map((key) => roles.find((role) => role.roleKey === key)?.id)
      .filter((id): id is string => Boolean(id));
  }, [roles]);

  const currentShareRoleKey = currentShare ? [...currentShare.roomRoleIds].sort().join(",") : "";
  useEffect(() => {
    if (currentShare && presets.length && roles.length) {
      const currentIds = [...currentShare.roomRoleIds].sort();
      const matching = presets.find((preset) => {
        const target = roleIdsForPreset(preset).sort();
        return target.length === currentIds.length && target.every((id, index) => id === currentIds[index]);
      });
      if (matching) setSelectedPresetId(matching.id);
    } else if (!selectedPresetId && presets.length) {
      setSelectedPresetId(presets[0].id);
    }
  }, [currentShare?.id, currentShareRoleKey, presets, roles, roleIdsForPreset, selectedPresetId]);

  useEffect(() => {
    const handleData = (
      payload: Uint8Array,
      sender?: RemoteParticipant,
      _kind?: DataPacket_Kind,
      topic?: string,
    ) => {
      // Only the backend's server-side push (VisibilityEngineImpl, via
      // RoomServiceClient — never a client token, see LiveKitTokenService)
      // is trusted here. Any connected participant can still publish a
      // message on this same topic (LiveKit doesn't scope topics), so the
      // sender must be verified, not just the topic — see
      // lib/visibilityGrants.ts for why "no sender" is what that check is.
      if (!isServerVisibilityGrant(topic, sender) || !me.isHost) return;
      try {
        const grants = JSON.parse(new TextDecoder().decode(payload)) as VisibilityGrantMessage[];
        room.localParticipant.setTrackSubscriptionPermissions(false, toTrackSubscriptionPermissions(grants));
      } catch (err) {
        setError(err instanceof Error ? err.message : "Invalid visibility grant update");
      }
    };

    room.on(RoomEvent.DataReceived, handleData);
    return () => { room.off(RoomEvent.DataReceived, handleData); };
  }, [room, me.isHost]);

  const roleById = useMemo(() => new Map(roles.map((role) => [role.id, role])), [roles]);
  const ownRole = roleById.get(me.roomRoleId);
  const canSeeCurrentShare = Boolean(currentShare && currentShare.roomRoleIds.includes(me.roomRoleId));
  const visibleRoleNames = currentShare
    ? currentShare.roomRoleIds.map((id) => roleById.get(id)?.name).filter((name): name is string => Boolean(name))
    : [];

  const currentScreenTrack = currentShare
    ? screenTracks.find((trackRef: any) => trackRef.publication?.trackName?.startsWith(`${currentShare.id}:`))
    : undefined;

  async function applyPreset(preset: TemplatePreset) {
    setSelectedPresetId(preset.id);
    if (!currentShare || !me.isHost) return;
    setBusy(true);
    setError(null);
    try {
      const targetRoleIds = roleIdsForPreset(preset);
      const currentRoleIds = currentShare.roomRoleIds;
      const removeIds = currentRoleIds.filter((id) => !targetRoleIds.includes(id));
      const addIds = targetRoleIds.filter((id) => !currentRoleIds.includes(id));

      for (const roomRoleId of removeIds) {
        const grantId = getGrantId(currentShare.id, roomRoleId);
        if (!grantId) {
          throw new Error("This browser no longer has the grant record needed to revoke that role. Restart the demo share before switching this preset.");
        }
        await api.revokeGrant(grantId);
        forgetGrantId(currentShare.id, roomRoleId);
      }

      for (const roomRoleId of addIds) {
        const grant = await api.createGrant(currentShare.id, roomRoleId);
        rememberGrantId(currentShare.id, roomRoleId, grant.id);
      }
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to update visibility");
    } finally {
      setBusy(false);
    }
  }

  async function startScreenShare() {
    if (!me.isHost || currentShare) return;
    const preset = presets.find((item) => item.id === selectedPresetId) ?? presets[0];
    if (!preset) return;
    setBusy(true);
    setError(null);
    setMoreOpen(false);
    let createdShare: ActiveShare | null = null;
    try {
      const share = await api.startShare(roomId, me.id, preset.id, "Screen share");
      createdShare = { id: share.id, label: share.label, roomRoleIds: [] };
      for (const roomRoleId of roleIdsForPreset(preset)) {
        const grant = await api.createGrant(share.id, roomRoleId);
        rememberGrantId(share.id, roomRoleId, grant.id);
      }

      await room.localParticipant.setScreenShareEnabled(
        true,
        { audio: false },
        { name: `${share.id}:${share.label}` },
      );
      await refresh();
    } catch (err) {
      if (createdShare) {
        try { await api.endShare(createdShare.id); } catch { /* Preserve the original failure. */ }
        clearShareGrantIds(createdShare.id);
      }
      setError(err instanceof Error ? err.message : "Unable to share screen");
    } finally {
      setBusy(false);
    }
  }

  async function stopScreenShare() {
    if (!currentShare || !me.isHost) return;
    setBusy(true);
    setMoreOpen(false);
    try {
      await room.localParticipant.setScreenShareEnabled(false);
      await api.endShare(currentShare.id);
      clearShareGrantIds(currentShare.id);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to stop sharing");
    } finally {
      setBusy(false);
    }
  }

  async function endCall() {
    setBusy(true);
    if (currentShare && me.isHost) {
      try { await api.endShare(currentShare.id); } catch { /* Disconnect anyway. */ }
    }
    await room.disconnect();
    navigate(me.isHost ? "/templates" : "/");
  }

  if (connectionState !== ConnectionState.Connected) return <ConnectingScreen />;

  const sessionTitle = roomInfo ? templateName(roomInfo.templateId) : "Session";

  return (
    <AppShell showTaglines={false}>
      <section className="call-page">
        <div className="call-header-row">
          <div>
            <h1>{sessionTitle} · <span>{sessionTitle} Session</span> <span className="live-pill">● Live</span></h1>
            <p>{sessionTitle} template&nbsp;&nbsp;·&nbsp;&nbsp; Precued session</p>
          </div>
          <button className="end-call-top" onClick={endCall} disabled={busy}>☎ End call</button>
        </div>

        <div className="call-grid">
          <div className="call-main-column">
            {me.isHost ? (
              <div className="visibility-controls surface-card-lite">
                <strong>Who can view the current share? <span className="info-icon">i</span></strong>
                <div className="preset-tabs">
                  {presets.map((preset) => (
                    <button
                      key={preset.id}
                      className={selectedPresetId === preset.id ? "active" : ""}
                      disabled={busy}
                      onClick={() => applyPreset(preset)}
                    >
                      ♙ {preset.name}
                    </button>
                  ))}
                </div>
              </div>
            ) : currentShare && canSeeCurrentShare ? (
              <div className="visible-banner"><span>◉</span><div><strong>Visible to your role</strong><small>This content is currently shared with {formatRoleNames(visibleRoleNames)}.</small></div></div>
            ) : null}

            <div className={`share-stage ${currentShare && !canSeeCurrentShare && !me.isHost ? "locked-stage" : ""}`}>
              {!currentShare ? (
                <div className="empty-share-state">
                  <div className="lock-or-share-icon">▣</div>
                  <h2>{me.isHost ? "Ready to share" : "Waiting for shared content"}</h2>
                  <p>{me.isHost ? "Open More and choose Share screen to begin." : "Shared content will appear here when the host starts sharing."}</p>
                </div>
              ) : !canSeeCurrentShare && !me.isHost ? (
                <div className="locked-content">
                  <div className="lock-circle">▢</div>
                  <h2>Content not shared with your role</h2>
                  <p>The host is currently sharing material visible only to {formatRoleNames(visibleRoleNames)}.</p>
                  <span />
                  <small>You’ll see shared content here when it becomes available to the {ownRole?.name ?? me.roleName} role.</small>
                </div>
              ) : (
                <>
                  <div className="share-toolbar"><div><strong>▧ {currentShare.label}</strong><small>Shared by host</small></div><div className="share-toolbar-actions">100%⌄ &nbsp; ↗</div></div>
                  <div className="share-video-wrap">
                    {currentScreenTrack ? <VideoTrack trackRef={currentScreenTrack as any} /> : (
                      <div className="share-pending"><div className="spinner small" /><strong>Preparing shared content...</strong><small>Visibility is set; waiting for the LiveKit track.</small></div>
                    )}
                  </div>
                </>
              )}
            </div>

            <div className="video-strip">
              {participants.filter((participant) => !participant.leftAt).map((participant) => {
                const cameraRef = cameraTracks.find((trackRef: any) => trackRef.participant?.identity === participant.livekitIdentity);
                const role = participant.activeRoomRoleId ? roleById.get(participant.activeRoomRoleId) : undefined;
                return (
                  <div className="video-tile" key={participant.id}>
                    {cameraRef ? <VideoTrack trackRef={cameraRef as any} /> : <div className="video-avatar">{initials(participant.displayName)}</div>}
                    <div className="video-label">◉ {participant.displayName} {role?.isHostRole && <span>Host</span>} {participant.id === me.id && !role?.isHostRole && <span>You</span>}</div>
                    <div className="signal-bars">▮▮▮</div>
                  </div>
                );
              })}
            </div>
          </div>

          <ParticipantsSidebar
            participants={participants}
            roles={roles}
            currentShare={currentShare}
            meId={me.id}
            isHost={me.isHost}
            visibleRoleNames={visibleRoleNames}
            canSeeCurrentShare={canSeeCurrentShare}
            muted={!room.localParticipant.isMicrophoneEnabled}
            videoOff={!room.localParticipant.isCameraEnabled}
            onToggleMute={() => { void room.localParticipant.setMicrophoneEnabled(!room.localParticipant.isMicrophoneEnabled); }}
            onToggleVideo={() => { void room.localParticipant.setCameraEnabled(!room.localParticipant.isCameraEnabled); }}
            onEndCall={() => { void endCall(); }}
            moreOpen={moreOpen}
            setMoreOpen={setMoreOpen}
            onStartShare={() => { void startScreenShare(); }}
            onStopShare={() => { void stopScreenShare(); }}
            busy={busy}
          />
        </div>
        {error && <div className="error-banner floating-error">{error}</div>}
      </section>
    </AppShell>
  );
}

function ParticipantsSidebar(props: {
  participants: RoomParticipantWithGrants[];
  roles: RoomRole[];
  currentShare: ActiveShare | null;
  meId: string;
  isHost: boolean;
  visibleRoleNames: string[];
  canSeeCurrentShare: boolean;
  muted: boolean;
  videoOff: boolean;
  onToggleMute: () => void;
  onToggleVideo: () => void;
  onEndCall: () => void;
  moreOpen: boolean;
  setMoreOpen: (value: boolean) => void;
  onStartShare: () => void;
  onStopShare: () => void;
  busy: boolean;
}) {
  const roleById = new Map(props.roles.map((role) => [role.id, role]));
  return (
    <aside className="participants-sidebar">
      <div className="participants-heading"><h2>♙ Participants ({props.participants.filter((p) => !p.leftAt).length})</h2><span>•••</span></div>
      <div className="participant-list">
        {props.participants.filter((participant) => !participant.leftAt).map((participant) => {
          const role = participant.activeRoomRoleId ? roleById.get(participant.activeRoomRoleId) : undefined;
          const canSee = Boolean(props.currentShare && participant.activeRoomRoleId && props.currentShare.roomRoleIds.includes(participant.activeRoomRoleId));
          return (
            <div className="participant-row" key={participant.id}>
              <span className="avatar-placeholder large-avatar">{initials(participant.displayName)}</span>
              <div className="participant-copy"><strong>{participant.displayName} {role?.isHostRole && <span className="host-badge">Host</span>} {participant.id === props.meId && !role?.isHostRole && <span className="host-badge">You</span>}</strong><small>{role?.name ?? "Unassigned"}</small></div>
              {props.currentShare && <span className={`visibility-status ${canSee ? "can-see" : "cannot-see"}`}>{canSee ? "◉ Can see" : "⊘ Cannot see"}</span>}
            </div>
          );
        })}
      </div>

      {props.currentShare && !props.isHost && props.canSeeCurrentShare && (
        <div className="sidebar-info-box"><strong>ⓘ Current share is visible to {formatRoleNames(props.visibleRoleNames)}.</strong><p>You can view this content, but you cannot change who can see it.</p></div>
      )}

      <div className="call-controls">
        <ControlButton icon={props.muted ? "🔇" : "♩"} label={props.muted ? "Unmute" : "Mute"} onClick={props.onToggleMute} />
        <ControlButton icon="▣" label={props.videoOff ? "Start video" : "Stop video"} onClick={props.onToggleVideo} />
        <div className="more-control-wrap">
          <ControlButton icon="•••" label="More" onClick={() => props.setMoreOpen(!props.moreOpen)} />
          {props.moreOpen && props.isHost && (
            <div className="more-menu">
              <button disabled={props.busy} onClick={props.currentShare ? props.onStopShare : props.onStartShare}>{props.currentShare ? "Stop sharing" : "Share screen"}</button>
            </div>
          )}
        </div>
        <ControlButton icon="☎" label="End call" danger onClick={props.onEndCall} />
      </div>
    </aside>
  );
}

function ControlButton({ icon, label, onClick, danger = false }: { icon: string; label: string; onClick: () => void; danger?: boolean }) {
  return <button className={`call-control ${danger ? "danger" : ""}`} onClick={onClick}><span>{icon}</span><small>{label}</small></button>;
}

function formatRoleNames(names: string[]) {
  if (!names.length) return "no roles";
  if (names.length === 1) return names[0];
  if (names.length === 2) return `${names[0]} and ${names[1]}`;
  return `${names.slice(0, -1).join(", ")}, and ${names[names.length - 1]}`;
}

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("");
}
