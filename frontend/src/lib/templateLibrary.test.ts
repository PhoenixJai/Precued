import { describe, expect, it } from "vitest";
import type { TemplateSummary } from "../types/precued";
import {
  PUBLIC_TEMPLATE_CARDS,
  filterLibraryCards,
  privateTemplateSummaries,
} from "./templateLibrary";

describe("template library product rules", () => {
  it("exposes exactly the three built-in Precued templates as public", () => {
    expect(PUBLIC_TEMPLATE_CARDS.map((template) => template.id)).toEqual([
      "sales_call",
      "mock_trial",
      "ld_debate",
    ]);
    expect(PUBLIC_TEMPLATE_CARDS.every((template) => template.visibility === "PUBLIC")).toBe(true);
  });

  it("keeps only user-created templates in the private collection", () => {
    const templates: TemplateSummary[] = [
      { id: "sales_call", name: "Sales Call", isCustom: false, createdAt: "2026-09-01T00:00:00Z" },
      { id: "custom-1", name: "Panel Interview", isCustom: true, createdAt: "2026-09-13T00:00:00Z" },
      { id: "mock_trial", name: "Mock Trial", isCustom: false, createdAt: "2026-09-01T00:00:00Z" },
      { id: "custom-2", name: "Negotiation Lab", isCustom: true, createdAt: "2026-09-14T00:00:00Z" },
    ];

    expect(privateTemplateSummaries(templates).map((template) => template.id)).toEqual(["custom-1", "custom-2"]);
  });

  it("searches public templates by title, description, and role", () => {
    expect(filterLibraryCards(PUBLIC_TEMPLATE_CARDS, "trial").map((template) => template.id)).toEqual(["mock_trial"]);
    expect(filterLibraryCards(PUBLIC_TEMPLATE_CARDS, "client").map((template) => template.id)).toEqual(["sales_call"]);
    expect(filterLibraryCards(PUBLIC_TEMPLATE_CARDS, "audience").map((template) => template.id)).toEqual(["ld_debate"]);
  });

  it("returns the full collection for a blank search", () => {
    expect(filterLibraryCards(PUBLIC_TEMPLATE_CARDS, "   ")).toEqual(PUBLIC_TEMPLATE_CARDS);
  });
});
