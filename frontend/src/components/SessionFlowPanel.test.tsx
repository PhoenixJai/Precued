import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { RoomRole, SessionFlow, SessionFlowStage } from "../types/precued";
import { SessionFlowPanel } from "./SessionFlowPanel";

const roles: RoomRole[] = [
  { id: "judge-role", roomId: "room-1", roleKey: "judge", name: "Judge", isHostRole: true, isGuestRole: false, maxMembers: 1 },
  { id: "jury-role", roomId: "room-1", roleKey: "jury", name: "Jury", isHostRole: false, isGuestRole: false, maxMembers: null },
  { id: "defense-role", roomId: "room-1", roleKey: "defense", name: "Defense", isHostRole: false, isGuestRole: false, maxMembers: null },
];

function stage(overrides: Partial<SessionFlowStage> = {}): SessionFlowStage {
  return {
    id: "stage-1",
    stageKey: "opening",
    name: "Opening",
    sortOrder: 0,
    durationSeconds: 120,
    status: "PENDING",
    startedAt: null,
    completedAt: null,
    roomRoleIds: ["judge-role"],
    ...overrides,
  };
}

function flow(overrides: Partial<SessionFlow> = {}): SessionFlow {
  return {
    roomId: "room-1",
    enabled: true,
    status: "NOT_STARTED",
    currentStageId: null,
    stages: [stage()],
    ...overrides,
  };
}

function render(flowValue: SessionFlow, isHost: boolean, nowMs = Date.parse("2026-09-13T10:01:00Z")) {
  return renderToStaticMarkup(
    <SessionFlowPanel
      flow={flowValue}
      roles={roles}
      isHost={isHost}
      busy={false}
      nowMs={nowMs}
      onStart={vi.fn()}
      onAdvance={vi.fn()}
    />,
  );
}

describe("SessionFlowPanel", () => {
  it("shows stage, position, active roles, timer, and Next to a host", () => {
    const current = flow({
      status: "IN_PROGRESS",
      currentStageId: "stage-2",
      stages: [
        stage({ id: "stage-1", status: "COMPLETED", completedAt: "2026-09-13T10:00:00Z" }),
        stage({
          id: "stage-2",
          stageKey: "evidence_review",
          name: "Evidence Review",
          sortOrder: 1,
          status: "ACTIVE",
          durationSeconds: 120,
          startedAt: "2026-09-13T10:00:30Z",
          roomRoleIds: ["judge-role", "defense-role"],
        }),
        stage({ id: "stage-3", sortOrder: 2 }),
      ],
    });

    const html = render(current, true);
    expect(html).toContain("Evidence Review");
    expect(html).toContain("Stage 2 of 3");
    expect(html).toContain("Judge");
    expect(html).toContain("Defense");
    expect(html).toContain("1:30");
    expect(html).toContain(">Next<");
  });

  it("shows Start before the flow begins only to the host", () => {
    expect(render(flow(), true)).toContain(">Start<");
    expect(render(flow(), false)).not.toContain(">Start<");
  });

  it("changes the final active-stage control to Finish", () => {
    const current = flow({
      status: "IN_PROGRESS",
      currentStageId: "stage-2",
      stages: [
        stage({ id: "stage-1", status: "COMPLETED" }),
        stage({ id: "stage-2", sortOrder: 1, status: "ACTIVE", durationSeconds: null, startedAt: "2026-09-13T10:00:00Z" }),
      ],
    });

    const html = render(current, true);
    expect(html).toContain("Untimed");
    expect(html).toContain(">Finish<");
    expect(html).not.toContain(">Next<");
  });

  it("keeps participant views read-only", () => {
    const current = flow({ status: "IN_PROGRESS", currentStageId: "stage-1", stages: [stage({ status: "ACTIVE", startedAt: "2026-09-13T10:00:00Z" })] });
    const html = render(current, false);
    expect(html).toContain("Opening");
    expect(html).not.toContain("session-flow-control");
  });

  it("shows completion without any host control", () => {
    const current = flow({ status: "COMPLETED", currentStageId: null, stages: [stage({ status: "COMPLETED" })] });
    const html = render(current, true);
    expect(html).toContain("Session flow complete");
    expect(html).not.toContain("session-flow-control");
  });

  it("renders nothing for disabled or unconfigured flow", () => {
    expect(render(flow({ enabled: false, status: "DISABLED" }), true)).toBe("");
    expect(render(flow({ status: "NOT_CONFIGURED", stages: [] }), true)).toBe("");
  });
});
