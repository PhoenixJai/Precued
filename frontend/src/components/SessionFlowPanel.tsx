import type { RoomRole, SessionFlow } from "../types/precued";
import {
  activeStageRoleNames,
  displayStage,
  flowControlLabel,
  formatStageTimer,
  stagePositionLabel,
} from "../lib/sessionFlowUi";

export function SessionFlowPanel(props: {
  flow: SessionFlow;
  roles: RoomRole[];
  isHost: boolean;
  busy: boolean;
  nowMs: number;
  onStart: () => void;
  onAdvance: () => void;
}) {
  if (!props.flow.enabled || props.flow.status === "DISABLED" || props.flow.status === "NOT_CONFIGURED") {
    return null;
  }

  if (props.flow.status === "COMPLETED") {
    return (
      <section className="session-flow-panel surface-card-lite" aria-live="polite">
        <div className="session-flow-copy">
          <span className="session-flow-eyebrow">SESSION FLOW</span>
          <div className="session-flow-title-row">
            <h2>Session flow complete</h2>
            <span className="session-flow-complete-badge">Completed</span>
          </div>
          <p>{props.flow.stages.length} {props.flow.stages.length === 1 ? "stage" : "stages"} completed.</p>
        </div>
      </section>
    );
  }

  const stage = displayStage(props.flow);
  if (!stage) return null;

  const roleNames = activeStageRoleNames(stage, props.roles);
  const timer = formatStageTimer(stage, props.nowMs);
  const controlLabel = flowControlLabel(props.flow);
  const isReady = props.flow.status === "NOT_STARTED";

  return (
    <section className="session-flow-panel surface-card-lite" aria-live="polite">
      <div className="session-flow-copy">
        <span className="session-flow-eyebrow">SESSION FLOW</span>
        <div className="session-flow-title-row">
          <h2>{stage.name}</h2>
          <span className="session-flow-position">{stagePositionLabel(props.flow)}</span>
        </div>
        <div className="session-flow-meta">
          <span className="session-flow-chip">
            <strong>{isReady ? "Up first" : "Active"}:</strong> {roleNames.length ? roleNames.join(", ") : "All participants"}
          </span>
          <span className={`session-flow-chip session-flow-timer ${timer === "0:00" ? "elapsed" : ""}`}>
            <strong>{isReady ? "Duration" : "Time"}:</strong> {timer}
          </span>
          {timer === "0:00" && !isReady && <span className="session-flow-elapsed-note">Time elapsed · host advances manually</span>}
        </div>
      </div>

      {props.isHost && controlLabel && (
        <button
          type="button"
          className="session-flow-control primary-button compact"
          disabled={props.busy}
          onClick={controlLabel === "Start" ? props.onStart : props.onAdvance}
        >
          {controlLabel}
        </button>
      )}
    </section>
  );
}
