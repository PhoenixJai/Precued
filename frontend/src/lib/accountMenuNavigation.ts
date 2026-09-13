export function navigateToProfile(
  navigate: (to: string) => unknown,
  closeMenu: () => void,
) {
  closeMenu();
  navigate("/profile");
}
