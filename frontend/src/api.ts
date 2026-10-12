const TOKEN_KEY = "gateflow_token";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type") && options.body) {
    headers.set("Content-Type", "application/json");
  }
  const token = getToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    clearToken();
    throw new Error("Unauthorized");
  }
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || response.statusText);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json();
}

export type Metrics = {
  totalRequests: number;
  successRate: number;
  avgLatencyMs: number;
  blockedRequests: number;
  activeRoutes: number;
};

export type RequestRow = {
  id: string;
  method: string;
  path: string;
  status: number;
  latencyMs: number;
  time: string;
  apiKeyPrefix: string;
};

export type ApiKeyRow = {
  id: number;
  name: string;
  prefix: string;
  quotaPerMinute: number;
  revoked: boolean;
  createdAt: string;
};

export type RouteRow = {
  name: string;
  match: string;
  target: string;
  auth: string;
};
