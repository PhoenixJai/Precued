export interface LiveSessionRailItem {
  label: string;
  href: string | null;
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
