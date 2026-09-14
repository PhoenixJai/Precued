import { useEffect, useState } from "react";
import type { CSSProperties } from "react";
import { createPortal } from "react-dom";
import { useParams } from "react-router-dom";
import { SessionFlowCallDock } from "../components/SessionFlowCallDock";
import {
  liveSessionDesktopGridTemplate,
  liveSessionMode,
  liveSessionShellPolicy,
  participantGridLayout,
} from "../lib/liveSessionUi";
import {
  fullscreenButtonLabel,
  toggleShareStageFullscreen,
} from "../lib/shareStageFullscreen";
import CallPage from "./CallPage";
import "../shareStageFullscreen.css";
import "../liveSessionUtilitySidebar.css";
import "../liveSessionGridFirst.css";

export default function LiveSessionRoute() {
  const { roomId = "" } = useParams();
  const [hasActiveShare, setHasActiveShare] = useState(false);
  const [gridLayout, setGridLayout] = useState(() => participantGridLayout(1));
  const [panelsOpen, setPanelsOpen] = useState(false);
  const shellPolicy = liveSessionShellPolicy();
  const mode = liveSessionMode(hasActiveShare);
  const layoutStyle = {
    "--live-session-desktop-columns": liveSessionDesktopGridTemplate(),
  } as CSSProperties;

  useEffect(() => {
    const resolveWorkspace = () => {
      const callGrid = document.querySelector<HTMLElement>(".live-session-route .call-grid");
      if (!callGrid) return;

      const nextHasActiveShare = Boolean(callGrid.querySelector(".share-stage"))
        && !Boolean(callGrid.querySelector(".empty-share-state"));
      const participantCount = callGrid.querySelectorAll(".video-strip .video-tile").length;

      setHasActiveShare(nextHasActiveShare);
      setGridLayout(participantGridLayout(participantCount));
      if (!nextHasActiveShare) setPanelsOpen(false);
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
        `participant-layout-${gridLayout}`,
        panelsOpen ? "" : "panels-collapsed",
      ].filter(Boolean).join(" ")}
      data-session-flow-placement={shellPolicy.sessionFlowPlacement}
      data-workspace-mode={shellPolicy.workspaceMode}
      data-session-mode={mode}
      data-participant-layout={gridLayout}
      style={layoutStyle}
    >
      {roomId && (
        <div className="live-session-flow-layer">
          <SessionFlowCallDock roomId={roomId} />
        </div>
      )}

      {mode === "share" && (
        <button
          type="button"
          className="live-session-sidebar-caret"
          aria-expanded={panelsOpen}
          aria-label={panelsOpen ? "Hide sharing controls" : "Show sharing controls"}
          title={panelsOpen ? "Hide sharing controls" : "Show sharing controls"}
          onClick={() => setPanelsOpen((open) => !open)}
        >
          <span aria-hidden="true">{panelsOpen ? "›" : "‹"}</span>
        </button>
      )}

      <CallPage />
      <LiveWorkspaceFullscreenControl />
    </div>
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
