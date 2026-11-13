import type { EditorData, ProposedPayload, SessionInfo } from "./types";

function tokenFromLocation(): string | null {
  const url = new URL(window.location.href);
  const q = url.searchParams.get("token");
  if (q) return q;
  const hash = url.hash || "";
  const m = /[?&]token=([^&]+)/.exec(hash);
  return m ? decodeURIComponent(m[1]!) : null;
}

export function currentToken(): string | null {
  return tokenFromLocation();
}

export function sessionIdFromHash(): string | null {
  const m = /#\/s\/([^/?]+)/.exec(window.location.hash);
  return m ? decodeURIComponent(m[1]!) : null;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = currentToken();
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", "Bearer " + token);
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const sep = path.includes("?") ? "&" : "?";
  const url = token && !path.includes("token=") ? path + sep + "token=" + encodeURIComponent(token) : path;
  const res = await fetch(url, { ...init, headers });
  if (!res.ok) {
    let message = res.status + " " + res.statusText;
    try {
      const body = await res.json();
      if (body && body.error) message = body.error;
    } catch {
      /* ignore */
    }
    throw new Error(message);
  }
  return (await res.json()) as T;
}

export async function fetchSession(): Promise<SessionInfo> {
  return request<SessionInfo>("/api/session");
}

export async function fetchData(): Promise<EditorData> {
  return request<EditorData>("/api/data");
}

export async function postDiff(payload: ProposedPayload) {
  return request("/api/diff", { method: "POST", body: JSON.stringify(payload) });
}

export async function postApply(payload: ProposedPayload): Promise<{ ok: boolean; summary: string; applyCode?: string; diff?: unknown }> {
  return request("/api/apply", { method: "POST", body: JSON.stringify(payload) });
}

export async function searchNodes(q: string): Promise<{ hits: { subject: string; node: string }[]; count: number }> {
  return request("/api/search?q=" + encodeURIComponent(q));
}
