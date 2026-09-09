import { Navigate, Route, Routes } from "react-router-dom";
import { SessionProvider, useSession } from "./session";
import { CartProvider } from "./cart";
import { Shell } from "./pages/Shell";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { CatalogPage } from "./pages/CatalogPage";
import { CartPage } from "./pages/CartPage";
import { OrdersPage } from "./pages/OrdersPage";
import { AdminPage } from "./pages/AdminPage";
import type { ReactNode } from "react";

function RequireAuth({ children }: { children: ReactNode }) {
  const { token } = useSession();
  return token ? <>{children}</> : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <SessionProvider>
      <CartProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <Shell />
              </RequireAuth>
            }
          >
            <Route index element={<Navigate to="/catalog" replace />} />
            <Route path="catalog" element={<CatalogPage />} />
            <Route path="cart" element={<CartPage />} />
            <Route path="orders" element={<OrdersPage />} />
            {/*
              Routed for everyone on purpose. Hiding the link is a convenience; the page
              itself simply shows whatever the server returns, which is 403 for a
              RESTAURANT token even when the URL is typed in directly.
            */}
            <Route path="admin" element={<AdminPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/catalog" replace />} />
        </Routes>
      </CartProvider>
    </SessionProvider>
  );
}
