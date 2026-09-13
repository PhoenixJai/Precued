import type { RoomRole, SessionFlow, SessionFlowStage } from "../types/precued";

export function activeStage(flow: SessionFlow): SessionFlowStage | null {
  if (flow.currentStageId) {
    const byId = flow.stages.find((stage) => stage.id === flow.currentStageId);
    if (byId) return byId;
  }
  return flow.stages.find((stage) => stage.status === "ACTIVE") ?? null;
}

export function displayStage(flow: SessionFlow): SessionFlowStage | null {
  if (flow.stages.length === 0) return null;
  if (flow.status === "IN_PROGRESS") return activeStage(flow);
  if (flow.status === "COMPLETED") return flow.stages[flow.stages.length - 1] ?? null;
  return flow.stages[0] ?? null;
}

export function stagePositionLabel(flow: SessionFlow): string {
  const stage = displayStage(flow);
  if (!stage || flow.stages.length === 0) return "";
  const index = flow.stages.findIndex((candidate) => candidate.id === stage.id);
  return `Stage ${Math.max(index, 0) + 1} of ${flow.stages.length}`;
}

export function activeStageRoleNames(stage: SessionFlowStage, roles: RoomRole[]): string[] {
  const roleById = new Map(roles.map((role) => [role.id, role.name]));
  return stage.roomRoleIds
    .map((roleId) => roleById.get(roleId))
    .filter((name): name is string => Boolean(name));
}

function formatSeconds(totalSeconds: number): string {
  const safeSeconds = Math.max(0, totalSeconds);
  const minutes = Math.floor(safeSeconds / 60);
  const seconds = safeSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

/**
 * The browser never owns timer state. It derives a display value from the
 * server's startedAt plus the snapshotted duration. Reaching zero is only a
 * visual state; it never triggers advanceSessionFlow().
 */
export function formatStageTimer(stage: SessionFlowStage, nowMs: number): string {
  if (stage.durationSeconds == null) return "Untimed";
  if (!stage.startedAt || stage.status !== "ACTIVE") return formatSeconds(stage.durationSeconds);

  const endsAtMs = Date.parse(stage.startedAt) + stage.durationSeconds * 1000;
  const remainingSeconds = Math.max(0, Math.ceil((endsAtMs - nowMs) / 1000));
  return formatSeconds(remainingSeconds);
}

export function flowControlLabel(flow: SessionFlow): "Start" | "Next" | "Finish" | null {
  if (!flow.enabled || flow.status === "DISABLED" || flow.status === "NOT_CONFIGURED" || flow.status === "COMPLETED") {
    return null;
  }
  if (flow.status === "NOT_STARTED") return "Start";
  if (flow.status !== "IN_PROGRESS") return null;

  const current = activeStage(flow);
  if (!current) return null;
  const currentIndex = flow.stages.findIndex((stage) => stage.id === current.id);
  return currentIndex === flow.stages.length - 1 ? "Finish" : "Next";
}
