export interface LiveSessionRailItem {
  label: string;
  href: string | null;
}

export type LiveSessionMode = "grid" | "share";
export type ParticipantGridLayout = "single" | "two-up" | "three-up" | "quad" | "gallery";
export type LiveViewMode = "gallery" | "speaker" | "share";
export type ParticipantMediaMode = "video" | "avatar";
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

/** View is always available. Shared-content focus becomes an additional option
 * only while somebody is actively presenting. */
export function availableLiveViewModes(mode: LiveSessionMode): LiveViewMode[] {
  return mode === "share" ? ["share", "gallery", "speaker"] : ["gallery", "speaker"];
}

export function defaultLiveViewMode(mode: LiveSessionMode): LiveViewMode {
  return mode === "share" ? "share" : "gallery";
}

/** Keep a user-selected view when it still exists in the current session mode.
 * Ending a share while in share-focus falls back to gallery. */
export function normalizeLiveViewMode(viewMode: LiveViewMode, mode: LiveSessionMode): LiveViewMode {
  return availableLiveViewModes(mode).includes(viewMode) ? viewMode : defaultLiveViewMode(mode);
}

/** A muted LiveKit camera publication can still leave a video element mounted.
 * Treat that as camera-off so the UI shows an intentional avatar state instead
 * of a black video rectangle. */
export function participantMediaMode(hasCameraTrack: boolean, cameraMuted: boolean): ParticipantMediaMode {
  return hasCameraTrack && !cameraMuted ? "video" : "avatar";
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
