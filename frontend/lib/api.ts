const TOKEN_KEY = "fg_token";
const ROLE_KEY = "fg_role";
const USER_KEY = "fg_user";

export type Role = "ADMIN" | "OPERATOR" | "VIEWER";

export function token(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  return sessionStorage.getItem(TOKEN_KEY);
}

export function role(): Role | null {
  if (typeof window === "undefined") {
    return null;
  }
  return sessionStorage.getItem(ROLE_KEY) as Role | null;
}

export function username(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  return sessionStorage.getItem(USER_KEY);
}

export function setSession(accessToken: string, userRole: string, user: string) {
  sessionStorage.setItem(TOKEN_KEY, accessToken);
  sessionStorage.setItem(ROLE_KEY, userRole);
  sessionStorage.setItem(USER_KEY, user);
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(ROLE_KEY);
  sessionStorage.removeItem(USER_KEY);
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const t = token();
  if (t) {
    headers.set("Authorization", `Bearer ${t}`);
  }
  const signal = init.signal ?? AbortSignal.timeout(8000);
  let res = await fetch(path, { ...init, headers, credentials: "include", signal });
  if (res.status === 401 && !path.startsWith("/api/v1/auth/")) {
    const refreshed = await refresh();
    if (refreshed) {
      headers.set("Authorization", `Bearer ${token()}`);
      res = await fetch(path, { ...init, headers, credentials: "include", signal });
    }
  }
  if (!res.ok) {
    const text = await res.text();
    throw new Error(formatError(text, res.status));
  }
  if (res.status === 204) {
    return undefined as T;
  }
  const ct = res.headers.get("content-type") || "";
  if (!ct.includes("application/json")) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

function formatError(text: string, status: number): string {
  try {
    const body = JSON.parse(text) as { message?: string; error?: string; status?: number };
    return body.message || body.error || `${status}`;
  } catch {
    const clipped = text.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
    return clipped.slice(0, 160) || `${status}`;
  }
}

export type LoginResult = {
  accessToken: string;
  role: Role;
  username?: string;
  tokenType: string;
  expiresInMs: number;
};

export async function login(user: string, password: string): Promise<LoginResult> {
  const body = await api<LoginResult>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ username: user, password }),
  });
  setSession(body.accessToken, body.role, body.username || user);
  return body;
}

export async function refresh(): Promise<boolean> {
  try {
    const body = await api<LoginResult>("/api/v1/auth/refresh", { method: "POST" });
    setSession(body.accessToken, body.role, body.username || username() || "user");
    return true;
  } catch {
    clearSession();
    return false;
  }
}

export async function logout() {
  try {
    await api("/api/v1/auth/logout", { method: "POST" });
  } finally {
    clearSession();
  }
}
