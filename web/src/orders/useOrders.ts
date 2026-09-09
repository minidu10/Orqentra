import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../api/endpoints";
import { useOrderStream } from "../stream/useOrderStream";
import type { ConnectionState, Order, OrderEventMessage } from "../types";

interface Result {
  orders: Order[];
  loading: boolean;
  error: string | null;
  connection: ConnectionState;
  refresh: () => Promise<void>;
}

/**
 * Orders come from HTTP. The stream only patches what HTTP already told us.
 *
 * <p>This ordering is the whole point. The notification service has no database, no
 * replay and loses its routing map on restart, so anything that happened while this
 * client was disconnected is gone for good. A UI that built its state from the stream
 * would be permanently wrong after a single dropped connection, and would look perfectly
 * healthy while being wrong. So the list is authoritative, events are an optimisation
 * that saves polling, and every reconnect triggers a re-fetch to close the gap.
 */
export function useOrders(enabled: boolean): Result {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Lets an event handler ask "do I know this order?" without re-creating callbacks.
  const knownRefs = useRef<Set<string>>(new Set());

  const refresh = useCallback(async () => {
    try {
      const page = await api.orders(0, 50);
      setOrders(page.content);
      knownRefs.current = new Set(page.content.map((order) => order.reference));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load orders");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!enabled) return;
    void refresh();
  }, [enabled, refresh]);

  const handleEvent = useCallback(
    (event: OrderEventMessage, name: string) => {
      // stock.released carries no status; it is informational for an order already
      // cancelled, so there is nothing to patch.
      if (name !== "order.status" || !event.status) return;

      if (!knownRefs.current.has(event.orderReference)) {
        // An order we have never seen: placed in another tab, or created while this
        // client was disconnected. Re-fetch rather than inventing a row from an event
        // that carries only a fragment of an order.
        void refresh();
        return;
      }

      setOrders((current) =>
        current.map((order) =>
          order.reference === event.orderReference
            ? {
                ...order,
                status: event.status!,
                cancellationReason: event.reason ?? order.cancellationReason,
              }
            : order,
        ),
      );
    },
    [refresh],
  );

  const handleConnected = useCallback(() => {
    // Resynchronise on every connect. Events during the gap were not queued anywhere.
    void refresh();
  }, [refresh]);

  const connection = useOrderStream({
    onEvent: handleEvent,
    onConnected: handleConnected,
    enabled,
  });

  return { orders, loading, error, connection, refresh };
}
