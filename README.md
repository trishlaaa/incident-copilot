# AI Incident Triage Copilot

An event-driven incident triage tool. Services stream error logs through **Kafka**; a
**Spring Boot** triage service groups related errors into incidents in **PostgreSQL**; an AI
advisor suggests a likely root cause and fix using similar past incidents; and a **React**
dashboard shows everything live.

![Dashboard screenshot](docs/dashboard.png) <!-- add your screenshot here -->

## What it does

1. **Simulated services emit errors** (payments, auth, orders, notifications, ledger) to the
   `app-logs` Kafka topic, keyed by service.
2. **Triage service consumes the stream** and fingerprints each error
   (service + exception type + message template with IDs/numbers stripped). Errors with the same
   fingerprint within 15 minutes are grouped into one incident with a count and severity.
3. **AI advisor** retrieves the most similar past incidents from a knowledge base and asks an LLM
   for a root cause, fix and confidence, with citations.
   - Guardrails: secrets and personal data are redacted before anything leaves the service; the
     advisor only suggests, never acts; it declines when evidence is weak; citations not in the
     retrieved set are dropped.
   - Works without an API key: falls back to the closest past incident (retrieval-only mode).
4. **Dashboard** shows live incidents, recent log lines, the suggestion with its sources,
   feedback buttons, status changes and pipeline throughput.

## Architecture

```
log simulator ──► Kafka: app-logs (3 partitions) ──► triage consumer ──► PostgreSQL
                                                         │                (incidents, log_events)
                                                         └─► AI advisor (async, after commit)
                                                               ├─ keyword retrieval over past incidents
                                                               └─ LLM (any OpenAI-compatible API)
React dashboard ◄──────────── REST API (/api/incidents, /api/stats, /api/simulate)
```

## Tech stack

Java 17, Spring Boot 3, Spring Kafka, Spring Data JPA, PostgreSQL 16, Apache Kafka 3.8 (KRaft),
React 18 + TypeScript (Vite), Docker Compose, GitHub Actions, JUnit 5.

## Run it locally

Prerequisites: Docker Desktop, Node.js 20+.

```bash
# 1. Start Kafka, Postgres and the triage service
docker compose up --build

# 2. In another terminal, start the dashboard
cd dashboard
npm install
npm run dev
# open http://localhost:5173
```

Optional: enable LLM suggestions by copying `.env.example` to `.env` and adding an API key,
then run `docker compose up --build` again.

## Try it

- Pick a failure in the dashboard and click **Inject failure**. Within a few seconds an incident
  appears with a suggested root cause.
- Inject the same failure again: the event count rises on the same incident instead of creating a
  new one.
- Click **Run load test** to send 20,000 mixed events and watch events per second in the header.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/incidents` | Latest 100 incidents |
| GET | `/api/incidents/{id}` | Incident with recent log lines and cited past incidents |
| PATCH | `/api/incidents/{id}/status` | `{"status": "ACKNOWLEDGED" \| "RESOLVED" \| "OPEN"}` |
| POST | `/api/incidents/{id}/feedback` | `{"feedback": "up" \| "down"}` |
| POST | `/api/incidents/{id}/analyze` | Re-run the AI analysis |
| GET | `/api/simulate/scenarios` | Available failure scenarios |
| POST | `/api/simulate/{scenarioId}?count=25` | Inject one failure scenario |
| POST | `/api/simulate/burst?count=20000` | Load test with a random mix |
| GET / POST | `/api/stats`, `/api/stats/reset` | Throughput and processing time |

## Results

Measured locally with `POST /api/stats/reset` then `POST /api/simulate/burst?count=20000`.
Fill in your own numbers:

| Metric | Value |
|---|---|
| Events processed | 20,000 |
| Throughput | _ events/sec |
| Avg processing time per event | _ ms |
| Incidents created from 20,000 events | _ (one per distinct failure) |

## Tests

```bash
cd triage-service && mvn test
```

Unit tests cover fingerprinting (same problem, different IDs → same incident), severity rules
and secret redaction.

## Next steps

- Replace keyword retrieval with embeddings in PostgreSQL (pgvector).
- Split the simulator into its own service.
- Add an evaluation set of labelled incidents and report root-cause accuracy.
- Deploy with a public demo link.
