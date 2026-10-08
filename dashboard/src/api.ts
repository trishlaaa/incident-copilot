export type Severity = 'LOW' | 'MEDIUM' | 'HIGH';
export type Status = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';

export interface Incident {
  id: number;
  service: string;
  exceptionType: string;
  fingerprint: string;
  messageTemplate: string;
  sampleMessage: string;
  severity: Severity;
  status: Status;
  eventCount: number;
  firstSeen: string;
  lastSeen: string;
  aiRootCause: string | null;
  aiFix: string | null;
  aiConfidence: string | null;
  aiCitations: string | null;
  aiSource: 'llm' | 'retrieval' | 'none' | null;
  aiAnalyzedAt: string | null;
  feedback: 'up' | 'down' | null;
}

export interface LogLine {
  id: number;
  service: string;
  level: string;
  exceptionType: string;
  message: string;
  traceId: string;
  timestamp: string;
}

export interface PastIncident {
  id: string;
  title: string;
  service: string;
  rootCause: string;
  fix: string;
}

export interface IncidentDetail {
  incident: Incident;
  recentEvents: LogLine[];
  citedPastIncidents: PastIncident[];
}

export interface Scenario {
  id: string;
  label: string;
  service: string;
}

export interface Stats {
  eventsProcessed: number;
  eventsPerSecond: number;
  avgProcessingMs: number;
  activeIncidents: number;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!res.ok) {
    throw new Error(`${init?.method ?? 'GET'} ${path} failed with ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  incidents: () => request<Incident[]>('/api/incidents'),
  incident: (id: number) => request<IncidentDetail>(`/api/incidents/${id}`),
  scenarios: () => request<Scenario[]>('/api/simulate/scenarios'),
  stats: () => request<Stats>('/api/stats'),
  resetStats: () => request<Stats>('/api/stats/reset', { method: 'POST' }),
  inject: (scenarioId: string, count: number) =>
    request(`/api/simulate/${scenarioId}?count=${count}`, { method: 'POST' }),
  burst: (count: number) => request(`/api/simulate/burst?count=${count}`, { method: 'POST' }),
  setStatus: (id: number, status: Status) =>
    request<Incident>(`/api/incidents/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),
  feedback: (id: number, feedback: 'up' | 'down') =>
    request<Incident>(`/api/incidents/${id}/feedback`, {
      method: 'POST',
      body: JSON.stringify({ feedback }),
    }),
  analyze: (id: number) => request<Incident>(`/api/incidents/${id}/analyze`, { method: 'POST' }),
};
