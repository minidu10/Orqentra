import { useEffect, useRef, useState } from "react";
import { API_BASE_URL } from "../api/client";
import { getToken } from "../api/auth";
import { readSseStream } from "./sseClient";
import type { ConnectionState, OrderEventMessage } from "../types";

const BASE_RETRY_MS = 1000;
const MAX_RETRY_MS = 30000;

/**
 * Wait this long before opening the socket on mount.
 *
 * React StrictMode mounts, unmounts and remounts every effect in development. Connecting
 * synchronously means the discarded mount has already opened a stream by the time its
 * cleanup runs; aborting it closes the socket on this side, but the server holds the
 * emitter until its next write fails, so the service reports two connections per tab for
 * up to a heartbeat. Letting the mount settle first means the throwaway pass never opens
 * a socket at all. It also collapses a burst of rapid navigations into one connect.
 */
const CONNECT_DELAY_MS = 150;

interface Options {
  /** Called for every order.status and stock.released event. */
  onEvent: (event: OrderEventMessage, name: string) => void;
  /**
   * Called after every successful (re)connect. The caller re-fetches here: events that
   * happened while the socket was down were never queued anywhere, so the only way back
   * to the truth is to ask the API again.
   */
  onConnected: () => void;
  enabled: boolean;
}

export function useOrderStream({ onEvent, onConnected, enabled }: Options): ConnectionState {
  const [state, setState] = useState<ConnectionState>("connecting");

  // Held in refs so a changing callback identity never tears down a live connection.
  const onEventRef = useRef(onEvent);
  const onConnectedRef = useRef(onConnected);
  onEventRef.current = onEvent;
  onConnectedRef.current = onConnected;

  useEffect(() => {
    if (!enabled) {
      setState("disconnected");
      return;
    }

    const controller = new AbortController();
    // StrictMode mounts effects twice in development. Without this flag the first
    // effect's retry timer would resurrect a connection after its cleanup ran, leaving
    // two live streams and duplicate events for the rest of the session.
    let cancelled = false;
    let attempt = 0;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;

    const run = async () => {
      const token = getToken();
      if (!token) {
        setState("disconnected");
        return;
      }

      try {
        setState(attempt === 0 ? "connecting" : "reconnecting");

        await readSseStream(
          `${API_BASE_URL}/api/notifications/stream`,
          token,
          controller.signal,
          {
            onOpen: () => {
              if (cancelled) return;
              attempt = 0;
              setState("connected");
              onConnectedRef.current();
            },
            onMessage: (message) => {
              if (cancelled) return;
              if (message.event !== "order.status" && message.event !== "stock.released") {
                return;
              }
              try {
                onEventRef.current(
                  JSON.parse(message.data) as OrderEventMessage,
                  message.event,
                );
              } catch {
                /* a frame we cannot parse is skipped rather than killing the stream */
              }
            },
          },
        );

        // A clean end still means no more updates, so it is treated as a drop.
        if (!cancelled) scheduleRetry();
      } catch (error) {
        if (cancelled || controller.signal.aborted) return;
        void error;
        scheduleRetry();
      }
    };

    const scheduleRetry = () => {
      if (cancelled) return;
      setState("reconnecting");

      // Exponential backoff with a cap, so a service that stays down is retried
      // every 30 seconds rather than being hammered.
      const delay = Math.min(BASE_RETRY_MS * 2 ** attempt, MAX_RETRY_MS);
      attempt += 1;
      retryTimer = setTimeout(() => {
        if (!cancelled) void run();
      }, delay);
    };

    const openTimer = setTimeout(() => {
      if (!cancelled) void run();
    }, CONNECT_DELAY_MS);

    return () => {
      cancelled = true;
      clearTimeout(openTimer);
      controller.abort();
      if (retryTimer) clearTimeout(retryTimer);
    };
  }, [enabled]);

  return state;
}
