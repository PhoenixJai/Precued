import type { TemplateSummary } from "../types/precued";

export type TemplateLibraryVisibility = "PUBLIC" | "PRIVATE";

export interface LibraryTemplateCard {
  id: string;
  title: string;
  description: string;
  roles: string[];
  visibility: TemplateLibraryVisibility;
  icon: string;
  badge?: string;
  context: string;
  highlights: string[];
}

/**
 * Product rule for the current release: only Precued's three seeded templates
 * are public. User-created templates remain private to their creator.
 */
export const PUBLIC_TEMPLATE_CARDS: LibraryTemplateCard[] = [
  {
    id: "sales_call",
    title: "Sales Call",
    description: "Keep internal collaboration useful without exposing the wrong content to clients.",
    roles: ["Sales Rep", "Sales Engineer", "Client"],
    visibility: "PUBLIC",
    icon: "▤",
    badge: "POPULAR",
    context: "Client conversations",
    highlights: ["3 defined roles", "Role-aware sharing", "Precued built-in"],
  },
  {
    id: "mock_trial",
    title: "Mock Trial",
    description: "Run a structured trial simulation with clear roles, exhibits, and controlled visibility.",
    roles: ["Judge", "Jury", "Defense", "Prosecution"],
    visibility: "PUBLIC",
    icon: "⚖",
    context: "Legal simulation",
    highlights: ["4 defined roles", "Session flow included", "Precued built-in"],
  },
  {
    id: "ld_debate",
    title: "Lincoln-Douglas Debate",
    description: "Facilitate academic debate with distinct sides, a judge, and a role-aware audience.",
    roles: ["Judge", "Affirmative", "Negative", "Audience"],
    visibility: "PUBLIC",
    icon: "◫",
    context: "Academic debate",
    highlights: ["4 defined roles", "Role-aware sharing", "Precued built-in"],
  },
];

export function privateTemplateSummaries(templates: TemplateSummary[]): TemplateSummary[] {
  return templates.filter((template) => template.isCustom);
}

export function filterLibraryCards<T extends Pick<LibraryTemplateCard, "title" | "description" | "roles">>(
  cards: T[],
  query: string,
): T[] {
  const normalized = query.trim().toLocaleLowerCase();
  if (!normalized) return cards;

  return cards.filter((card) => {
    const searchable = [card.title, card.description, ...card.roles].join(" ").toLocaleLowerCase();
    return searchable.includes(normalized);
  });
}
