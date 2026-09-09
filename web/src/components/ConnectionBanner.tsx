import type { ConnectionState } from "../types";

/**
 * The user is told plainly when updates have stopped. Showing a healthy-looking screen
 * whose badges have silently stopped moving is worse than showing none at all.
 */
export function ConnectionBanner({ state }: { state: ConnectionState }) {
  if (state === "connected") {
    return (
      <p data-testid="connection" data-state={state} className="text-xs text-emerald-700">
        Live updates connected
      </p>
    );
  }

  if (state === "connecting" || state === "reconnecting") {
    return (
      <p data-testid="connection" data-state={state} className="text-xs text-amber-700">
        {state === "connecting" ? "Connecting to live updates…" : "Reconnecting to live updates…"}
      </p>
    );
  }

  return (
    <p data-testid="connection" data-state={state} className="text-xs text-rose-700">
      Live updates disconnected — statuses may be out of date. Reload to refresh.
    </p>
  );
}
