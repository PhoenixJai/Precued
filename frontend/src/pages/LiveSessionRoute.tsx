import { useEffect, useState } from "react";
import type { CSSProperties } from "react";
import { createPortal } from "react-dom";
import { useParams } from "react-router-dom";
import { SessionFlowCallDock } from "../components/SessionFlowCallDock";
import {
  liveSessionDesktopGridTemplate,
  liveSessionShellPolicy,
} from "../lib/liveSessionUi";
import {
  fullscreenButtonLabel,
  toggleShareStageFullscreen,
} from "../lib/shareStageFullscreen";
import CallPage from "./CallPage";
import "../shareStageFullscreen.css";
import "../liveSessionUtilitySidebar.css";

export default function LiveSessionRoute() {
  const { roomId = "" } = useParams();
  const [panelsOpen, setPanelsOpen] = useState(true);
  const shellPolicy = liveSessionShellPolicy();
  const layoutStyle = {
    "--live-session-desktop-columns": liveSessionDesktopGridTemplate(),
  } as CSSProperties;

  return (
    <div
      className={[
        "live-session-route",
        shellPolicy.showGlobalNavigation ? "" : "live-session-route--standalone",
        panelsOpen ? "" : "panels-collapsed",
      ].filter(Boolean).join(" ")}
      data-session-flow-placement={shellPolicy.sessionFlowPlacement}
      data-workspace-mode={shellPolicy.workspaceMode}
      style={layoutStyle}
    >
      {roomId && (
        <div className="live-session-flow-layer">
          <SessionFlowCallDock roomId={roomId} />
        </div>
      )}

      <button
        type="button"
        className="live-session-sidebar-caret"
        aria-expanded={panelsOpen}
        aria-label={panelsOpen ? "Hide settings sidebar" : "Show settings sidebar"}
        title={panelsOpen ? "Hide settings sidebar" : "Show settings sidebar"}
        onClick={() => setPanelsOpen((open) => !open)}
      >
        <span aria-hidden="true">{panelsOpen ? "›" : "‹"}</span>
      </button>

      <CallPage />
      <ShareStageFullscreenControl />
    </div>
  );
}

function ShareStageFullscreenControl() {
  const [shareStage, setShareStage] = useState<HTMLElement | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    const resolveShareStage = () => {
      setShareStage(document.querySelector<HTMLElement>(".live-session-route .share-stage"));
    };

    resolveShareStage();
    const observer = new MutationObserver(resolveShareStage);
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(Boolean(shareStage && document.fullscreenElement === shareStage));
    };

    handleFullscreenChange();
    document.addEventListener("fullscreenchange", handleFullscreenChange);
    return () => document.removeEventListener("fullscreenchange", handleFullscreenChange);
  }, [shareStage]);

  if (!shareStage) return null;

  return createPortal(
    <button
      type="button"
      className="share-stage-fullscreen-button"
      aria-pressed={isFullscreen}
      onClick={() => {
        void toggleShareStageFullscreen(shareStage).catch(() => {});
      }}
    >
      <span aria-hidden="true">⛶</span>
      {fullscreenButtonLabel(isFullscreen)}
    </button>,
    shareStage,
  );
}
