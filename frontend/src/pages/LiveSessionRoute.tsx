import { useState } from "react";
import type { CSSProperties } from "react";
import { useParams } from "react-router-dom";
import { SessionFlowCallDock } from "../components/SessionFlowCallDock";
import {
  liveSessionDesktopGridTemplate,
  liveSessionShellPolicy,
} from "../lib/liveSessionUi";
import CallPage from "./CallPage";

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
        className="live-session-panel-toggle"
        aria-expanded={panelsOpen}
        onClick={() => setPanelsOpen((open) => !open)}
      >
        {panelsOpen ? "Hide panels" : "Show panels"}
      </button>

      <CallPage />
    </div>
  );
}