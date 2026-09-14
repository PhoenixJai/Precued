import { useEffect, useState } from "react";
import type { CSSProperties } from "react";
import { createPortal } from "react-dom";
import { useParams } from "react-router-dom";
import { SessionFlowCallDock } from "../components/SessionFlowCallDock";
import {
  availableLiveViewModes,
  defaultLiveViewMode,
  liveSessionDesktopGridTemplate,
  liveSessionMode,
  liveSessionShellPolicy,
  participantGridLayout,
  participantMediaMode,
  sessionFlowSurface,
  shouldShowSharingSidebar,
} from "../lib/liveSessionUi";
import type { LiveSessionMode, LiveViewMode } from "../lib/liveSessionUi";
import {
  fullscreenButtonLabel,
  toggleShareStageFullscreen,
} from "../lib/shareStageFullscreen";
import CallPage from "./CallPage";
import "../shareStageFullscreen.css";
import "../liveSessionUtilitySidebar.css";
import "../liveSessionGridFirst.css";
import "../liveSharingSidebar.css";
import "../liveViewControls.css";

export default function LiveSessionRoute() {
  const { roomId = "" } = useParams();
  const [hasActiveShare, setHasActiveShare] = useState(false);
  const [canManageShare, setCanManageShare] = useState(false);
  const [participantCount, setParticipantCount] = useState(1);
  const [gridLayout, setGridLayout] = useState(() => participantGridLayout(1));
  const [panelsOpen, setPanelsOpen] = useState(false);
  const [viewMode, setViewMode] = useState<LiveViewMode>("gallery");
  const shellPolicy = liveSessionShellPolicy();
  const mode = liveSessionMode(hasActiveShare);
  const showSharingSidebar = shouldShowSharingSidebar(mode, canManageShare);
  const layoutStyle = {
    "--live-session-desktop-columns": liveSessionDesktopGridTemplate(),
  } as CSSProperties;

  useEffect(() => {
    setViewMode(defaultLiveViewMode(mode));
  }, [mode]);

  useEffect(() => {
    const resolveWorkspace = () => {
      const callGrid = document.querySelector<HTMLElement>(".live-session-route .call-grid");
      if (!callGrid) return;

      const nextHasActiveShare = Boolean(callGrid.querySelector(".share-stage"))
        && !Boolean(callGrid.querySelector(".empty-share-state"));
      const nextParticipantCount = callGrid.querySelectorAll(".video-strip .video-tile").length;
      const nextCanManageShare = Boolean(callGrid.querySelector(".visibility-controls"));

      setHasActiveShare(nextHasActiveShare);
      setCanManageShare(nextCanManageShare);
      setParticipantCount(Math.max(1, nextParticipantCount));
      setGridLayout(participantGridLayout(nextParticipantCount));
      if (!nextHasActiveShare || !nextCanManageShare) setPanelsOpen(false);
    };

    resolveWorkspace();
    const observer = new MutationObserver(resolveWorkspace);
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  return (
    <div
      className={[
        "live-session-route",
        shellPolicy.showGlobalNavigation ? "" : "live-session-route--standalone",
        `session-mode-${mode}`,
        `session-view-${viewMode}`,
        `participant-layout-${gridLayout}`,
        panelsOpen ? "sharing-sidebar-open" : "panels-collapsed",
      ].filter(Boolean).join(" ")}
      data-session-flow-placement={shellPolicy.sessionFlowPlacement}
      data-workspace-mode={shellPolicy.workspaceMode}
      data-session-mode={mode}
      data-live-view={viewMode}
      data-participant-layout={gridLayout}
      data-sharing-sidebar={showSharingSidebar ? (panelsOpen ? "open" : "collapsed") : "unavailable"}
      style={layoutStyle}
    >
      {roomId && sessionFlowSurface(mode, canManageShare) === "topbar" && (
        <div className="live-session-flow-layer">
          <SessionFlowCallDock roomId={roomId} />
        </div>
      )}

      <CallPage />

      <LiveViewControl
        mode={mode}
        value={viewMode}
        onChange={setViewMode}
      />

      <ParticipantCameraFallbacks />

      <SharingSidebarCaretPortal
        visible={showSharingSidebar}
        open={panelsOpen}
        onToggle={() => setPanelsOpen((open) => !open)}
      />

      {roomId && (
        <SharingSidebarSupplement
          visible={showSharingSidebar}
          roomId={roomId}
          participantCount={participantCount}
        />
      )}

      <LiveWorkspaceFullscreenControl />
    </div>
  );
}

function LiveViewControl(props: {
  mode: LiveSessionMode;
  value: LiveViewMode;
  onChange: (view: LiveViewMode) => void;
}) {
  const [target, setTarget] = useState<HTMLElement | null>(null);
  const [open, setOpen] = useState(false);
  const options = availableLiveViewModes(props.mode);
  const labels: Record<LiveViewMode, string> = {
    gallery: "Gallery",
    speaker: "Speaker",
    share: "Shared content",
  };

  useEffect(() => {
    const resolveTarget = () => {
      setTarget(document.querySelector<HTMLElement>(".live-session-route .call-controls"));
    };
    resolveTarget();
    const observer = new MutationObserver(resolveTarget);
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  useEffect(() => setOpen(false), [props.mode]);

  if (!target) return null;

  return createPortal(
    <div className="live-view-control-wrap">
      <button
        type="button"
        className="call-control live-view-control-button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <span aria-hidden="true">▦</span>
        <small>View</small>
      </button>
      {open && (
        <div className="live-view-menu" role="menu" aria-label="Change session view">
          <div className="live-view-menu-heading">View</div>
          {options.map((option) => (
            <button
              key={option}
              type="button"
              role="menuitemradio"
              aria-checked={props.value === option}
              className={props.value === option ? "active" : ""}
              onClick={() => {
                props.onChange(option);
                setOpen(false);
              }}
            >
              <span aria-hidden="true">{option === "gallery" ? "▦" : option === "speaker" ? "▣" : "▤"}</span>
              <span>{labels[option]}</span>
              {props.value === option && <span className="live-view-check" aria-hidden="true">✓</span>}
            </button>
          ))}
        </div>
      )}
    </div>,
    target,
  );
}

function ParticipantCameraFallbacks() {
  useEffect(() => {
    const updateTiles = () => {
      document.querySelectorAll<HTMLElement>(".live-session-route .video-tile").forEach((tile) => {
        const video = tile.querySelector<HTMLVideoElement>("video");
        if (!video) {
          tile.classList.remove("camera-fallback");
          delete tile.dataset.participantInitials;
          return;
        }

        const stream = video.srcObject instanceof MediaStream ? video.srcObject : null;
        const track = stream?.getVideoTracks()[0];
        const cameraMuted = !track
          || track.muted
          || !track.enabled
          || track.readyState !== "live"
          || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA;
        const mediaMode = participantMediaMode(Boolean(track), cameraMuted);

        if (mediaMode === "avatar") {
          const labelText = tile.querySelector<HTMLElement>(".video-label")?.textContent ?? "";
          const displayName = labelText
            .replace(/^\s*◉\s*/, "")
            .replace(/\s+Host\s*$/, "")
            .replace(/\s+You\s*$/, "")
            .trim();
          const initials = displayName
            .split(/\s+/)
            .filter(Boolean)
            .slice(0, 2)
            .map((part) => part.charAt(0).toUpperCase())
            .join("") || "•";

          tile.classList.add("camera-fallback");
          tile.dataset.participantInitials = initials;
        } else {
          tile.classList.remove("camera-fallback");
          delete tile.dataset.participantInitials;
        }
      });
    };

    updateTiles();
    const observer = new MutationObserver(updateTiles);
    observer.observe(document.body, { childList: true, subtree: true });
    const timer = window.setInterval(updateTiles, 500);
    return () => {
      observer.disconnect();
      window.clearInterval(timer);
    };
  }, []);

  return null;
}

function SharingSidebarCaretPortal(props: {
  visible: boolean;
  open: boolean;
  onToggle: () => void;
}) {
  const [workspace, setWorkspace] = useState<HTMLElement | null>(null);

  useEffect(() => {
    const resolveWorkspace = () => {
      setWorkspace(document.querySelector<HTMLElement>(".live-session-route .call-grid"));
    };
    resolveWorkspace();
    const observer = new MutationObserver(resolveWorkspace);
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  if (!props.visible || !workspace) return null;

  return createPortal(
    <button
      type="button"
      className="live-session-sidebar-caret"
      aria-expanded={props.open}
      aria-label={props.open ? "Hide sharing controls" : "Show sharing controls"}
      title={props.open ? "Hide sharing controls" : "Show sharing controls"}
      onClick={props.onToggle}
    >
      <span aria-hidden="true">{props.open ? "›" : "‹"}</span>
    </button>,
    workspace,
  );
}

function SharingSidebarSupplement(props: {
  visible: boolean;
  roomId: string;
  participantCount: number;
}) {
  const [target, setTarget] = useState<HTMLElement | null>(null);
  const [workspace, setWorkspace] = useState<HTMLElement | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    const resolveTargets = () => {
      setTarget(document.querySelector<HTMLElement>(".live-session-route .visibility-controls"));
      setWorkspace(document.querySelector<HTMLElement>(".live-session-route .call-grid"));
    };
    resolveTargets();
    const observer = new MutationObserver(resolveTargets);
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(Boolean(workspace && document.fullscreenElement === workspace));
    };
    handleFullscreenChange();
    document.addEventListener("fullscreenchange", handleFullscreenChange);
    return () => document.removeEventListener("fullscreenchange", handleFullscreenChange);
  }, [workspace]);

  if (!props.visible || !target) return null;

  return createPortal(
    <>
      <header className="sharing-sidebar-header">
        <span className="sharing-sidebar-eyebrow">In-session controls</span>
        <strong>Sharing controls</strong>
        <small>Manage this share without covering the presentation.</small>
      </header>

      <section className="sharing-sidebar-actions" aria-label="Share actions">
        <span className="sharing-sidebar-section-label">Share actions</span>
        <button
          type="button"
          className="sharing-sidebar-action-button"
          disabled={!workspace}
          onClick={() => {
            if (workspace) void toggleShareStageFullscreen(workspace).catch(() => {});
          }}
        >
          <span aria-hidden="true">⛶</span>
          {fullscreenButtonLabel(isFullscreen)}
        </button>
      </section>

      <details className="sharing-sidebar-flow-section">
        <summary>
          <span>Session Flow</span>
          <span aria-hidden="true">⌄</span>
        </summary>
        <div className="sharing-sidebar-flow-content">
          <SessionFlowCallDock roomId={props.roomId} />
        </div>
      </details>

      <div className="sharing-sidebar-participant-summary">
        <span>Participants</span>
        <strong>{props.participantCount}</strong>
        <small>Everyone remains visible in the filmstrip below.</small>
      </div>
    </>,
    target,
  );
}

function LiveWorkspaceFullscreenControl() {
  const [workspace, setWorkspace] = useState<HTMLElement | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    const resolveWorkspace = () => {
      setWorkspace(document.querySelector<HTMLElement>(".live-session-route .call-grid"));
    };

    resolveWorkspace();
    const observer = new MutationObserver(resolveWorkspace);
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(Boolean(workspace && document.fullscreenElement === workspace));
    };

    handleFullscreenChange();
    document.addEventListener("fullscreenchange", handleFullscreenChange);
    return () => document.removeEventListener("fullscreenchange", handleFullscreenChange);
  }, [workspace]);

  if (!workspace) return null;

  return createPortal(
    <button
      type="button"
      className="share-stage-fullscreen-button live-workspace-fullscreen-button"
      aria-pressed={isFullscreen}
      onClick={() => {
        void toggleShareStageFullscreen(workspace).catch(() => {});
      }}
    >
      <span aria-hidden="true">⛶</span>
      {fullscreenButtonLabel(isFullscreen)}
    </button>,
    workspace,
  );
}
