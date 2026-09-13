/** Shared with CallPage/RoomSetupPage's participant avatars and AppShell's account menu. */
export function initials(name: string): string {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("");
}
