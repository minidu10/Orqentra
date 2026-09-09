import type { OrderStatus } from "../types";

const TOKEN_KEY = "orqentra.token";

/**
 * The token lives in localStorage so a refresh does not log the user out.
 *
 * This is a deliberate, labelled simplification: anything that can inject a script into
 * this origin can read localStorage and steal the token. The production answer is an
 * httpOnly, SameSite cookie, which JavaScript cannot read at all.
 */
export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    /* private mode or blocked storage: the session simply will not survive a reload */
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* nothing to do */
  }
}

export interface TokenClaims {
  sub: string;
  role: string;
  restaurantRef: string;
  exp: number;
}

/**
 * Reads the claims for display and navigation only.
 *
 * <p>This is not a security check. The signature is never verified here, and it could not
 * meaningfully be: the browser holds no secret. Hiding an admin link is a convenience so
 * users are not shown doors they cannot open. The gateway's 403 is the actual control,
 * and every rule this reads is enforced server-side as well.
 */
export function readClaims(token: string | null): TokenClaims | null {
  if (!token) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;

  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = payload + "=".repeat((4 - (payload.length % 4)) % 4);
    return JSON.parse(atob(padded)) as TokenClaims;
  } catch {
    return null;
  }
}

export function isAdmin(token: string | null): boolean {
  return readClaims(token)?.role === "ADMIN";
}

export const TERMINAL_STATUSES: OrderStatus[] = ["CONFIRMED", "CANCELLED"];
