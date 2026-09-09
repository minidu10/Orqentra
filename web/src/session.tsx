import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";
import { clearToken, getToken, readClaims, setToken } from "./api/auth";
import { setUnauthorizedHandler } from "./api/client";

interface Session {
  token: string | null;
  email: string | null;
  restaurantRef: string | null;
  isAdmin: boolean;
  signIn: (token: string) => void;
  signOut: () => void;
}

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => getToken());
  const navigate = useNavigate();

  const signOut = useCallback(() => {
    clearToken();
    setTokenState(null);
    navigate("/login", { replace: true });
  }, [navigate]);

  const signIn = useCallback((next: string) => {
    setToken(next);
    setTokenState(next);
  }, []);

  useEffect(() => {
    // Any 401 from any call ends the session, wherever it came from.
    setUnauthorizedHandler(() => {
      setTokenState(null);
      navigate("/login", { replace: true });
    });
  }, [navigate]);

  const value = useMemo<Session>(() => {
    const claims = readClaims(token);
    return {
      token,
      email: claims?.sub ?? null,
      restaurantRef: claims?.restaurantRef ?? null,
      isAdmin: claims?.role === "ADMIN",
      signIn,
      signOut,
    };
  }, [token, signIn, signOut]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error("useSession must be used inside SessionProvider");
  return session;
}
