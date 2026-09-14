import { Link, useParams } from "react-router-dom";
import { SessionFlowCallDock } from "../components/SessionFlowCallDock";
import { liveSessionRailItems, liveSessionStatusCopy } from "../lib/liveSessionUi";
import CallPage from "./CallPage";

export default function LiveSessionRoute() {
  const { roomId = "" } = useParams();
  const railItems = liveSessionRailItems();

  return (
    <div className="live-session-route">
      <aside className="live-session-rail" aria-label="Live session workspace">
        <div className="live-session-rail-mark" aria-hidden="true">
          <span />
          <span />
          <span />
          <span />
          <span />
        </div>
        <nav className="live-session-rail-nav">
          {railItems.map((item) => item.href ? (
            <Link key={item.label} to={item.href} className="live-session-rail-item">
              <span className="live-session-rail-icon" aria-hidden="true">{item.label.slice(0, 1)}</span>
              <small>{item.label}</small>
            </Link>
          ) : (
            <span
              key={item.label}
              className={`live-session-rail-item ${item.label === "Sessions" ? "active" : "disabled"}`}
              aria-disabled="true"
            >
              <span className="live-session-rail-icon" aria-hidden="true">{item.label.slice(0, 1)}</span>
              <small>{item.label}</small>
            </span>
          ))}
        </nav>
        <div className="live-session-rail-status">
          <span className="live-session-status-dot" aria-hidden="true" />
          <small>{liveSessionStatusCopy(true)}</small>
        </div>
      </aside>

      {roomId && (
        <div className="live-session-flow-layer">
          <SessionFlowCallDock roomId={roomId} />
        </div>
      )}

      <CallPage />
    </div>
  );
}
