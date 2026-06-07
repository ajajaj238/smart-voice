import type {
  ApiResponse,
  AuthResponse,
  PageResponse,
  PracticeSession,
  Scenario,
  SessionDetail,
  SessionReport,
  UserProfile,
  VoiceDialogueResponse
} from "./types";

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
  let payload: any = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = null;
    }
  }

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

export function register(username: string, password: string, email?: string) {
  return request<AuthResponse>("/api/v1/auth/register", {
    method: "POST",
    body: JSON.stringify({ username, password, email })
  });
}

export function listScenarios(token: string) {
  return request<Scenario[]>("/api/v1/scenarios", { token });
}

export function getCurrentUser(token: string) {
  return request<UserProfile>("/api/v1/users/me", { token });
}

export function updateCurrentUser(token: string, profile: { email?: string; englishLevel?: string; avatarUrl?: string }) {
  return request<UserProfile>("/api/v1/users/me", {
    method: "PUT",
    token,
    body: JSON.stringify(profile)
  });
}

export function createSession(token: string, scenarioId: string, difficulty: string) {
  return request<PracticeSession>("/api/v1/sessions", {
    method: "POST",
    token,
    body: JSON.stringify({ scenarioId, difficulty })
  });
}

export function listSessions(token: string, page = 1, size = 8) {
  return request<PageResponse<PracticeSession>>(`/api/v1/sessions?page=${page}&size=${size}`, { token });
}

export function getSessionDetail(token: string, sessionId: string) {
  return request<SessionDetail>(`/api/v1/sessions/${sessionId}/detail`, { token })
    .catch((error) => {
      const message = error instanceof Error ? error.message : String(error);
      if (!message.includes("Resource not found") && !message.includes("No static resource") && !message.includes("HTTP 404")) {
        throw error;
      }
      return request<SessionDetail>(`/api/v1/sessions/detail/${sessionId}`, { token });
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

export function getReport(token: string, sessionId: string) {
  return request<ApiResponse<SessionReport>>(`/api/v1/sessions/${sessionId}/report`, {
    token
  });
}
