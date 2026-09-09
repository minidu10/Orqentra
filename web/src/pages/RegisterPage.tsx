import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/endpoints";
import { useSession } from "../session";

export function RegisterPage() {
  const { signIn } = useSession();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [restaurantName, setRestaurantName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.register(email, password, restaurantName);
      // Registration does not return a token, so sign in with the new credentials.
      const login = await api.login(email, password);
      signIn(login.token);
      navigate("/catalog", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto mt-24 max-w-sm px-4">
      <h1 className="text-lg font-semibold">Register a restaurant</h1>

      <form onSubmit={submit} className="mt-6 space-y-3">
        <label className="block">
          <span className="text-xs text-stone-600">Restaurant name</span>
          <input
            value={restaurantName}
            onChange={(e) => setRestaurantName(e.target.value)}
            required
            className="mt-1 w-full rounded border border-stone-300 px-2 py-1.5 text-sm"
          />
        </label>

        <label className="block">
          <span className="text-xs text-stone-600">Email</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            className="mt-1 w-full rounded border border-stone-300 px-2 py-1.5 text-sm"
          />
        </label>

        <label className="block">
          <span className="text-xs text-stone-600">Password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            className="mt-1 w-full rounded border border-stone-300 px-2 py-1.5 text-sm"
          />
        </label>

        {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}

        <button
          type="submit"
          disabled={busy}
          className="w-full rounded bg-stone-900 px-3 py-2 text-sm text-white disabled:opacity-50"
        >
          {busy ? "Creating…" : "Create account"}
        </button>
      </form>

      <p className="mt-4 text-xs text-stone-500">
        Already registered? <Link to="/login" className="underline">Sign in</Link>
      </p>
    </div>
  );
}
