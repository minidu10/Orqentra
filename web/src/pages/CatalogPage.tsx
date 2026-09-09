import { useEffect, useState } from "react";
import { api } from "../api/endpoints";
import { useCart } from "../cart";
import { money } from "../components/Money";
import type { Product } from "../types";

export function CatalogPage() {
  const { add } = useCart();
  const [products, setProducts] = useState<Product[]>([]);
  const [stock, setStock] = useState<Record<string, number>>({});
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const list = await api.products();
        if (cancelled) return;
        setProducts(list);

        // N+1: one stock request per product row. Left deliberately, see the summary.
        // The fix is a batch endpoint taking several SKUs, which is out of scope here.
        const levels = await Promise.all(
          list.map(async (product) => {
            try {
              const s = await api.stock(product.sku);
              return [product.sku, s.available] as const;
            } catch {
              return [product.sku, -1] as const;
            }
          }),
        );
        if (cancelled) return;
        setStock(Object.fromEntries(levels));
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Could not load catalog");
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) return <p className="text-sm text-stone-500">Loading catalog…</p>;
  if (error) return <p className="text-sm text-rose-700">{error}</p>;

  return (
    <div>
      <h1 className="text-base font-semibold">Catalog</h1>

      <table className="mt-4 w-full text-sm">
        <thead>
          <tr className="border-b border-stone-200 text-left text-xs text-stone-500">
            <th className="py-2">SKU</th>
            <th>Product</th>
            <th className="text-right">Price</th>
            <th className="text-right">In stock</th>
            <th className="w-40" />
          </tr>
        </thead>
        <tbody>
          {products.map((product) => {
            const available = stock[product.sku];
            const quantity = quantities[product.sku] ?? 1;
            return (
              <tr key={product.sku} className="border-b border-stone-100">
                <td className="py-2 font-mono text-xs text-stone-500">{product.sku}</td>
                <td>{product.name}</td>
                <td className="text-right tabular-nums">{money(product.price)}</td>
                <td className="text-right tabular-nums">
                  {available === undefined ? "…" : available < 0 ? "—" : available}
                </td>
                <td className="py-1.5">
                  <div className="flex items-center justify-end gap-2">
                    <input
                      type="number"
                      min={1}
                      value={quantity}
                      onChange={(e) =>
                        setQuantities((q) => ({
                          ...q,
                          [product.sku]: Math.max(1, Number(e.target.value) || 1),
                        }))
                      }
                      className="w-16 rounded border border-stone-300 px-1.5 py-1 text-right text-sm"
                    />
                    <button
                      onClick={() => add(product, quantity)}
                      className="rounded bg-stone-900 px-2 py-1 text-xs text-white"
                    >
                      Add
                    </button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
