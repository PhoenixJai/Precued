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
  utilityPanel: "collapsible-sidebar";
  utilitySections: ["visibility", "participants"];
  participantPlacement: "bottom-filmstrip";
  collapseControl: "edge-caret";
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
 * a dedicated utility sidebar occupies a predictable fixed-width column.
 */
export function liveSessionDesktopGridTemplate(): string {
  return "minmax(0, 1fr) 360px";
}

/**
 * Active sessions intentionally leave the normal application chrome behind.
 * The bottom row is reserved for participant media, while visibility and
 * participant metadata live in one collapsible sidebar with an edge caret.
 */
export function liveSessionShellPolicy(): LiveSessionShellPolicy {
  return {
    showGlobalNavigation: false,
    showAppRail: false,
    workspaceMode: "full-viewport",
    sessionFlowPlacement: "compact-topbar",
    sessionFlowOwner: "live-session-route",
    utilityPanel: "collapsible-sidebar",
    utilitySections: ["visibility", "participants"],
    participantPlacement: "bottom-filmstrip",
    collapseControl: "edge-caret",
  };
}
