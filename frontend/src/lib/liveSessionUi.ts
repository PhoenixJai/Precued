export interface LiveSessionRailItem {
  label: string;
  href: string | null;
}

export interface LiveSessionShellPolicy {
  showGlobalNavigation: boolean;
  sessionFlowPlacement: "inline";
  sessionFlowOwner: "live-session-route";
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
 * Desktop live-session layout contract: the main workspace is allowed to
 * shrink while the participant rail always owns its own dedicated column.
 */
export function liveSessionDesktopGridTemplate(): string {
  return "minmax(0, 1fr) 320px";
}

/**
 * The live route owns its own chrome and Session Flow surface. This prevents
 * the global AppShell header and its historical call-flow injection from
 * rendering a second navigation shell or a duplicate flow card.
 */
export function liveSessionShellPolicy(): LiveSessionShellPolicy {
  return {
    showGlobalNavigation: false,
    sessionFlowPlacement: "inline",
    sessionFlowOwner: "live-session-route",
  };
}
