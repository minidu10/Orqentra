import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { CartLine, Product } from "./types";

interface CartApi {
  lines: CartLine[];
  total: number;
  add: (product: Product, quantity: number) => void;
  setQuantity: (sku: string, quantity: number) => void;
  remove: (sku: string) => void;
  clear: () => void;
}

const CartContext = createContext<CartApi | null>(null);

export function CartProvider({ children }: { children: ReactNode }) {
  const [lines, setLines] = useState<CartLine[]>([]);

  const add = useCallback((product: Product, quantity: number) => {
    setLines((current) => {
      const existing = current.find((line) => line.sku === product.sku);
      if (existing) {
        return current.map((line) =>
          line.sku === product.sku
            ? { ...line, quantity: line.quantity + quantity }
            : line,
        );
      }
      return [
        ...current,
        {
          sku: product.sku,
          name: product.name,
          unitPrice: product.price,
          quantity,
        },
      ];
    });
  }, []);

  const setQuantity = useCallback((sku: string, quantity: number) => {
    setLines((current) =>
      quantity <= 0
        ? current.filter((line) => line.sku !== sku)
        : current.map((line) => (line.sku === sku ? { ...line, quantity } : line)),
    );
  }, []);

  const remove = useCallback((sku: string) => {
    setLines((current) => current.filter((line) => line.sku !== sku));
  }, []);

  const clear = useCallback(() => setLines([]), []);

  const total = useMemo(
    () => lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0),
    [lines],
  );

  const value = useMemo<CartApi>(
    () => ({ lines, total, add, setQuantity, remove, clear }),
    [lines, total, add, setQuantity, remove, clear],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartApi {
  const cart = useContext(CartContext);
  if (!cart) throw new Error("useCart must be used inside CartProvider");
  return cart;
}
