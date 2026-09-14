export interface AppNavigationItem {
  label: string;
  href?: string;
  icon: string;
  enabled: boolean;
}

export const PRIMARY_NAVIGATION: AppNavigationItem[] = [
  { label: "Templates", href: "/templates", icon: "▧", enabled: true },
  { label: "Sessions", icon: "▣", enabled: false },
  { label: "Help", icon: "ⓘ", enabled: false },
];

export const WORKSPACE_NAVIGATION: AppNavigationItem[] = [
  { label: "Overview", href: "/profile", icon: "⌂", enabled: true },
  { label: "Templates", href: "/templates", icon: "▧", enabled: true },
  { label: "Sessions", icon: "▣", enabled: false },
  { label: "Help", icon: "ⓘ", enabled: false },
];
