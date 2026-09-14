export function fullscreenButtonLabel(isFullscreen: boolean): "Full screen" | "Exit full screen" {
  return isFullscreen ? "Exit full screen" : "Full screen";
}

/**
 * Toggle fullscreen for the shared-content stage. Returns the expected
 * fullscreen state after the browser completes the requested transition.
 */
export async function toggleShareStageFullscreen(
  element: HTMLElement,
  documentRef: Document = document,
): Promise<boolean> {
  if (documentRef.fullscreenElement === element) {
    await documentRef.exitFullscreen();
    return false;
  }

  await element.requestFullscreen();
  return true;
}
