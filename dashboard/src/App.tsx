import { useCallback, useEffect, useState } from 'react';
import { api, Incident, IncidentDetail, Scenario, Stats, Status } from './api';

const POLL_MS = 2500;

function timeAgo(iso: string | null): string {
  if (!iso) return '';
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.round(s / 60)}m ago`;
  return `${Math.round(s / 3600)}h ago`;
}

const statusLabel: Record<Status, string> = {
  OPEN: 'Open',
  ACKNOWLEDGED: 'Acknowledged',
  RESOLVED: 'Resolved',
};

export default function App() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<IncidentDetail | null>(null);
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [scenarioId, setScenarioId] = useState('');
  const [count, setCount] = useState(25);
  const [stats, setStats] = useState<Stats | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [list, st] = await Promise.all([api.incidents(), api.stats()]);
      setIncidents(list);
      setStats(st);
      if (selectedId != null) setDetail(await api.incident(selectedId));
      setError(null);
    } catch {
      setError('Can’t reach the triage service on port 8080. Start it with docker compose up, then this page reconnects on its own.');
    }
  }, [selectedId]);

  useEffect(() => {
    api.scenarios().then((s) => {
      setScenarios(s);
      if (s.length) setScenarioId(s[0].id);
    }).catch(() => undefined);
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, POLL_MS);
    return () => clearInterval(t);
  }, [refresh]);

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await fn();
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const select = async (id: number) => {
    setSelectedId(id);
    setDetail(await api.incident(id));
  };

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <h1>Incident Copilot</h1>
          <p>Groups errors into incidents and suggests likely root causes.</p>
        </div>
        {stats && (
          <dl className="stats" aria-label="Pipeline stats">
            <div><dt>Events processed</dt><dd>{stats.eventsProcessed.toLocaleString()}</dd></div>
            <div><dt>Events per second</dt><dd>{stats.eventsPerSecond.toLocaleString()}</dd></div>
            <div><dt>Avg processing</dt><dd>{stats.avgProcessingMs} ms</dd></div>
            <div><dt>Active incidents</dt><dd>{stats.activeIncidents}</dd></div>
          </dl>
        )}
      </header>

      <section className="controls" aria-label="Simulate failures">
        <label>
          Failure
          <select value={scenarioId} onChange={(e) => setScenarioId(e.target.value)}>
            {scenarios.map((s) => (
              <option key={s.id} value={s.id}>{s.label}</option>
            ))}
          </select>
        </label>
        <label>
          Events
          <input type="number" min={1} max={5000} value={count}
                 onChange={(e) => setCount(Number(e.target.value) || 1)} />
        </label>
        <button className="primary" disabled={busy || !scenarioId}
                onClick={() => run(() => api.inject(scenarioId, count))}>
          Inject failure
        </button>
        <span className="divider" aria-hidden="true" />
        <button disabled={busy} onClick={() => run(async () => {
          await api.resetStats();
          await api.burst(20000);
        })}>
          Run load test (20,000 events)
        </button>
      </section>

      {error && <p className="error" role="alert">{error}</p>}

      <main className="layout">
        <section className="list" aria-label="Incidents">
          {incidents.length === 0 ? (
            <div className="empty">
              <p>No incidents yet.</p>
              <p>Inject a failure above to see errors grouped into an incident.</p>
            </div>
          ) : (
            <ul>
              {incidents.map((i) => (
                <li key={i.id}>
                  <button className={`row sev-${i.severity} ${selectedId === i.id ? 'selected' : ''} ${i.status === 'RESOLVED' ? 'resolved' : ''}`}
                          onClick={() => select(i.id)}>
                    <span className="row-title">{i.exceptionType}</span>
                    <span className="row-meta">{i.service}, {i.eventCount} events, {timeAgo(i.lastSeen)}</span>
                    <span className="row-status">{statusLabel[i.status]}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="detail" aria-label="Incident detail">
          {!detail ? (
            <div className="empty">
              <p>Select an incident to see its logs and the suggested root cause.</p>
            </div>
          ) : (
            <IncidentView detail={detail} busy={busy} run={run} />
          )}
        </section>
      </main>
    </div>
  );
}

function IncidentView({ detail, busy, run }: {
  detail: IncidentDetail;
  busy: boolean;
  run: (fn: () => Promise<unknown>) => Promise<void>;
}) {
  const i = detail.incident;
  const sourceNote: Record<string, string> = {
    llm: 'Suggested by the LLM using similar past incidents.',
    retrieval: 'Closest past incident (no LLM key configured).',
    none: 'No similar past incident found.',
  };

  return (
    <article>
      <div className={`detail-head sev-${i.severity}`}>
        <div className="count">
          <strong>{i.eventCount.toLocaleString()}</strong>
          <span>events</span>
        </div>
        <div>
          <h2>{i.exceptionType} in {i.service}</h2>
          <p className="template">{i.messageTemplate}</p>
          <p className="muted">
            Severity {i.severity.toLowerCase()}. First seen {timeAgo(i.firstSeen)}, last seen {timeAgo(i.lastSeen)}.
          </p>
        </div>
      </div>

      <div className="actions">
        {i.status !== 'ACKNOWLEDGED' && i.status !== 'RESOLVED' && (
          <button disabled={busy} onClick={() => run(() => api.setStatus(i.id, 'ACKNOWLEDGED'))}>Acknowledge</button>
        )}
        {i.status !== 'RESOLVED' ? (
          <button disabled={busy} onClick={() => run(() => api.setStatus(i.id, 'RESOLVED'))}>Mark resolved</button>
        ) : (
          <button disabled={busy} onClick={() => run(() => api.setStatus(i.id, 'OPEN'))}>Reopen</button>
        )}
      </div>

      <section className="suggestion" aria-label="Suggested root cause">
        <h3>Suggested root cause</h3>
        {!i.aiAnalyzedAt ? (
          <p className="muted">Analysing…</p>
        ) : (
          <>
            <p>{i.aiRootCause}</p>
            <h3>Suggested fix</h3>
            <p>{i.aiFix}</p>
            <p className="muted">
              Confidence {i.aiConfidence}. {sourceNote[i.aiSource ?? 'none']}
            </p>
            {detail.citedPastIncidents.length > 0 && (
              <div className="citations">
                <h4>Based on</h4>
                <ul>
                  {detail.citedPastIncidents.map((p) => (
                    <li key={p.id}><span className="cite-id">{p.id}</span> {p.title}</li>
                  ))}
                </ul>
              </div>
            )}
            <div className="feedback">
              <span>Was this helpful?</span>
              <button aria-pressed={i.feedback === 'up'} disabled={busy}
                      onClick={() => run(() => api.feedback(i.id, 'up'))}>Yes</button>
              <button aria-pressed={i.feedback === 'down'} disabled={busy}
                      onClick={() => run(() => api.feedback(i.id, 'down'))}>No</button>
              <button className="link" disabled={busy} onClick={() => run(() => api.analyze(i.id))}>Analyse again</button>
            </div>
          </>
        )}
      </section>

      <section aria-label="Recent log lines">
        <h3>Recent log lines</h3>
        <ol className="logs">
          {detail.recentEvents.map((e) => (
            <li key={e.id}>
              <span className={`lvl lvl-${e.level}`}>{e.level}</span>
              <code>{e.message}</code>
            </li>
          ))}
        </ol>
      </section>
    </article>
  );
}
