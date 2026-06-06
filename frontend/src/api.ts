import type { ApiResponse, AuthResponse, PracticeSession, Scenario, SessionReport, VoiceDialogueResponse } from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

type RequestOptions = RequestInit & {
  token?: string;
};

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.token) {
    headers.set("Authorization", `Bearer ${options.token}`);
  }
  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new Error(payload?.message ?? payload?.error ?? `HTTP ${response.status}`);
  }
  return payload as T;
}

export function login(username: string, password: string) {
  return request<AuthResponse>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password })
  });
}

export function listScenarios(token: string) {
  return request<Scenario[]>("/api/v1/scenarios", { token });
}

export function createSession(token: string, scenarioId: string, difficulty: string) {
  return request<PracticeSession>("/api/v1/sessions", {
    method: "POST",
    token,
    body: JSON.stringify({ scenarioId, difficulty })
  });
}

export function sendVoiceDialogue(params: {
  token: string;
  sessionId: string;
  audio: Blob;
  referenceText?: string;
  durationMs?: number;
  voice?: string;
}) {
  const formData = new FormData();
  formData.append("audio", params.audio, "practice.wav");
  formData.append("language", "en-US");
  if (params.referenceText) formData.append("referenceText", params.referenceText);
  if (params.durationMs) formData.append("durationMs", String(params.durationMs));
  if (params.voice) formData.append("voice", params.voice);

  return request<VoiceDialogueResponse>(`/api/v1/voice/dialogue/${params.sessionId}`, {
    method: "POST",
    token: params.token,
    body: formData
  });
}

export function generateReport(token: string, sessionId: string) {
  return request<ApiResponse<SessionReport>>(`/api/v1/sessions/${sessionId}/report`, {
    method: "POST",
    token
  });
}
