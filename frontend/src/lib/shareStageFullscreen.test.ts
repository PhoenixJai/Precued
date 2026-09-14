import { describe, expect, it, vi } from "vitest";
import { fullscreenButtonLabel, toggleShareStageFullscreen } from "./shareStageFullscreen";

describe("share-stage fullscreen", () => {
  it("uses explicit enter and exit labels", () => {
    expect(fullscreenButtonLabel(false)).toBe("Full screen");
    expect(fullscreenButtonLabel(true)).toBe("Exit full screen");
  });

  it("requests fullscreen for the share stage when it is not already fullscreen", async () => {
    const requestFullscreen = vi.fn(async () => {});
    const exitFullscreen = vi.fn(async () => {});
    const element = { requestFullscreen } as unknown as HTMLElement;
    const documentRef = { fullscreenElement: null, exitFullscreen } as unknown as Document;

    expect(await toggleShareStageFullscreen(element, documentRef)).toBe(true);
    expect(requestFullscreen).toHaveBeenCalledTimes(1);
    expect(exitFullscreen).not.toHaveBeenCalled();
  });

  it("exits fullscreen when the share stage already owns fullscreen", async () => {
    const requestFullscreen = vi.fn(async () => {});
    const exitFullscreen = vi.fn(async () => {});
    const element = { requestFullscreen } as unknown as HTMLElement;
    const documentRef = { fullscreenElement: element, exitFullscreen } as unknown as Document;

    expect(await toggleShareStageFullscreen(element, documentRef)).toBe(false);
    expect(exitFullscreen).toHaveBeenCalledTimes(1);
    expect(requestFullscreen).not.toHaveBeenCalled();
  });
});
