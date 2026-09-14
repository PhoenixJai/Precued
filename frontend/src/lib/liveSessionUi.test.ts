import { describe, expect, it } from "vitest";
import {
  liveSessionDesktopGridTemplate,
  liveSessionRailItems,
  liveSessionShellPolicy,
  liveSessionStatusCopy,
} from "./liveSessionUi";

describe("live session shell ui", () => {
  it("uses Sessions terminology in the live workspace rail", () => {
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

  it("reserves a dedicated participant rail instead of overlaying the main workspace", () => {
    expect(liveSessionDesktopGridTemplate()).toBe("minmax(0, 1fr) 320px");
  });

  it("uses one standalone live shell with one inline Session Flow surface", () => {
    expect(liveSessionShellPolicy()).toEqual({
      showGlobalNavigation: false,
      sessionFlowPlacement: "inline",
      sessionFlowOwner: "live-session-route",
    });
  });
});
