import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { api } from "../api/endpoints";
import { useSession } from "../session";

export function LoginPage() {
  const { token, signIn } = useSession();
  const navigate = useNavigate();
  const [email, setEmail] = useState("alice@orqentra.test");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (token) return <Navigate to="/catalog" replace />;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const response = await api.login(email, password);
      signIn(response.token);
      navigate("/catalog", { replace: true });
    } catch (err) {
      // The server returns one message for a wrong password and an unknown email alike,
      // so it is shown verbatim: inventing a friendlier, more specific message here
      // would undo the server's care not to reveal which emails are registered.
      setError(err instanceof Error ? err.message : "Sign in failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto mt-24 max-w-sm px-4">
      <h1 className="text-lg font-semibold">Sign in to Orqentra</h1>

      <form onSubmit={submit} className="mt-6 space-y-3">
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

        {error && (
          <p role="alert" data-testid="login-error" className="text-sm text-rose-700">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={busy}
          className="w-full rounded bg-stone-900 px-3 py-2 text-sm text-white disabled:opacity-50"
        >
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>

      <p className="mt-4 text-xs text-stone-500">
        No account? <Link to="/register" className="underline">Register a restaurant</Link>
      </p>
    </div>
  );
}
