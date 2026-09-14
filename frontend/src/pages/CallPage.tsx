import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { DragEvent } from "react";
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
import { api, describeSlideImageError } from "../lib/api";
import { defaultVisibleRoleIds, toggleVisibleRoleId } from "../lib/fallbackRoleVisibility";
import { initials } from "../lib/initials";
import {
  clearShareGrantIds,
  forgetGrantId,
  getGrantId,
  getParticipant,
  rememberGrantId,
} from "../lib/session";
import { derivePresentationLabel, validatePresentationFile } from "../lib/presentationUpload";
import { templateName } from "../lib/templates";
import {
  computeLocalVisibilityGrants,
  isServerVisibilityGrant,
  toTrackSubscriptionPermissions,
} from "../lib/visibilityGrants";
import type { CellState, VisibilityScope } from "../lib/presentationVisibility";
import {
  cellState,
  planCellStateChange,
  planScopedAudienceChange,
  selectedRoleIdsForScope,
  visibilityAudienceMode,
} from "../lib/presentationVisibility";
import type {
  ActiveShare,
  LiveKitTokenResponse,
  Room,
  RoomParticipantWithGrants,
  RoomRole,
  ShareRoleGrant,
  ShareSlide,
  TemplatePreset,
  VisibilityGrantMessage,
} from "../types/precued";

type SlideImageState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "visible"; objectUrl: string }
  | { status: "locked" }
  | { status: "error"; message: string };

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
  const [fallbackVisibleRoleIds, setFallbackVisibleRoleIds] = useState<string[] | null>(null);
  const [moreOpen, setMoreOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [slides, setSlides] = useState<ShareSlide[]>([]);
  const [grants, setGrants] = useState<ShareRoleGrant[]>([]);
  const [thumbnailUrls, setThumbnailUrls] = useState<Record<string, string>>({});
  const [slideImage, setSlideImage] = useState<SlideImageState>({ status: "idle" });
  const [uploadError, setUploadError] = useState<string | null>(null);
  const slideImageUrlRef = useRef<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const screenTracks = useTracks([Track.Source.ScreenShare], { onlySubscribed: false });
  const cameraTracks = useTracks([Track.Source.Camera], { onlySubscribed: false });
  const currentShare = activeShares[0] ?? null;

  useEffect(() => () => {
    if (slideImageUrlRef.current) URL.revokeObjectURL(slideImageUrlRef.current);
  }, []);

  const refreshSlideImage = useCallback(async (share: ActiveShare) => {
    const result = await api.fetchSlideImage(share.id, share.currentSlideIndex);
    if (slideImageUrlRef.current) {
      URL.revokeObjectURL(slideImageUrlRef.current);
      slideImageUrlRef.current = null;
    }
    if (result.ok) {
      slideImageUrlRef.current = result.objectUrl;
      setSlideImage({ status: "visible", objectUrl: result.objectUrl });
    } else if (result.status === 403) {
      setSlideImage({ status: "locked" });
    } else {
      setSlideImage({ status: "error", message: describeSlideImageError(result.status) });
    }
  }, []);

  const refreshGrants = useCallback(async (shareId: string) => {
    try {
      setGrants(await api.getShareGrants(shareId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load visibility grants");
    }
  }, []);

  useEffect(() => {
    if (!me.isHost || !currentShare || currentShare.kind !== "PRESENTATION") {
      setSlides([]);
      setGrants([]);
      return;
    }
    let cancelled = false;
    api.getSlides(currentShare.id).then((next) => { if (!cancelled) setSlides(next); }).catch(() => {});
    void refreshGrants(currentShare.id);
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentShare?.id, currentShare?.kind, me.isHost]);

  useEffect(() => {
    if (!me.isHost || !currentShare || slides.length === 0) {
      setThumbnailUrls({});
      return;
    }
    let cancelled = false;
    Promise.all(
      slides.map(async (slide) => [slide.id, await api.fetchSlideImage(currentShare.id, slide.slideIndex)] as const),
    ).then((results) => {
      if (cancelled) return;
      const next: Record<string, string> = {};
      for (const [slideId, result] of results) if (result.ok) next[slideId] = result.objectUrl;
      setThumbnailUrls(next);
    });
    return () => { cancelled = true; };
  }, [me.isHost, currentShare?.id, slides]);

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

      const nextCurrentShare = nextShares[0] ?? null;
      if (me.isHost) applyVisibilityPermissions(nextParticipants, nextCurrentShare);

      if (nextCurrentShare && nextCurrentShare.kind === "PRESENTATION") {
        void refreshSlideImage(nextCurrentShare);
      } else if (slideImageUrlRef.current) {
        URL.revokeObjectURL(slideImageUrlRef.current);
        slideImageUrlRef.current = null;
        setSlideImage({ status: "idle" });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to refresh call state");
    }
  }, [roomId, roomInfo, me.isHost, applyVisibilityPermissions, refreshSlideImage]);

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
    if (presets.length > 0) {
      setFallbackVisibleRoleIds(null);
      return;
    }
    if (roles.length === 0) return;
    if (currentShare?.kind === "SCREEN") {
      setFallbackVisibleRoleIds([...currentShare.roomRoleIds]);
      return;
    }
    setFallbackVisibleRoleIds((current) => current ?? defaultVisibleRoleIds(roles));
  }, [presets.length, roles, currentShare?.id, currentShare?.kind, currentShareRoleKey]);

  useEffect(() => {
    const handleData = (
      payload: Uint8Array,
      sender?: RemoteParticipant,
      _kind?: DataPacket_Kind,
      topic?: string,
    ) => {
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
  const fallbackSelectedRoleIds = fallbackVisibleRoleIds ?? defaultVisibleRoleIds(roles);

  const currentScreenTrack = currentShare
    ? screenTracks.find((trackRef: any) => trackRef.publication?.trackName?.startsWith(`${currentShare.id}:`))
    : undefined;

  const viewerSeesCurrent = currentShare
    ? currentShare.kind === "SCREEN" ? canSeeCurrentShare : slideImage.status === "visible"
    : false;

  async function uploadNewPresentation(file: File) {
    await api.uploadPresentation(roomId, me.id, derivePresentationLabel(file.name), file);
    await refresh();
  }

  async function uploadPresentationFile(file: File) {
    if (!me.isHost || currentShare) return;
    const validationError = validatePresentationFile(file);
    if (validationError) {
      setUploadError(validationError);
      return;
    }
    setUploadError(null);
    setBusy(true);
    setError(null);
    setMoreOpen(false);
    try {
      await uploadNewPresentation(file);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to upload presentation");
    } finally {
      setBusy(false);
    }
  }

  async function replacePresentationFile(file: File) {
    if (!me.isHost || currentShare?.kind !== "PRESENTATION") return;
    const validationError = validatePresentationFile(file);
    if (validationError) {
      setUploadError(validationError);
      return;
    }
    setUploadError(null);
    setBusy(true);
    setError(null);
    setMoreOpen(false);
    try {
      await api.endShare(currentShare.id);
      clearShareGrantIds(currentShare.id);
      await uploadNewPresentation(file);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to replace the document");
    } finally {
      setBusy(false);
    }
  }

  function handleShareStageDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    if (!me.isHost) return;
    const file = event.dataTransfer.files?.[0];
    if (!file) return;
    if (!currentShare) {
      void uploadPresentationFile(file);
    } else if (currentShare.kind === "PRESENTATION") {
      void replacePresentationFile(file);
    }
  }

  async function goToSlide(slideIndex: number) {
    if (!currentShare || currentShare.kind !== "PRESENTATION" || !me.isHost) return;
    if (slideIndex < 0 || slideIndex >= slides.length || slideIndex === currentShare.currentSlideIndex) return;
    setBusy(true);
    setError(null);
    try {
      await api.changeSlide(currentShare.id, slideIndex);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to change slide");
    } finally {
      setBusy(false);
    }
  }

  async function setVisibilityCell(roomRoleId: string, slide: ShareSlide, newState: CellState) {
    if (!currentShare) return;
    const plan = planCellStateChange(roomRoleId, slide.id, newState, grants);
    if (plan.toCreate.length === 0 && plan.toRevokeGrantIds.length === 0) return;
    setBusy(true);
    setError(null);
    try {
      for (const grantId of plan.toRevokeGrantIds) await api.revokeGrant(grantId);
      for (const item of plan.toCreate) await api.createGrant(currentShare.id, item.roomRoleId, item.shareSlideId);
      await refreshGrants(currentShare.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to update visibility");
    } finally {
      setBusy(false);
    }
  }

  async function setPresentationAudience(scope: VisibilityScope, visibleRoleIds: string[]) {
    if (!currentShare || currentShare.kind !== "PRESENTATION" || !me.isHost) return;
    const currentSlide = slides.find((slide) => slide.slideIndex === currentShare.currentSlideIndex);
    if (!currentSlide) return;

    const roleIds = roles.filter((role) => !role.isHostRole).map((role) => role.id);
    const plan = planScopedAudienceChange(
      roleIds,
      visibleRoleIds,
      slides.map((slide) => slide.id),
      currentSlide.id,
      scope,
      grants,
    );
    if (plan.toCreate.length === 0 && plan.toRevokeGrantIds.length === 0) return;

    setBusy(true);
    setError(null);
    try {
      for (const grantId of plan.toRevokeGrantIds) await api.revokeGrant(grantId);
      for (const item of plan.toCreate) await api.createGrant(currentShare.id, item.roomRoleId, item.shareSlideId);
      await refreshGrants(currentShare.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to update visibility");
    } finally {
      setBusy(false);
    }
  }

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

  async function setFallbackScreenRoleVisibility(roomRoleId: string) {
    if (!me.isHost || presets.length > 0) return;
    const nextRoleIds = toggleVisibleRoleId(fallbackSelectedRoleIds, roomRoleId);
    setFallbackVisibleRoleIds(nextRoleIds);

    if (!currentShare || currentShare.kind !== "SCREEN") return;
    const shouldBeVisible = nextRoleIds.includes(roomRoleId);
    const isVisible = currentShare.roomRoleIds.includes(roomRoleId);
    if (shouldBeVisible === isVisible) return;

    setBusy(true);
    setError(null);
    try {
      if (shouldBeVisible) {
        const grant = await api.createGrant(currentShare.id, roomRoleId);
        rememberGrantId(currentShare.id, roomRoleId, grant.id);
      } else {
        const activeGrants = (await api.getShareGrants(currentShare.id))
          .filter((grant) => !grant.revokedAt && grant.shareSlideId === null);
        const grant = activeGrants.find((item) => item.roomRoleId === roomRoleId);
        if (!grant) throw new Error("Unable to find the active visibility grant for that role.");
        await api.revokeGrant(grant.id);
        forgetGrantId(currentShare.id, roomRoleId);
      }
      await refresh();
    } catch (err) {
      setFallbackVisibleRoleIds([...currentShare.roomRoleIds]);
      setError(err instanceof Error ? err.message : "Unable to update visibility");
    } finally {
      setBusy(false);
    }
  }

  async function startScreenShare() {
    if (!me.isHost || currentShare) return;
    const preset = presets.find((item) => item.id === selectedPresetId) ?? presets[0];
    const targetRoleIds = preset ? roleIdsForPreset(preset) : fallbackSelectedRoleIds;
    setBusy(true);
    setError(null);
    setMoreOpen(false);
    let createdShare: ActiveShare | null = null;
    try {
      const share = await api.startShare(roomId, me.id, preset?.id ?? null, "Screen share");
      createdShare = { id: share.id, label: share.label, kind: share.kind, currentSlideIndex: share.currentSlideIndex, roomRoleIds: [] };
      for (const roomRoleId of targetRoleIds) {
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
  const nonHostRoles = roles.filter((role) => !role.isHostRole);

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
            <input
              ref={fileInputRef}
              type="file"
              accept="application/pdf"
              style={{ display: "none" }}
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) {
                  if (currentShare?.kind === "PRESENTATION") void replacePresentationFile(file);
                  else void uploadPresentationFile(file);
                }
                event.target.value = "";
              }}
            />

            {me.isHost ? (
              currentShare?.kind === "PRESENTATION" ? (
                <SlideVisibilityMatrix
                  roles={nonHostRoles}
                  slides={slides}
                  grants={grants}
                  thumbnailUrls={thumbnailUrls}
                  currentSlideIndex={currentShare.currentSlideIndex}
                  disabled={busy}
                  onApplyAudience={(scope, roleIds) => { void setPresentationAudience(scope, roleIds); }}
                  onChangeCell={setVisibilityCell}
                  onGoToSlide={(index) => { void goToSlide(index); }}
                />
              ) : (
                <div className="visibility-controls surface-card-lite">
                  <strong>Who can view the current share? <span className="info-icon">i</span></strong>
                  <div className="preset-tabs">
                    {presets.length > 0 ? presets.map((preset) => (
                      <button
                        key={preset.id}
                        className={selectedPresetId === preset.id ? "active" : ""}
                        disabled={busy}
                        onClick={() => applyPreset(preset)}
                      >
                        ♙ {preset.name}
                      </button>
                    )) : nonHostRoles.length > 0 ? nonHostRoles.map((role) => (
                      <button
                        key={role.id}
                        className={fallbackSelectedRoleIds.includes(role.id) ? "active" : ""}
                        disabled={busy}
                        onClick={() => { void setFallbackScreenRoleVisibility(role.id); }}
                      >
                        ♙ {role.name}
                      </button>
                    )) : (
                      <span className="empty-state-note">Add a non-host role to control who can view shared content.</span>
                    )}
                  </div>
                </div>
              )
            ) : currentShare && viewerSeesCurrent ? (
              <div className="visible-banner"><span>◉</span><div><strong>Visible to your role</strong><small>{currentShare.kind === "SCREEN" ? `This content is currently shared with ${formatRoleNames(visibleRoleNames)}.` : "This slide is currently visible to your role."}</small></div></div>
            ) : null}

            <div
              className={`share-stage ${(currentShare?.kind === "SCREEN" && !canSeeCurrentShare && !me.isHost) || (currentShare?.kind === "PRESENTATION" && !me.isHost && slideImage.status === "locked") ? "locked-stage" : ""}`}
              onDragOver={(event) => event.preventDefault()}
              onDrop={handleShareStageDrop}
            >
              {!currentShare ? (
                <div className="empty-share-state">
                  <div className="lock-or-share-icon">▣</div>
                  <h2>{me.isHost ? "Ready to share" : "Waiting for shared content"}</h2>
                  <p>{me.isHost ? "Open More to share your screen, or drop a PDF here to share a presentation." : "Shared content will appear here when the host starts sharing."}</p>
                  {me.isHost && (
                    <button className="secondary-button" disabled={busy} onClick={() => fileInputRef.current?.click()}>
                      ▤ Share a presentation
                    </button>
                  )}
                </div>
              ) : currentShare.kind === "PRESENTATION" ? (
                slideImage.status === "visible" ? (
                  <div className="slide-stage-wrap">
                    <img className="slide-image" src={slideImage.objectUrl} alt={`Slide ${currentShare.currentSlideIndex + 1}`} />
                  </div>
                ) : slideImage.status === "locked" ? (
                  <div className="locked-content">
                    <div className="lock-circle">▢</div>
                    <h2>Content not shared with your role</h2>
                    <p>The host is currently showing a slide that isn’t visible to the {ownRole?.name ?? me.roleName} role.</p>
                    <span />
                    <small>You’ll see it here as soon as the host makes it visible to your role.</small>
                  </div>
                ) : slideImage.status === "error" ? (
                  <div className="locked-content">
                    <div className="lock-circle">!</div>
                    <h2>Couldn’t load this slide</h2>
                    <p>{slideImage.message}</p>
                  </div>
                ) : (
                  <div className="share-pending"><div className="spinner small" /><strong>Loading slide...</strong></div>
                )
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
            {uploadError && <div className="error-banner">{uploadError}</div>}

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
            canSeeCurrentShare={viewerSeesCurrent}
            muted={!room.localParticipant.isMicrophoneEnabled}
            videoOff={!room.localParticipant.isCameraEnabled}
            onToggleMute={() => { void room.localParticipant.setMicrophoneEnabled(!room.localParticipant.isMicrophoneEnabled); }}
            onToggleVideo={() => { void room.localParticipant.setCameraEnabled(!room.localParticipant.isCameraEnabled); }}
            onEndCall={() => { void endCall(); }}
            moreOpen={moreOpen}
            setMoreOpen={setMoreOpen}
            onStartShare={() => { void startScreenShare(); }}
            onStopShare={() => { void stopScreenShare(); }}
            onSharePresentation={() => fileInputRef.current?.click()}
            onReplaceDocument={() => fileInputRef.current?.click()}
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
  onSharePresentation: () => void;
  onReplaceDocument: () => void;
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
              {props.currentShare?.kind === "SCREEN" && <span className={`visibility-status ${canSee ? "can-see" : "cannot-see"}`}>{canSee ? "◉ Can see" : "⊘ Cannot see"}</span>}
            </div>
          );
        })}
      </div>

      {props.currentShare?.kind === "SCREEN" && !props.isHost && props.canSeeCurrentShare && (
        <div className="sidebar-info-box"><strong>ⓘ Current share is visible to {formatRoleNames(props.visibleRoleNames)}.</strong><p>You can view this content, but you cannot change who can see it.</p></div>
      )}

      <div className="call-controls">
        <ControlButton icon={props.muted ? "🔇" : "♩"} label={props.muted ? "Unmute" : "Mute"} onClick={props.onToggleMute} />
        <ControlButton icon="▣" label={props.videoOff ? "Start video" : "Stop video"} onClick={props.onToggleVideo} />
        <div className="more-control-wrap">
          <ControlButton icon="•••" label="More" onClick={() => props.setMoreOpen(!props.moreOpen)} />
          {props.moreOpen && props.isHost && (
            <div className="more-menu">
              {props.currentShare ? (
                <>
                  {props.currentShare.kind === "PRESENTATION" && (
                    <button disabled={props.busy} onClick={props.onReplaceDocument}>Replace document</button>
                  )}
                  <button disabled={props.busy} onClick={props.onStopShare}>Stop sharing</button>
                </>
              ) : (
                <>
                  <button disabled={props.busy} onClick={props.onStartShare}>Share screen</button>
                  <button disabled={props.busy} onClick={props.onSharePresentation}>Share presentation</button>
                </>
              )}
            </div>
          )}
        </div>
        <ControlButton icon="☎" label="End call" danger onClick={props.onEndCall} />
      </div>
    </aside>
  );
}

function SlideVisibilityMatrix(props: {
  roles: RoomRole[];
  slides: ShareSlide[];
  grants: ShareRoleGrant[];
  thumbnailUrls: Record<string, string>;
  currentSlideIndex: number;
  disabled: boolean;
  onApplyAudience: (scope: VisibilityScope, visibleRoleIds: string[]) => void;
  onChangeCell: (roomRoleId: string, slide: ShareSlide, newState: CellState) => void;
  onGoToSlide: (slideIndex: number) => void;
}) {
  const [scope, setScope] = useState<VisibilityScope>("slide");
  const [forceSpecific, setForceSpecific] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const currentSlide = props.slides.find((slide) => slide.slideIndex === props.currentSlideIndex);
  const roleIds = props.roles.map((role) => role.id);
  const selectedRoleIds = currentSlide
    ? selectedRoleIdsForScope(roleIds, currentSlide.id, scope, props.grants)
    : [];
  const derivedAudienceMode = visibilityAudienceMode(roleIds, selectedRoleIds);
  const audienceMode = forceSpecific || derivedAudienceMode === "specific" ? "specific" : "everyone";
  const cellLabels: Record<CellState, string> = { always: "★ Always", slide: "◐ This slide", hidden: "⊘ Hidden" };

  function changeScope(nextScope: VisibilityScope) {
    setScope(nextScope);
    setForceSpecific(false);
  }

  function toggleRole(roleId: string) {
    const nextRoleIds = selectedRoleIds.includes(roleId)
      ? selectedRoleIds.filter((id) => id !== roleId)
      : [...selectedRoleIds, roleId];
    props.onApplyAudience(scope, nextRoleIds);
  }

  return (
    <div className="visibility-controls presentation-visibility-panel surface-card-lite">
      <div className="presentation-visibility-heading">
        <div>
          <strong>Share visibility <span className="info-icon">i</span></strong>
          <small>Choose the audience first. Open the matrix only when you need per-slide exceptions.</small>
        </div>
        <div className="slide-nav-row compact-slide-nav">
          <button disabled={props.disabled || props.currentSlideIndex <= 0} onClick={() => props.onGoToSlide(props.currentSlideIndex - 1)}>‹ Prev</button>
          <span>Slide {props.currentSlideIndex + 1} of {props.slides.length}</span>
          <button disabled={props.disabled || props.currentSlideIndex >= props.slides.length - 1} onClick={() => props.onGoToSlide(props.currentSlideIndex + 1)}>Next ›</button>
        </div>
      </div>

      <div className="presentation-slide-strip" aria-label="Presentation slides">
        {props.slides.map((slide) => (
          <button
            key={slide.id}
            type="button"
            className={`slide-thumb-button ${slide.slideIndex === props.currentSlideIndex ? "active" : ""}`}
            disabled={props.disabled}
            onClick={() => props.onGoToSlide(slide.slideIndex)}
          >
            {props.thumbnailUrls[slide.id] ? (
              <img className="slide-thumb-image" src={props.thumbnailUrls[slide.id]} alt={`Slide ${slide.slideIndex + 1}`} />
            ) : (
              <span className="slide-thumb-placeholder" />
            )}
            <small>{slide.slideIndex + 1}</small>
          </button>
        ))}
      </div>

      <div className="simple-visibility-grid">
        <section className="simple-visibility-section">
          <span className="simple-visibility-label">Visible to</span>
          <div className="visibility-segmented" role="group" aria-label="Visible to">
            <button
              type="button"
              className={audienceMode === "everyone" ? "active" : ""}
              disabled={props.disabled || roleIds.length === 0}
              onClick={() => {
                setForceSpecific(false);
                props.onApplyAudience(scope, roleIds);
              }}
            >
              Everyone
            </button>
            <button
              type="button"
              className={audienceMode === "specific" ? "active" : ""}
              disabled={props.disabled || roleIds.length === 0}
              onClick={() => setForceSpecific(true)}
            >
              Specific roles
            </button>
          </div>
          {audienceMode === "specific" && (
            <div className="visibility-role-choices">
              {props.roles.map((role) => (
                <label key={role.id} className={selectedRoleIds.includes(role.id) ? "selected" : ""}>
                  <input
                    type="checkbox"
                    checked={selectedRoleIds.includes(role.id)}
                    disabled={props.disabled}
                    onChange={() => toggleRole(role.id)}
                  />
                  <span>{role.name}</span>
                </label>
              ))}
            </div>
          )}
        </section>

        <section className="simple-visibility-section">
          <span className="simple-visibility-label">Apply to</span>
          <div className="visibility-segmented" role="group" aria-label="Apply visibility to">
            <button type="button" className={scope === "slide" ? "active" : ""} disabled={props.disabled} onClick={() => changeScope("slide")}>This slide</button>
            <button type="button" className={scope === "presentation" ? "active" : ""} disabled={props.disabled} onClick={() => changeScope("presentation")}>Entire presentation</button>
          </div>
          <small className="visibility-scope-note">
            {scope === "slide"
              ? "Changes only this slide. Visibility on the other slides is preserved."
              : "Uses the same audience on every slide in this presentation."}
          </small>
        </section>
      </div>

      <button
        type="button"
        className="advanced-visibility-toggle"
        aria-expanded={advancedOpen}
        onClick={() => setAdvancedOpen(!advancedOpen)}
      >
        <span>Advanced visibility settings</span>
        <span aria-hidden="true">{advancedOpen ? "▴" : "▾"}</span>
      </button>

      {advancedOpen && (
        <div className="advanced-visibility-panel">
          <div className="advanced-visibility-copy">
            <strong>Per-slide role matrix</strong>
            <small>Always applies to every slide. This slide creates a slide-specific grant. Hidden removes visibility for that cell.</small>
          </div>
          <div className="slide-matrix-scroll">
            <table className="slide-matrix compact-visibility-matrix">
              <thead>
                <tr>
                  <th>Role</th>
                  {props.slides.map((slide) => <th key={slide.id}>Slide {slide.slideIndex + 1}</th>)}
                </tr>
              </thead>
              <tbody>
                {props.roles.map((role) => (
                  <tr key={role.id}>
                    <th scope="row">{role.name}</th>
                    {props.slides.map((slide) => {
                      const state = cellState(role.id, slide.id, props.grants);
                      return (
                        <td key={slide.id}>
                          <div className="cell-toggle">
                            {(["always", "slide", "hidden"] as const).map((option) => (
                              <button
                                key={option}
                                type="button"
                                className={state === option ? "active" : ""}
                                disabled={props.disabled}
                                title={option === "always" ? "Always visible, on every slide" : option === "slide" ? "Visible only on this slide" : "Hidden on this slide"}
                                onClick={() => props.onChangeCell(role.id, slide, option)}
                              >
                                {cellLabels[option]}
                              </button>
                            ))}
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
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
