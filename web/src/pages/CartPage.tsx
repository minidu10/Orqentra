import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/endpoints";
import { useCart } from "../cart";
import { money } from "../components/Money";
import { StatusBadge } from "../components/StatusBadge";
import type { Order } from "../types";

export function CartPage() {
  const { lines, total, setQuantity, remove, clear } = useCart();
  const navigate = useNavigate();
  const [placed, setPlaced] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const placeOrder = async () => {
    setBusy(true);
    setError(null);
    try {
      // Items only. The restaurant comes from the token, so there is nothing else to send.
      const order = await api.placeOrder(
        lines.map((line) => ({ sku: line.sku, quantity: line.quantity })),
      );
      setPlaced(order);
      clear();
      // Show the PENDING order briefly, then move to the list where the badge will
      // change on its own as the saga progresses.
      setTimeout(() => navigate("/orders"), 900);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not place the order");
    } finally {
      setBusy(false);
    }
  };

  if (placed) {
    return (
      <div>
        <h1 className="text-base font-semibold">Order placed</h1>
        <div className="mt-3 flex items-center gap-3 text-sm">
          <span className="font-mono text-xs text-stone-500">{placed.reference}</span>
          <StatusBadge status={placed.status} />
        </div>
        <p className="mt-2 text-xs text-stone-500">Taking you to your orders…</p>
      </div>
    );
  }

  if (lines.length === 0) {
    return <p className="text-sm text-stone-500">Your cart is empty.</p>;
  }

  return (
    <div>
      <h1 className="text-base font-semibold">Cart</h1>

      <table className="mt-4 w-full text-sm">
        <thead>
          <tr className="border-b border-stone-200 text-left text-xs text-stone-500">
            <th className="py-2">Product</th>
            <th className="text-right">Unit</th>
            <th className="text-right">Qty</th>
            <th className="text-right">Line total</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {lines.map((line) => (
            <tr key={line.sku} className="border-b border-stone-100">
              <td className="py-2">
                {line.name}
                <span className="ml-2 font-mono text-xs text-stone-400">{line.sku}</span>
              </td>
              <td className="text-right tabular-nums">{money(line.unitPrice)}</td>
              <td className="py-1.5 text-right">
                <input
                  type="number"
                  min={1}
                  value={line.quantity}
                  onChange={(e) => setQuantity(line.sku, Number(e.target.value) || 0)}
                  className="w-16 rounded border border-stone-300 px-1.5 py-1 text-right text-sm"
                />
              </td>
              <td className="text-right tabular-nums">
                {money(line.unitPrice * line.quantity)}
              </td>
              <td className="text-right">
                <button
                  onClick={() => remove(line.sku)}
                  className="text-xs text-stone-500 underline"
                >
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={3} className="py-3 text-right text-xs text-stone-500">
              Total
            </td>
            <td className="py-3 text-right font-semibold tabular-nums">{money(total)}</td>
            <td />
          </tr>
        </tfoot>
      </table>

      {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}

      <button
        onClick={placeOrder}
        disabled={busy}
        className="mt-2 rounded bg-stone-900 px-3 py-2 text-sm text-white disabled:opacity-50"
      >
        {busy ? "Placing…" : "Place order"}
      </button>
    </div>
  );
}
