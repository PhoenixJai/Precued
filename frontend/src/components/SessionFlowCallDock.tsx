import { useCallback, useEffect, useState } from "react";
import { api } from "../lib/api";
import { getParticipant } from "../lib/session";
import type { RoomRole, SessionFlow } from "../types/precued";
import { SessionFlowPanel } from "./SessionFlowPanel";

const SESSION_FLOW_POLL_MS = 1500;

export function SessionFlowCallDock({ roomId }: { roomId: string }) {
  const me = getParticipant();
  const [flow, setFlow] = useState<SessionFlow | null>(null);
  const [roles, setRoles] = useState<RoomRole[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nowMs, setNowMs] = useState(() => Date.now());

  const refreshFlow = useCallback(async () => {
    try {
      const next = await api.getSessionFlow(roomId);
      setFlow(next);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load Session Flow");
    }
  }, [roomId]);

  useEffect(() => {
    if (!me || me.roomId !== roomId) return;
    let cancelled = false;

    Promise.all([api.getRoomRoles(roomId), api.getSessionFlow(roomId)])
      .then(([nextRoles, nextFlow]) => {
        if (cancelled) return;
        setRoles(nextRoles);
        setFlow(nextFlow);
        setError(null);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Unable to load Session Flow");
      });

    const poll = window.setInterval(() => {
      void refreshFlow();
    }, SESSION_FLOW_POLL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(poll);
    };
  }, [roomId, me?.id, refreshFlow]);

  useEffect(() => {
    if (flow?.status !== "IN_PROGRESS") return;
    setNowMs(Date.now());
    const timer = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [flow?.status, flow?.currentStageId]);

  async function startFlow() {
    if (!me?.isHost || busy) return;
    setBusy(true);
    setError(null);
    try {
      setFlow(await api.startSessionFlow(roomId));
      setNowMs(Date.now());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to start Session Flow");
    } finally {
      setBusy(false);
    }
  }

  async function advanceFlow() {
    if (!me?.isHost || busy) return;
    setBusy(true);
    setError(null);
    try {
      setFlow(await api.advanceSessionFlow(roomId));
      setNowMs(Date.now());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to advance Session Flow");
    } finally {
      setBusy(false);
    }
  }

  if (!me || me.roomId !== roomId) return null;
  if (!error && (!flow || !flow.enabled || flow.status === "DISABLED" || flow.status === "NOT_CONFIGURED")) {
    return null;
  }

  return (
    <div className="session-flow-dock">
      {flow && (
        <SessionFlowPanel
          flow={flow}
          roles={roles}
          isHost={me.isHost}
          busy={busy}
          nowMs={nowMs}
          onStart={() => { void startFlow(); }}
          onAdvance={() => { void advanceFlow(); }}
        />
      )}
      {error && <div className="session-flow-error">{error}</div>}
    </div>
  );
}
