import { describe, expect, it } from "vitest";
import {
  liveSessionDesktopGridTemplate,
  liveSessionRailItems,
  liveSessionShellPolicy,
  liveSessionStatusCopy,
} from "./liveSessionUi";

describe("live session shell ui", () => {
  it("keeps Sessions terminology available for non-live workspace navigation", () => {
    expect(liveSessionRailItems()).toEqual([
      { label: "Home", href: "/profile" },
      { label: "Templates", href: "/templates" },
      { label: "Sessions", href: null },
      { label: "Settings", href: null },
    ]);
  });

  it("keeps the live state concise and product-facing", () => {
    expect(liveSessionStatusCopy(true)).toBe("Live");
    expect(liveSessionStatusCopy(false)).toBe("Connecting");
  });

  it("reserves a roomy utility panel while keeping the shared-content canvas flexible", () => {
    expect(liveSessionDesktopGridTemplate()).toBe("minmax(0, 1fr) minmax(340px, 400px)");
  });

  it("treats an active session as a dedicated full-viewport conferencing workspace", () => {
    expect(liveSessionShellPolicy()).toEqual({
      showGlobalNavigation: false,
      showAppRail: false,
      workspaceMode: "full-viewport",
      sessionFlowPlacement: "compact-topbar",
      sessionFlowOwner: "live-session-route",
      utilityPanel: "collapsible-right",
    });
  });
});