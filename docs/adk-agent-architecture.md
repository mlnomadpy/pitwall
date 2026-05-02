# ADK Agent Architecture

Companion to [ADR-019](adr/019-adk-multi-agent-backend.md),
[ADR-020](adr/020-adk-agent-architecture-refactor.md), and
[ADR-021](adr/021-adk-second-audit.md).

**Current state:** All phases implemented, 358/358 tests passing as of 2026-05-01.

---

## Guiding constraints

1. **ADK never touches the hot path.** In-drive coaching (< 100 ms) stays as `RuleCoach` + `CoachArbiter`. ADK is paddock-only.
2. **DuckDB writes belong to the bridge.** All ADK tools query DuckDB `read_only=True`. The bridge is the sole writer — except `save_voice_scripts`, which writes to `tools/audio_cache/` (JSON files, not DuckDB).
3. **Native LiteRT-LM — no Ollama or LiteLLM.** ADK connects to `lit serve --port 8001` via `Gemini(base_url="localhost:8001", model="gemma-4-e4b")`. E4B is the paddock model. The in-drive E2B `litert_lm.Engine` is in-process and completely separate.
4. **Two runtimes, one ecosystem:**

    | Path | Runtime | Model | How invoked | Latency budget |
    |---|---|---|---|---|
    | In-drive (hot) | `litert_lm.Engine` (in-process) | Gemma 4 E2B | Direct API | < 100 ms |
    | Paddock (ADK) | `lit serve` HTTP server | Gemma 4 E4B | `Gemini(base_url=...)` | 2–15 s |

5. **Routing is deterministic Python.** `PitwallOrchestrator` uses `_classify_intent()` — a keyword classifier — not LLM routing. This eliminates mis-routing between similar agents.
6. **SQL queries are bounded.** `query_pitwall_db` enforces `LIMIT 500` and rejects non-SELECT. No agent can blow the context window via a table scan.
7. **All agent calls go through `run_adk()`.** `BaseAgent` has no `.run()` shortcut; the canonical path is `Runner.run_async()` wrapped in `asyncio.run()`.
8. **Persistent sessions for KV cache reuse.** `InMemorySessionService` sessions are reused per driver within a process lifetime. `lit serve` clones the KV cache across turns — session system instructions only prefill once per driving day.

---

## Full topology

```
pitwall_bridge.py (Flask, sync)
    │
    │  run_adk(prompt, user_id)          ← _get_or_create_session(user_id)
    │  _drain_adk_traces(pitwall_sid)    ← get_pending_traces() → agent_traces DuckDB
    │  _reset_adk_session(driver_id)     ← called by POST /session/start
    │
    ▼
Runner(PitwallOrchestrator, InMemorySessionService, plugins=[PitwallTracingPlugin])
    │
    ▼
PitwallOrchestrator(BaseAgent)
    _classify_intent(query) → deterministic keyword routing
    │
    ├── "debrief"  →  DebriefPipeline (SequentialAgent)
    │                   ├── DebriefDataPhase (ParallelAgent)  ← 3× concurrent
    │                   │     ├── HighlightFinderAgent  output_key=highlights_data
    │                   │     ├── TelemetryAgent        output_key=telemetry_data
    │                   │     └── PedagogyAgent         output_key=pedagogy_data
    │                   └── NarrativeAgentDebrief       reads {highlights_data}
    │                                                        {telemetry_data}
    │                                                        {pedagogy_data}
    │
    ├── "brief"    →  BriefPipeline (SequentialAgent)
    │                   ├── PedagogyAgent                output_key=pedagogy_data
    │                   └── NarrativeAgentBrief          reads {pedagogy_data}
    │
    └── QA intent  →  single specialist agent (14 paths)
          gold_lap       → GoldLapAgent
          weather        → WeatherAdaptationAgent
          session_plan   → SessionPlannerAgent
          incident       → IncidentReviewAgent
          race_pace      → RacePaceAgent
          goal           → GoalSettingAgent
          mental_map     → MentalMapAgent
          voice_script   → VoiceScriptAgent
          lap_comparison → LapComparisonAgent
          corner         → CornerCoachAgent
          progress       → ProgressTrackerAgent
          setup          → SetupAdvisorAgent
          mindset        → MindsetCoachAgent
          agent_meta     → AgentMetaAgent
          telemetry      → TelemetryAgent  (default)
```

---

## PitwallOrchestrator

`BaseAgent` subclass at `tools/adk_agents.py`. `_run_async_impl(ctx)` reads `ctx.user_content.parts[0].text`, calls `_classify_intent()`, then `async for event in pipeline.run_async(ctx): yield event`.

**Intent classifier** — `_classify_intent(query: str) -> str` — evaluates keywords top-to-bottom, first match wins:

| Keywords matched | Intent | Agent / pipeline |
|---|---|---|
| `debrief`, `how did i do`, `session summary` | `debrief` | DebriefPipeline |
| `brief`, `pre-session`, `before i go out` | `brief` | BriefPipeline |
| `gold lap`, `reference lap`, ` aj ` | `gold_lap` | GoldLapAgent |
| `weather`, `fog`, `conditions`, `greasy` | `weather` | WeatherAdaptationAgent |
| `practice plan`, `laps available`, `i have` | `session_plan` | SessionPlannerAgent |
| `incident`, `moment at`, `close call` | `incident` | IncidentReviewAgent |
| `race pace`, `stint`, `degradation`, `tyre drop` | `race_pace` | RacePaceAgent |
| `target`, `goal`, `pb target`, `what time should` | `goal` | GoalSettingAgent |
| `variance`, `consistency`, `mental map` | `mental_map` | MentalMapAgent |
| `audio`, `tts`, `cue script`, `voice script` | `voice_script` | VoiceScriptAgent |
| `vs lap`, `compare lap`, `why was lap` | `lap_comparison` | LapComparisonAgent |
| `turn `, `corner`, `carousel`, ` t10` | `corner` | CornerCoachAgent |
| `progress`, `improving`, `getting faster` | `progress` | ProgressTrackerAgent |
| `setup`, `understeer`, `car feel` | `setup` | SetupAdvisorAgent |
| `frustrated`, `plateau`, `not working` | `mindset` | MindsetCoachAgent |
| `agent trace`, `slowest agent`, `tool call` | `agent_meta` | AgentMetaAgent |
| *(default)* | `telemetry` | TelemetryAgent |

---

## Pipelines

### DebriefPipeline

```python
_debrief_data_phase = ParallelAgent(
    name="DebriefDataPhase",
    sub_agents=[highlight_finder_agent, telemetry_agent, pedagogy_agent],
)
debrief_pipeline = SequentialAgent(
    name="DebriefPipeline",
    sub_agents=[_debrief_data_phase, _narrative_debrief],
)
```

Three data agents run concurrently. Each writes to `session.state` via `output_key`. `NarrativeAgentDebrief` runs after all three complete — wall-clock time is 1× the slowest data agent, not the sum.

### BriefPipeline

```python
brief_pipeline = SequentialAgent(
    name="BriefPipeline",
    sub_agents=[pedagogy_agent, _narrative_brief],
)
```

`PedagogyAgent` runs first, writes `pedagogy_data` to session state. `NarrativeAgentBrief` generates the pre-session brief from that structured context.

### Narrative agent instances

`_narrative_debrief` and `_narrative_brief` are separate `Agent` instances with identical instruction templates. Separate instances prevent session-state bleed if requests overlap. Both share the same template:

```
Session highlights: {highlights_data}
Telemetry analysis: {telemetry_data}
Pedagogy context:   {pedagogy_data}

[Output format rules + EMOTION tag instruction]
```

A third instance `narrative_agent` is used for QA paths.

---

## Agent catalogue

18 agents total. All use `_model = Gemini(base_url=..., model="gemma-4-e4b")`.

### Pipeline data agents (with `output_key`)

| Agent | `output_key` | Tools |
|---|---|---|
| `TelemetryAgent` | `telemetry_data` | `query_pitwall_db`, `get_session_highlights` |
| `HighlightFinderAgent` | `highlights_data` | `get_session_highlights`, `query_pitwall_db` |
| `PedagogyAgent` | `pedagogy_data` | `query_pitwall_db` |
| `NarrativeAgent` / `NarrativeAgentDebrief` / `NarrativeAgentBrief` | *(none)* | *(none)* |

### QA specialist agents

| Agent | Tools |
|---|---|
| `LapComparisonAgent` | `get_lap_delta`, `query_pitwall_db` |
| `CornerCoachAgent` | `get_corner_history`, `query_pitwall_db` |
| `ProgressTrackerAgent` | `get_progress_report`, `query_pitwall_db` |
| `SetupAdvisorAgent` | `get_setup_indicators`, `query_pitwall_db` |
| `MindsetCoachAgent` | `get_progress_report`, `get_corner_history`, `query_pitwall_db` |
| `GoldLapAgent` | `get_gold_lap_comparison`, `query_pitwall_db` |
| `WeatherAdaptationAgent` | `get_weather_adaptation_context`, `query_pitwall_db` |
| `SessionPlannerAgent` | `get_session_plan_context`, `query_pitwall_db` |
| `IncidentReviewAgent` | `get_incident_moments`, `query_pitwall_db` |
| `RacePaceAgent` | `get_race_pace_model`, `query_pitwall_db` |
| `GoalSettingAgent` | `get_goal_targets`, `get_progress_report`, `query_pitwall_db` |
| `MentalMapAgent` | `get_track_variance_map`, `query_pitwall_db` |
| `VoiceScriptAgent` | `get_audio_script_context`, `save_voice_scripts` |
| `AgentMetaAgent` | `get_agent_telemetry` |

---

## Tools specification

All 15 tools live in `tools/adk_tools.py`. All decorated with `@_adk_tool` (falls back to identity decorator when `google-adk` is not installed).

### `query_pitwall_db(sql)`
Read-only DuckDB query. Safety layer: rejects non-SELECT, auto-injects `LIMIT 500`.
Tables: `laps`, `telemetry`, `coaching_notes`, `telemetry_signals`, `sessions`, `driver_events`, `llm_friction`, `conversations`, `agent_traces`.

### `get_lap_delta(session_id, lap_a, lap_b)`
Frame-by-frame delta between two laps: time, speed, coast pct.

### `get_corner_history(driver_id, corner_name, n_sessions=10)`
Grade history + coaching notes + improvement trend for one corner across N sessions.

### `get_progress_report(driver_id, n_sessions=10)`
Multi-session arc: lap time trend, improving/regressing/stable corners, plateau detection.

### `get_setup_indicators(session_id)`
Telemetry patterns indicating car balance issues: coast ratio, steer oscillation, brake pressure.

### `get_session_highlights(session_id)`
Best lap, peak grip moment, coaching note counts, worst coast lap.

### `get_gold_lap_comparison(session_id)`
Driver's best lap vs AJ's gold standard. Corner-by-corner speed gap + lap-time leverage weights.

### `get_weather_adaptation_context(hour_local)`
Sonoma's 4 weather phases → concrete line, braking, and tyre warm-up advice per corner.

### `get_session_plan_context(driver_id, n_laps=10)`
Weakest corners + leverage weights → structured N-lap practice plan data.

### `get_incident_moments(session_id, combo_g_threshold, steer_spike_threshold)`
Over-limit grip events, emergency brakes, steering saves from telemetry.

### `get_race_pace_model(session_id)`
Lap degradation model: quali pace, race pace median, consistency score, degradation s/lap.

### `get_goal_targets(driver_id)`
Realistic PB targets from improvement rate. Top 3 corners by `(100 - score) × leverage`.

### `get_track_variance_map(session_id)`
Corner-by-corner speed variance from telemetry. High CV = inconsistent.

### `get_agent_telemetry(n_recent=50)`
Queries `agent_traces` table: slowest agents by avg latency, top tools by call count, recent trace rows.

### `get_audio_script_context(corner_name, driver_level)`
Returns corner tip, leverage pct, TROD voice examples, and script guidelines for VoiceScriptAgent.

### `save_voice_scripts(corner_name, scripts)`
Writes generated TTS phrases to `tools/audio_cache/<corner>.json`. Uses `fcntl.flock(LOCK_EX)` + `os.replace()` for atomic concurrent writes.

---

## Runner and invocation

```python
# adk_agents.py — all internal
_session_service = InMemorySessionService()
_runner = Runner(
    agent=coach_orchestrator,
    app_name="pitwall",
    session_service=_session_service,
    plugins=[PitwallTracingPlugin()],
)

# Public API — pitwall_bridge.py calls these
run_adk(prompt, user_id="driver") -> str      # sync, thread-safe via asyncio.run()
reset_driver_session(user_id)                  # expire session (call at /session/start)
get_pending_traces() -> list[dict]             # drain trace buffer for DuckDB write
```

`BaseAgent` has no `.run()` shortcut — `Runner.run_async()` is the only path.

---

## KV cache and persistent sessions

`lit serve` exposes no prefix-cache flags. KV reuse happens via **session cloning** at the ADK session layer: LiteRT-LM clones KV tensors for a reused session (<10 ms) rather than re-prefilling the system instruction tokens.

```
_driver_sessions: dict[str, str]    # user_id → ADK session_id
_session_turn_count: dict[str, int] # auto-reset at _SESSION_MAX_TURNS = 50
```

**Lifecycle:**
1. `POST /session/start` → `reset_driver_session(driver_id)` — intentional cold reset, acceptable once per day
2. First `run_adk()` call → creates ADK session, stores in `_driver_sessions`
3. Subsequent calls same day → `_session_service.get_session()` → LiteRT-LM clones KV cache
4. After 50 turns → auto-rotation to prevent context overflow
5. Process restart → all sessions lost (InMemory), next call creates fresh session

**Expected impact:** System instruction tokens (~100–300 per agent) skip re-prefill on warm calls → ~30–50% prefill reduction → ~0.5–3 s saved per call on Tensor G5 NPU.

---

## Agent telemetry (DuckDB)

`PitwallTracingPlugin(BasePlugin)` hooks into `Runner` and logs every agent run and tool call to a module-level deque. `pitwall_bridge.py` drains it after every `run_adk()` call.

```sql
CREATE TABLE agent_traces (
    id          INTEGER PRIMARY KEY,
    trace_id    VARCHAR,    -- ADK session UUID — groups one run_adk() call
    pitwall_sid VARCHAR,    -- pitwall session_id (empty for Q&A)
    agent_name  VARCHAR,
    event_type  VARCHAR,    -- 'agent' | 'tool'
    detail      VARCHAR,    -- tool name for tool events
    latency_ms  DOUBLE,     -- wall-clock ms for agent events; NULL for tool events
    success     BOOLEAN,
    ts          TIMESTAMP
);
```

**Useful queries:**

```sql
-- Which agents are slowest?
SELECT agent_name, ROUND(AVG(latency_ms), 1) as avg_ms, COUNT(*) as runs
FROM agent_traces WHERE event_type = 'agent'
GROUP BY agent_name ORDER BY avg_ms DESC;

-- Most-called tools
SELECT detail, COUNT(*) FROM agent_traces
WHERE event_type = 'tool' GROUP BY detail ORDER BY 2 DESC;

-- Full trace for one run_adk() call
SELECT agent_name, event_type, detail, latency_ms, ts
FROM agent_traces WHERE trace_id = ? ORDER BY ts;
```

`AgentMetaAgent` can query this table directly via `get_agent_telemetry` tool.

---

## Conversation persistence

All brief/debrief narratives and Q&A turns persist to the `conversations` table.

```sql
CREATE TABLE conversations (
    id           INTEGER PRIMARY KEY,
    session_id   VARCHAR,
    driver_id    VARCHAR,
    role         VARCHAR,   -- 'coach_brief' | 'coach_debrief' | 'user' | 'assistant'
    text         TEXT,
    focus_items  VARCHAR,   -- JSON array
    emotion      VARCHAR,
    recorded_at  TIMESTAMP DEFAULT now()
);
```

Q&A turns buffer in `_qa_histories` (in-memory, TTL = 1 hour) and flush to DuckDB on `POST /coach/ask/end`.

**Read endpoints:**
- `GET /conversations/<session_id>` — all turns for a session
- `GET /conversations/driver/<driver_id>` — brief/debrief history across sessions

---

## Bridge integration points

| Bridge call | What it does |
|---|---|
| `run_adk(prompt, user_id)` | Runs `PitwallOrchestrator` via `Runner`, returns final text |
| `_drain_adk_traces(pitwall_sid)` | Flushes `get_pending_traces()` → `agent_traces` DuckDB |
| `_reset_adk_session(driver_id)` | Expires ADK session (cold KV reset at session start) |
| `POST /session/start` | Calls `_reset_adk_session(driver_id)` automatically |
| `POST /coach/ask` | Calls `run_adk(prompt)`, buffers turns in `_qa_histories` |
| `POST /coach/ask/end` | Flushes `_qa_histories` to `conversations` table |
| `GET /coach/agents` | Returns `AGENT_REGISTRY` for Vue PWA discovery |

---

## What stays unchanged

- `RuleCoach` and `CoachArbiter` — hot path, untouched
- `LitertCoach.propose()` — still delegates to `RuleCoach` per ADR-017
- All existing Flask endpoints and their JSON contracts
- `llm_friction` table — still receives LLM performance metadata
- `litert_lm.Engine` in-process runtime (E2B, hot path)

---

## Startup (Termux, Pixel 10)

```bash
# Terminal 1 — LiteRT-LM paddock model (E4B, for ADK agents)
lit pull gemma-4-e4b
lit serve --port 8001

# Terminal 2 — Pitwall bridge (E2B loads in-process on first in-drive coaching call)
cd ~/pitwall/tools
python3 pitwall_bridge.py --coach litert \
    --litert-model ~/storage/shared/Pitwall/models/gemma-4-E2B-it.litertlm
```

Two terminal sessions. E2B is in-process (hot path, < 100 ms). E4B is `lit serve` (ADK paddock, 2–15 s). Both use the `litert-lm` ecosystem — no Ollama, no cloud.
