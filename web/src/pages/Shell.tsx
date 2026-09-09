import { Link, NavLink, Outlet } from "react-router-dom";
import { useSession } from "../session";
import { useCart } from "../cart";

export function Shell() {
  const { email, isAdmin, signOut } = useSession();
  const { lines } = useCart();
  const cartCount = lines.reduce((sum, line) => sum + line.quantity, 0);

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `px-3 py-1.5 text-sm rounded ${
      isActive ? "bg-stone-900 text-white" : "text-stone-700 hover:bg-stone-200"
    }`;

  return (
    <div className="min-h-screen">
      <header className="border-b border-stone-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center gap-4 px-4 py-3">
          <Link to="/catalog" className="text-sm font-semibold tracking-tight">
            Orqentra
          </Link>

          <nav className="flex items-center gap-1">
            <NavLink to="/catalog" className={linkClass}>
              Catalog
            </NavLink>
            <NavLink to="/cart" className={linkClass}>
              Cart{cartCount > 0 ? ` (${cartCount})` : ""}
            </NavLink>
            <NavLink to="/orders" className={linkClass}>
              Orders
            </NavLink>
            {/* Shown only for admins. This is convenience, not access control. */}
            {isAdmin && (
              <NavLink to="/admin" className={linkClass}>
                Admin
              </NavLink>
            )}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            <span className="text-xs text-stone-500">{email}</span>
            <button
              onClick={signOut}
              className="rounded border border-stone-300 px-2 py-1 text-xs text-stone-700 hover:bg-stone-100"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
