import { describe, expect, it } from "vitest";
import {
  availableLiveViewModes,
  defaultLiveViewMode,
  liveSessionDesktopGridTemplate,
  liveSessionMode,
  liveSessionRailItems,
  liveSessionShellPolicy,
  liveSessionStatusCopy,
  normalizeLiveViewMode,
  participantGridLayout,
  participantMediaMode,
  sessionFlowSurface,
  sharingSidebarSections,
  shouldShowSharingSidebar,
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

  it("uses participant grid mode until a share becomes active", () => {
    expect(liveSessionMode(false)).toBe("grid");
    expect(liveSessionMode(true)).toBe("share");
  });

  it("uses Zoom-like participant grid layouts", () => {
    expect(participantGridLayout(1)).toBe("single");
    expect(participantGridLayout(2)).toBe("two-up");
    expect(participantGridLayout(3)).toBe("three-up");
    expect(participantGridLayout(4)).toBe("quad");
    expect(participantGridLayout(5)).toBe("gallery");
    expect(participantGridLayout(9)).toBe("gallery");
  });

  it("makes View available even when the user is alone", () => {
    expect(availableLiveViewModes("grid")).toEqual(["gallery", "speaker"]);
    expect(defaultLiveViewMode("grid")).toBe("gallery");
  });

  it("adds shared-content focus while a share is active", () => {
    expect(availableLiveViewModes("share")).toEqual(["share", "gallery", "speaker"]);
    expect(defaultLiveViewMode("share")).toBe("share");
    expect(normalizeLiveViewMode("share", "grid")).toBe("gallery");
    expect(normalizeLiveViewMode("gallery", "share")).toBe("gallery");
  });

  it("renders an avatar instead of a black video surface when the camera is off", () => {
    expect(participantMediaMode(false, false)).toBe("avatar");
    expect(participantMediaMode(true, true)).toBe("avatar");
    expect(participantMediaMode(true, false)).toBe("video");
  });

  it("reserves a dedicated utility sidebar only for share mode", () => {
    expect(liveSessionDesktopGridTemplate()).toBe("minmax(0, 1fr) 360px");
    expect(shouldShowSharingSidebar("grid", true)).toBe(false);
    expect(shouldShowSharingSidebar("share", false)).toBe(false);
    expect(shouldShowSharingSidebar("share", true)).toBe(true);
  });

  it("moves host Session Flow into the sharing drawer while guests keep context visible", () => {
    expect(sessionFlowSurface("grid", true)).toBe("topbar");
    expect(sessionFlowSurface("grid", false)).toBe("topbar");
    expect(sessionFlowSurface("share", true)).toBe("sidebar");
    expect(sessionFlowSurface("share", false)).toBe("topbar");
  });

  it("keeps the sharing drawer focused on real in-session controls", () => {
    expect(sharingSidebarSections()).toEqual([
      "share-visibility",
      "share-actions",
      "session-flow",
    ]);
  });

  it("treats an active session as a dedicated full-viewport conferencing workspace", () => {
    expect(liveSessionShellPolicy()).toEqual({
      showGlobalNavigation: false,
      showAppRail: false,
      workspaceMode: "full-viewport",
      sessionFlowPlacement: "contextual",
      sessionFlowOwner: "live-session-route",
      utilityPanel: "share-only-collapsible-sidebar",
      utilityDefault: "collapsed",
      participantPlacement: {
        grid: "main-grid",
        share: "bottom-filmstrip",
      },
      fullscreenParticipants: "visible",
      collapseControl: "edge-caret",
    });
  });
});
