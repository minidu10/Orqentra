import { useSession } from "../session";
import { useOrders } from "../orders/useOrders";
import { StatusBadge } from "../components/StatusBadge";
import { ConnectionBanner } from "../components/ConnectionBanner";
import { money, relativeTime } from "../components/Money";

export function OrdersPage() {
  const { token } = useSession();
  const { orders, loading, error, connection, refresh } = useOrders(Boolean(token));

  return (
    <div>
      <div className="flex items-baseline gap-4">
        <h1 className="text-base font-semibold">Orders</h1>
        <ConnectionBanner state={connection} />
        <button
          onClick={() => void refresh()}
          className="ml-auto rounded border border-stone-300 px-2 py-1 text-xs text-stone-700 hover:bg-stone-100"
        >
          Refresh
        </button>
      </div>

      {loading && <p className="mt-4 text-sm text-stone-500">Loading orders…</p>}
      {error && <p className="mt-4 text-sm text-rose-700">{error}</p>}

      {!loading && orders.length === 0 && (
        <p className="mt-4 text-sm text-stone-500">No orders yet.</p>
      )}

      {orders.length > 0 && (
        <table className="mt-4 w-full text-sm">
          <thead>
            <tr className="border-b border-stone-200 text-left text-xs text-stone-500">
              <th className="py-2">Reference</th>
              <th>Items</th>
              <th className="text-right">Total</th>
              <th>Placed</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr
                key={order.reference}
                data-testid="order-row"
                data-reference={order.reference}
                className="border-b border-stone-100 align-top"
              >
                <td className="py-2 font-mono text-xs text-stone-500">
                  {order.reference.slice(0, 8)}
                </td>
                <td className="py-2 text-xs text-stone-600">
                  {order.items.map((item) => (
                    <div key={item.sku}>
                      {item.quantity} × {item.sku}
                    </div>
                  ))}
                </td>
                <td className="py-2 text-right tabular-nums">{money(order.total)}</td>
                <td className="py-2 text-xs text-stone-500">
                  {relativeTime(order.createdAt)}
                </td>
                <td className="py-2">
                  <StatusBadge status={order.status} />
                  {order.cancellationReason && (
                    <div
                      data-testid="cancellation-reason"
                      className="mt-1 text-xs text-rose-700"
                    >
                      {order.cancellationReason}
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
