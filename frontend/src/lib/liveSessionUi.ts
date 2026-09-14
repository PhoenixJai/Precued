export interface LiveSessionRailItem {
  label: string;
  href: string | null;
}

export interface LiveSessionShellPolicy {
  showGlobalNavigation: boolean;
  showAppRail: boolean;
  workspaceMode: "full-viewport";
  sessionFlowPlacement: "compact-topbar";
  sessionFlowOwner: "live-session-route";
  utilityPanel: "collapsible-right";
}

export function liveSessionRailItems(): LiveSessionRailItem[] {
  return [
    { label: "Home", href: "/profile" },
    { label: "Templates", href: "/templates" },
    { label: "Sessions", href: null },
    { label: "Settings", href: null },
  ];
}

export function liveSessionStatusCopy(connected: boolean): "Live" | "Connecting" {
  return connected ? "Live" : "Connecting";
}

/**
 * Desktop active-session layout: shared content owns the flexible canvas and
 * visibility/participants live in a roomy utility column. The route can
 * collapse that utility column so screen shares and presentations become
 * effectively full width without changing any media or visibility state.
 */
export function liveSessionDesktopGridTemplate(): string {
  return "minmax(0, 1fr) minmax(340px, 400px)";
}

/**
 * Active sessions intentionally leave the normal application chrome behind.
 * They behave like a conferencing workspace: full viewport, compact stage
 * context, and an optional right-side utility panel.
 */
export function liveSessionShellPolicy(): LiveSessionShellPolicy {
  return {
    showGlobalNavigation: false,
    showAppRail: false,
    workspaceMode: "full-viewport",
    sessionFlowPlacement: "compact-topbar",
    sessionFlowOwner: "live-session-route",
    utilityPanel: "collapsible-right",
  };
}