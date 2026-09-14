import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { sessionFlowToggleText } from "../lib/sessionFlowToggle";
import { formatStageDuration } from "../lib/templateBuilderUi";
import {
  cloneStageDraft,
  moveStage,
  nextStageKey,
  restoreStageDraft,
  validateTemplateFlowDraft,
  type TemplateFlowStageDraft,
} from "../lib/templateSessionFlowBuilder";
import type { TemplateRoleDefinition, TemplateSessionFlowDefinition } from "../types/precued";

export function TemplateSessionFlowBuilder(props: {
  templateId: string;
  authSessionToken: string;
  roles: TemplateRoleDefinition[];
  rolesLoaded: boolean;
}) {
  const [enabled, setEnabled] = useState(false);
  const [stages, setStages] = useState<TemplateFlowStageDraft[]>([]);
  const [newStageName, setNewStageName] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedNotice, setSavedNotice] = useState(false);
  const [editingStageIndex, setEditingStageIndex] = useState<number | null>(null);
  const [editingStageOriginal, setEditingStageOriginal] = useState<TemplateFlowStageDraft | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoaded(false);
    api.getTemplateSessionFlow(props.templateId, props.authSessionToken)
      .then((flow) => {
        if (cancelled) return;
        applyServerFlow(flow);
        setLoaded(true);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : "Unable to load Session Structure");
        setLoaded(true);
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [props.templateId, props.authSessionToken]);

  useEffect(() => {
    if (!loaded || !props.rolesLoaded) return;
    const validRoleIds = new Set(props.roles.map((role) => role.id));
    setStages((current) => current.map((stage) => ({
      ...stage,
      templateRoleIds: stage.templateRoleIds.filter((roleId) => validRoleIds.has(roleId)),
    })));
  }, [loaded, props.rolesLoaded, props.roles]);

  function applyServerFlow(flow: TemplateSessionFlowDefinition) {
    setEnabled(flow.enabled);
    setStages(flow.stages
      .slice()
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((stage) => ({
        id: stage.id,
        stageKey: stage.stageKey,
        name: stage.name,
        durationSeconds: stage.durationSeconds,
        templateRoleIds: [...stage.templateRoleIds],
      })));
    setEditingStageIndex(null);
    setEditingStageOriginal(null);
  }

  function addStage() {
    const name = newStageName.trim();
    if (!name) return;
    setStages((current) => [
      ...current,
      {
        id: null,
        stageKey: nextStageKey(name, current.map((stage) => stage.stageKey)),
        name,
        durationSeconds: null,
        templateRoleIds: [],
      },
    ]);
    setNewStageName("");
    setSavedNotice(false);
  }

  function updateStage(index: number, patch: Partial<TemplateFlowStageDraft>) {
    setStages((current) => current.map((stage, stageIndex) => stageIndex === index ? { ...stage, ...patch } : stage));
    setSavedNotice(false);
  }

  function toggleStageRole(index: number, roleId: string) {
    const stage = stages[index];
    const hasRole = stage.templateRoleIds.includes(roleId);
    updateStage(index, {
      templateRoleIds: hasRole
        ? stage.templateRoleIds.filter((id) => id !== roleId)
        : [...stage.templateRoleIds, roleId],
    });
  }

  function beginStageEdit(index: number) {
    setEditingStageIndex(index);
    setEditingStageOriginal(cloneStageDraft(stages[index]));
    setError(null);
  }

  function finishStageEdit() {
    setEditingStageIndex(null);
    setEditingStageOriginal(null);
    setSavedNotice(false);
  }

  function cancelStageEdit() {
    if (editingStageIndex !== null && editingStageOriginal) {
      setStages((current) => restoreStageDraft(current, editingStageIndex, editingStageOriginal));
    }
    setEditingStageIndex(null);
    setEditingStageOriginal(null);
    setSavedNotice(false);
  }

  async function saveFlow() {
    const validationError = validateTemplateFlowDraft(stages);
    if (validationError) {
      setError(validationError);
      return;
    }
    setSaving(true);
    setError(null);
    setSavedNotice(false);
    try {
      const saved = await api.saveTemplateSessionFlow(
        props.templateId,
        {
          enabled,
          stages: stages.map((stage) => ({
            id: stage.id,
            stageKey: stage.stageKey,
            name: stage.name.trim(),
            durationSeconds: stage.durationSeconds,
            templateRoleIds: stage.templateRoleIds,
          })),
        },
        props.authSessionToken,
      );
      applyServerFlow(saved);
      setSavedNotice(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to save Session Structure");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="surface-card session-flow-builder-card agenda-builder-card">
      <div className="session-flow-builder-header agenda-builder-header">
        <div>
          <span className="builder-card-kicker">AGENDA</span>
          <h2>Session Structure</h2>
          <p>Order the stages, set timing, and choose which roles are active in each stage.</p>
        </div>
        <label className="flow-switch-control">
          <input
            className="flow-switch-input"
            type="checkbox"
            role="switch"
            aria-label="Turn Session Structure on or off"
            aria-checked={enabled}
            checked={enabled}
            disabled={!loaded || saving}
            onChange={(event) => { setEnabled(event.target.checked); setSavedNotice(false); }}
          />
          <span className="flow-switch-track" aria-hidden="true">
            <span className="flow-switch-thumb" />
          </span>
          <span className="flow-switch-state">{sessionFlowToggleText(enabled)}</span>
        </label>
      </div>

      <p className={`flow-builder-note${enabled ? "" : " flow-builder-note-warning"}`}>
        {enabled
          ? "New sessions snapshot this structure when they are created. Existing sessions keep their original snapshot."
          : "Stages stay saved while Session Structure is off, but they will not appear in live sessions. Turn it on and save before creating a new session."}
      </p>

      {!loaded ? (
        <p className="empty-state-note">Loading Session Structure…</p>
      ) : (
        <>
          <div className="flow-stage-list agenda-stage-list">
            {stages.map((stage, index) => {
              const isSavedStage = stage.id !== null;
              const isEditing = !isSavedStage || editingStageIndex === index;
              const anotherStageIsEditing = editingStageIndex !== null && editingStageIndex !== index;
              const activeRoles = props.roles.filter((role) => stage.templateRoleIds.includes(role.id));

              return (
                <article className={`flow-stage-editor agenda-stage-card${isSavedStage && !isEditing ? " flow-stage-editor-readonly" : ""}`} key={stage.id ?? stage.stageKey}>
                  <div className="agenda-stage-number" aria-hidden="true">{index + 1}</div>
                  <div className="agenda-stage-content">
                    <div className="flow-stage-editor-top agenda-stage-top">
                      <div>
                        <div className="agenda-stage-title-row">
                          <strong>{stage.name || `Stage ${index + 1}`}</strong>
                          <span className="agenda-duration-pill">{formatStageDuration(stage.durationSeconds)}</span>
                        </div>
                        <small>{stage.stageKey}</small>
                        {!isEditing && (
                          <div className="agenda-stage-role-summary">
                            {activeRoles.length === 0
                              ? <span className="agenda-no-roles">No active roles assigned</span>
                              : activeRoles.map((role) => <span key={role.id}>{role.name}{role.isHostRole ? " · Host" : ""}</span>)}
                          </div>
                        )}
                      </div>
                      <div className="flow-stage-order-actions">
                        {isSavedStage && !isEditing && (
                          <button
                            className="secondary-button compact flow-edit-stage"
                            disabled={saving || editingStageIndex !== null}
                            onClick={() => beginStageEdit(index)}
                          >Edit</button>
                        )}
                        {isSavedStage && editingStageIndex === index && (
                          <>
                            <button className="secondary-button compact" disabled={saving} onClick={finishStageEdit}>Done editing</button>
                            <button className="text-button" disabled={saving} onClick={cancelStageEdit}>Cancel</button>
                          </>
                        )}
                        <button
                          className="secondary-button compact agenda-order-button"
                          aria-label={`Move ${stage.name || `stage ${index + 1}`} up`}
                          disabled={saving || anotherStageIsEditing || editingStageIndex === index || index === 0}
                          onClick={() => { setStages(moveStage(stages, index, index - 1)); setSavedNotice(false); }}
                        >↑</button>
                        <button
                          className="secondary-button compact agenda-order-button"
                          aria-label={`Move ${stage.name || `stage ${index + 1}`} down`}
                          disabled={saving || anotherStageIsEditing || editingStageIndex === index || index === stages.length - 1}
                          onClick={() => { setStages(moveStage(stages, index, index + 1)); setSavedNotice(false); }}
                        >↓</button>
                        <button
                          className="text-button flow-remove-stage"
                          disabled={saving || editingStageIndex !== null}
                          onClick={() => { setStages(stages.filter((_, stageIndex) => stageIndex !== index)); setSavedNotice(false); }}
                        >Remove</button>
                      </div>
                    </div>

                    {isSavedStage && editingStageIndex === index && (
                      <p className="flow-editing-note">Editing this stage. “Done editing” keeps the draft on screen; “Save structure” persists it.</p>
                    )}

                    {isEditing && (
                      <>
                        <div className="flow-stage-fields">
                          <label>
                            Stage name
                            <input
                              value={stage.name}
                              disabled={saving}
                              onChange={(event) => updateStage(index, { name: event.target.value })}
                            />
                          </label>
                          <label>
                            Duration in seconds <span>(optional)</span>
                            <input
                              type="number"
                              min={1}
                              step={1}
                              placeholder="Untimed"
                              disabled={saving}
                              value={stage.durationSeconds ?? ""}
                              onChange={(event) => updateStage(index, {
                                durationSeconds: event.target.value === "" ? null : Number(event.target.value),
                              })}
                            />
                          </label>
                        </div>

                        <div className="flow-role-picker">
                          <strong>Active roles</strong>
                          {props.roles.length === 0 ? (
                            <small>Add template roles before assigning them to a stage.</small>
                          ) : (
                            <div className="flow-role-options">
                              {props.roles.map((role) => (
                                <label key={role.id} className="flow-role-option">
                                  <input
                                    type="checkbox"
                                    disabled={saving}
                                    checked={stage.templateRoleIds.includes(role.id)}
                                    onChange={() => toggleStageRole(index, role.id)}
                                  />
                                  <span>{role.name}{role.isHostRole ? " · Host" : ""}</span>
                                </label>
                              ))}
                            </div>
                          )}
                        </div>
                      </>
                    )}
                  </div>
                </article>
              );
            })}
          </div>

          <div className="flow-add-stage-row agenda-add-stage-row">
            <input
              value={newStageName}
              disabled={saving || editingStageIndex !== null}
              placeholder="Add a stage, e.g. Candidate Questions"
              aria-label="New stage name"
              onChange={(event) => setNewStageName(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  addStage();
                }
              }}
            />
            <button className="secondary-button" disabled={saving || editingStageIndex !== null || !newStageName.trim()} onClick={addStage}>＋ Add stage</button>
          </div>

          {stages.length === 0 && (
            <div className="builder-empty-panel flow-empty-note agenda-empty-state">
              <strong>No stages yet.</strong>
              <p>Start with the first meaningful step in this session. You can reorder stages at any time.</p>
            </div>
          )}

          <div className="flow-save-row agenda-save-row">
            <div>
              {savedNotice && <span className="flow-saved-notice">✓ Structure saved</span>}
            </div>
            <button className="primary-button" disabled={saving} onClick={() => { void saveFlow(); }}>
              {saving ? "Saving…" : "Save structure"}
            </button>
          </div>
        </>
      )}

      {error && <div className="error-banner">{error}</div>}
    </section>
  );
}
