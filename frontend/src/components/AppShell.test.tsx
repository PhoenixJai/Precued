import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { Brand } from "./AppShell";

describe("Brand", () => {
  it("renders the modular P mark instead of a letter in a rounded square", () => {
    const html = renderToStaticMarkup(<Brand />);
    expect(html).toContain("brand-mark-grid");
    expect((html.match(/brand-mark-cell/g) ?? []).length).toBe(5);
    expect(html).toContain("Precued");
  });
});
