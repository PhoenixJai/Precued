export interface LiveSessionRailItem {
  label: string;
  href: string | null;
}

export type LiveSessionMode = "grid" | "share";
export type ParticipantGridLayout = "single" | "two-up" | "three-up" | "quad" | "gallery";
export type SharingSidebarSection = "share-visibility" | "share-actions" | "session-flow";
export type SessionFlowSurface = "topbar" | "sidebar";

export interface LiveSessionShellPolicy {
  showGlobalNavigation: boolean;
  showAppRail: boolean;
  workspaceMode: "full-viewport";
  sessionFlowPlacement: "contextual";
  sessionFlowOwner: "live-session-route";
  utilityPanel: "share-only-collapsible-sidebar";
  utilityDefault: "collapsed";
  participantPlacement: {
    grid: "main-grid";
    share: "bottom-filmstrip";
  };
  fullscreenParticipants: "visible";
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

export function liveSessionMode(hasActiveShare: boolean): LiveSessionMode {
  return hasActiveShare ? "share" : "grid";
}

export function participantGridLayout(participantCount: number): ParticipantGridLayout {
  if (participantCount <= 1) return "single";
  if (participantCount === 2) return "two-up";
  if (participantCount === 3) return "three-up";
  if (participantCount === 4) return "quad";
  return "gallery";
}

/**
 * Host/sharer controls are contextual. Grid mode stays participant-first and
 * guests never receive a host control drawer.
 */
export function shouldShowSharingSidebar(mode: LiveSessionMode, canManageShare: boolean): boolean {
  return mode === "share" && canManageShare;
}

/**
 * Hosts/sharers move Session Flow into their contextual drawer while sharing.
 * Guests keep the compact topbar so session structure remains visible without
 * exposing host controls.
 */
export function sessionFlowSurface(mode: LiveSessionMode, canManageShare: boolean): SessionFlowSurface {
  return mode === "share" && canManageShare ? "sidebar" : "topbar";
}

export function sharingSidebarSections(): SharingSidebarSection[] {
  return ["share-visibility", "share-actions", "session-flow"];
}

/**
 * Desktop share-mode layout: shared content owns the flexible canvas and a
 * dedicated utility sidebar occupies a predictable fixed-width column.
 */
export function liveSessionDesktopGridTemplate(): string {
  return "minmax(0, 1fr) 360px";
}

/**
 * Active sessions are participant-first until somebody shares. Sharing then
 * promotes content to the main stage, moves participants into a bottom
 * filmstrip, and exposes an optional right-side control drawer for the sharer.
 */
export function liveSessionShellPolicy(): LiveSessionShellPolicy {
  return {
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
  };
}
