import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useOrders } from "./useOrders";
import type { Order, OrderEventMessage } from "../types";

const ordersApiMock = vi.fn();
vi.mock("../api/endpoints", () => ({
  api: {
    orders: (...args: unknown[]) => ordersApiMock(...args),
  },
}));

// The stream itself (fetch, SSE parsing, reconnect/backoff) is tested separately in
// sseClient.test.ts and is not this hook's concern. Here, useOrderStream is replaced with
// a controllable stand-in so the test can fire onEvent/onConnected directly and assert
// only on how useOrders reacts to them.
let capturedOnEvent: (event: OrderEventMessage, name: string) => void = () => {};
let capturedOnConnected: () => void = () => {};

vi.mock("../stream/useOrderStream", () => ({
  useOrderStream: (options: {
    onEvent: (event: OrderEventMessage, name: string) => void;
    onConnected: () => void;
  }) => {
    capturedOnEvent = options.onEvent;
    capturedOnConnected = options.onConnected;
    return "connected";
  },
}));

function order(reference: string, status: Order["status"] = "PENDING"): Order {
  return {
    reference,
    restaurantId: "r1",
    status,
    total: 100,
    cancellationReason: null,
    createdAt: "2026-01-01T00:00:00Z",
    items: [{ sku: "SKU-001", quantity: 1, unitPrice: 100 }],
  };
}

function page(orders: Order[]) {
  return {
    content: orders,
    number: 0,
    size: 50,
    totalElements: orders.length,
    totalPages: 1,
    first: true,
    last: true,
    empty: orders.length === 0,
  };
}

describe("useOrders", () => {
  beforeEach(() => {
    ordersApiMock.mockReset();
  });

  it("loads the orders list from HTTP on mount and treats it as authoritative", async () => {
    ordersApiMock.mockResolvedValue(page([order("A")]));

    const { result } = renderHook(() => useOrders(true));

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(ordersApiMock).toHaveBeenCalledTimes(1);
    expect(result.current.orders).toEqual([order("A")]);
  });

  it("patches an existing order's status from a stream event, without re-fetching", async () => {
    ordersApiMock.mockResolvedValue(page([order("A", "PENDING")]));
    const { result } = renderHook(() => useOrders(true));
    await waitFor(() => expect(result.current.loading).toBe(false));

    ordersApiMock.mockClear();

    act(() => {
      capturedOnEvent(
        {
          orderReference: "A",
          status: "CONFIRMED",
          message: "Payment accepted",
          reason: null,
          timestamp: "2026-01-01T00:00:01Z",
        },
        "order.status",
      );
    });

    await waitFor(() => expect(result.current.orders[0].status).toBe("CONFIRMED"));
    // The whole point: a known order's event patches state in place. It must not trigger
    // another HTTP call, or the "no polling" property this hook exists for would not hold.
    expect(ordersApiMock).not.toHaveBeenCalled();
  });

  it("re-fetches on every reconnect, since events during the gap were never queued", async () => {
    ordersApiMock.mockResolvedValue(page([order("A")]));
    const { result } = renderHook(() => useOrders(true));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(ordersApiMock).toHaveBeenCalledTimes(1);

    act(() => {
      capturedOnConnected();
    });

    await waitFor(() => expect(ordersApiMock).toHaveBeenCalledTimes(2));
  });

  it("re-fetches, rather than inventing a row, for an event on an order it has not seen", async () => {
    ordersApiMock.mockResolvedValueOnce(page([order("A")]));
    const { result } = renderHook(() => useOrders(true));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.orders).toHaveLength(1);

    // A second order that this client never fetched: placed in another tab, or created
    // while this client was disconnected.
    ordersApiMock.mockResolvedValueOnce(page([order("A"), order("B", "CONFIRMED")]));

    act(() => {
      capturedOnEvent(
        {
          orderReference: "B",
          status: "CONFIRMED",
          message: "Order received",
          reason: null,
          timestamp: "2026-01-01T00:00:01Z",
        },
        "order.status",
      );
    });

    await waitFor(() => expect(result.current.orders).toHaveLength(2));
    expect(result.current.orders.map((o) => o.reference)).toEqual(["A", "B"]);
  });
});
