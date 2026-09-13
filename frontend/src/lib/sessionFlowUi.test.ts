import { describe, expect, it } from "vitest";
import type { RoomRole, SessionFlow, SessionFlowStage } from "../types/precued";
import {
  activeStage,
  activeStageRoleNames,
  flowControlLabel,
  formatStageTimer,
  stagePositionLabel,
} from "./sessionFlowUi";

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

describe("Session Flow UI helpers", () => {
  it("finds the active stage and reports its one-based position", () => {
    const stages = [
      stage({ id: "one", sortOrder: 0, status: "COMPLETED" }),
      stage({ id: "two", sortOrder: 1, status: "ACTIVE" }),
      stage({ id: "three", sortOrder: 2, status: "PENDING" }),
    ];
    const current = flow({ status: "IN_PROGRESS", currentStageId: "two", stages });

    expect(activeStage(current)?.id).toBe("two");
    expect(stagePositionLabel(current)).toBe("Stage 2 of 3");
  });

  it("shows the first stage position before the flow starts", () => {
    const current = flow({ stages: [stage(), stage({ id: "two", sortOrder: 1 })] });
    expect(stagePositionLabel(current)).toBe("Stage 1 of 2");
  });

  it("maps active stage role ids to readable role names", () => {
    const currentStage = stage({ roomRoleIds: ["judge-role", "defense-role"] });
    expect(activeStageRoleNames(currentStage, roles)).toEqual(["Judge", "Defense"]);
  });

  it("derives a countdown only from server startedAt + durationSeconds", () => {
    const timed = stage({
      status: "ACTIVE",
      durationSeconds: 120,
      startedAt: "2026-09-13T10:00:00.000Z",
    });

    expect(formatStageTimer(timed, Date.parse("2026-09-13T10:00:30.000Z"))).toBe("1:30");
    expect(formatStageTimer(timed, Date.parse("2026-09-13T10:02:10.000Z"))).toBe("0:00");
  });

  it("labels untimed stages without inventing a countdown", () => {
    expect(formatStageTimer(stage({ durationSeconds: null }), Date.now())).toBe("Untimed");
  });

  it("chooses Start, Next, and Finish controls from runtime state", () => {
    expect(flowControlLabel(flow())).toBe("Start");

    const middle = flow({
      status: "IN_PROGRESS",
      currentStageId: "two",
      stages: [
        stage({ id: "one", sortOrder: 0, status: "COMPLETED" }),
        stage({ id: "two", sortOrder: 1, status: "ACTIVE" }),
        stage({ id: "three", sortOrder: 2, status: "PENDING" }),
      ],
    });
    expect(flowControlLabel(middle)).toBe("Next");

    const final = flow({
      status: "IN_PROGRESS",
      currentStageId: "two",
      stages: [
        stage({ id: "one", sortOrder: 0, status: "COMPLETED" }),
        stage({ id: "two", sortOrder: 1, status: "ACTIVE" }),
      ],
    });
    expect(flowControlLabel(final)).toBe("Finish");

    expect(flowControlLabel(flow({ status: "COMPLETED" }))).toBeNull();
    expect(flowControlLabel(flow({ enabled: false, status: "DISABLED" }))).toBeNull();
  });
});
