export interface TemplateFlowStageDraft {
  id: string | null;
  stageKey: string;
  name: string;
  durationSeconds: number | null;
  templateRoleIds: string[];
}

export function cloneStageDraft(stage: TemplateFlowStageDraft): TemplateFlowStageDraft {
  return {
    ...stage,
    templateRoleIds: [...stage.templateRoleIds],
  };
}

export function restoreStageDraft(
  stages: TemplateFlowStageDraft[],
  index: number,
  original: TemplateFlowStageDraft,
): TemplateFlowStageDraft[] {
  return stages.map((stage, stageIndex) => stageIndex === index ? cloneStageDraft(original) : stage);
}

export function nextStageKey(name: string, existingKeys: string[]): string {
  const normalized = name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "") || "stage";
  const used = new Set(existingKeys);
  if (!used.has(normalized)) return normalized;
  let suffix = 2;
  while (used.has(`${normalized}_${suffix}`)) suffix += 1;
  return `${normalized}_${suffix}`;
}

export function moveStage<T>(stages: T[], fromIndex: number, toIndex: number): T[] {
  if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= stages.length || toIndex >= stages.length) {
    return [...stages];
  }
  const next = [...stages];
  const [moved] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, moved);
  return next;
}

export function validateTemplateFlowDraft(stages: TemplateFlowStageDraft[]): string | null {
  const keys = new Set<string>();
  for (const stage of stages) {
    if (!stage.name.trim()) return "Every Session Flow stage needs a name.";
    if (!stage.stageKey.trim()) return "Every Session Flow stage needs a stable key.";
    if (keys.has(stage.stageKey)) return `Stage key ${stage.stageKey} is duplicated.`;
    keys.add(stage.stageKey);
    if (stage.durationSeconds !== null && (!Number.isInteger(stage.durationSeconds) || stage.durationSeconds <= 0)) {
      return "Each stage timer must be a positive whole number of seconds, or left blank for untimed.";
    }
    if (stage.templateRoleIds.length === 0) {
      return `Choose at least one active role for ${stage.name || "each stage"}.`;
    }
  }
  return null;
}
