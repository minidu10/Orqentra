import { clearToken, getToken } from "./auth";
import type { ProblemDetail } from "../types";

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, message: string, problem: ProblemDetail | null) {
    super(message);
    this.status = status;
    this.problem = problem;
  }
}

/** Notified when any call comes back 401, so the app can send the user to login. */
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler;
}

/**
 * The single place a request is built. Attaching the token here rather than in each
 * component means there is exactly one thing to audit, and no screen can forget it.
 */
export interface ApiOptions {
  /**
   * True for endpoints where a 401 is a normal answer rather than an expired session.
   * Sign-in is the case that matters: rejecting bad credentials must surface the
   * server's own message, not log the user out of a session they never had.
   */
  credentialsEndpoint?: boolean;
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  options: ApiOptions = {},
): Promise<T> {
  const token = getToken();
  const headers = new Headers(init.headers);

  headers.set("Accept", "application/json");
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  // Never a query parameter: a token in a URL leaks into history, logs and referrers.
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });

  if (response.status === 401 && !options.credentialsEndpoint) {
    clearToken();
    onUnauthorized?.();
    throw new ApiError(401, "Your session has expired. Please sign in again.", null);
  }

  if (!response.ok) {
    const problem = await readProblem(response);
    throw new ApiError(
      response.status,
      problem?.detail ?? problem?.title ?? `Request failed (${response.status})`,
      problem,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return null;
  }
}
