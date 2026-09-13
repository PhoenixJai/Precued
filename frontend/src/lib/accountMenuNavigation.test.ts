import { describe, expect, it, vi } from "vitest";
import { navigateToProfile } from "./accountMenuNavigation";

describe("navigateToProfile", () => {
  it("closes the account menu and explicitly navigates to /profile", () => {
    const closeMenu = vi.fn();
    const navigate = vi.fn();

    navigateToProfile(navigate, closeMenu);

    expect(closeMenu).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith("/profile");
  });
});
