# Pitwall — Trustable AI Racing Coach

[![Tests](https://img.shields.io/badge/tests-358%20passed-2aa198?style=flat-square)](tests/)
[![Smoke](https://img.shields.io/badge/smoke-51%20assertions-2aa198?style=flat-square)](tools/smoke_test_endpoints.py)
[![Routes](https://img.shields.io/badge/routes-56-859900?style=flat-square)](docs/api.md)
[![ADRs](https://img.shields.io/badge/ADRs-21-b58900?style=flat-square)](docs/adr/index.md)
[![Agents](https://img.shields.io/badge/ADK%20agents-18-6c71c4?style=flat-square)](docs/adk-agent-architecture.md)
[![PWA design](https://img.shields.io/badge/PWA%20design-38%20screens-d33682?style=flat-square)](docs/vue/README.md)
[![Python](https://img.shields.io/badge/python-3.10+-3776ab?style=flat-square&logo=python&logoColor=white)](#install)
[![Platform](https://img.shields.io/badge/platform-macOS%20%7C%20Linux%20%7C%20Termux-1a3a52?style=flat-square)](#hardware)
[![Field test](https://img.shields.io/badge/Sonoma%20field%20test-May%2023%2C%202026-cb4b16?style=flat-square)](#status)
[![License](https://img.shields.io/badge/license-TBD-555555?style=flat-square)](#license)
[![Explainer](https://img.shields.io/badge/explainer-live-0058bd?style=flat-square)](https://storage.googleapis.com/pitwall-demo/pitwall-explainer.html)

> **[→ Project Explainer](https://storage.googleapis.com/pitwall-demo/pitwall-explainer.html)** — architecture, cue distribution, Sonoma corner doctrine, team.

A real-time, on-device coaching system for track-day drivers. Runs on a
Pixel 10 via Termux: ingests CAN telemetry over USB from the car's OBD-II
port, makes coaching decisions with on-device Gemma 4 (via litert-lm),
and pairs with a Vue PWA (`pitwall-web`, sibling repo, fully designed in
[`docs/vue/`](docs/vue/README.md), implementation pending) for
presentation. Built for the May 23, 2026 Sonoma Raceway field test —
**no cloud dependency** for the core coaching loop.

Three LLM tiers, all on-device:

| Tier | Model | Runtime | When |
|---|---|---|---|
| Hot path | — | `sonic_model` + canonical phrases | In-drive, < 50 ms |
| Warm path | Gemma 4 E2B | `litert_lm.Engine` (in-process) | In-drive brief cues, < 100 ms |
| Paddock | Gemma 4 E4B | `lit serve` + 18 ADK agents | Pre-brief, debrief, Q&A, 2–15 s |

<details>
<summary><strong>Table of contents</strong></summary>

- [Status](#status)
- [What this is / what this isn't](#what-this-is--what-this-isnt)
- [Repo layout](#repo-layout)
- [Quick start](#quick-start)
- [Architecture](#architecture)
- [ADK multi-agent coaching](#adk-multi-agent-coaching)
- [Performance budget](#performance-budget)
- [Hardware](#hardware)
- [Data privacy & residency](#data-privacy--residency)
- [Key documentation](#key-documentation)
- [Test status](#test-status)
- [Roadmap](#roadmap)
- [Frozen artefacts](#frozen-artefacts)
- [Team 2 (Intermediate, BMW M3)](#team-2-intermediate-bmw-m3)
- [Acknowledgements](#acknowledgements)
- [Contributing](#contributing)
- [Code style](#code-style)
- [Changelog](#changelog)
- [License](#license)

</details>

The architecture is documented across 21 ADRs. The four most recent focus on the ADK paddock backend:
[ADR-018](docs/adr/018-field-readiness-blockers-and-pedagogy-tuning.md)
(field-readiness + pedagogy tuning from the 2026-04-29 Team 2 review),
[ADR-019](docs/adr/019-adk-multi-agent-backend.md) (ADK multi-agent plan),
[ADR-020](docs/adr/020-adk-agent-architecture-refactor.md) (7 structural fixes: `PitwallOrchestrator`, `SequentialAgent`/`ParallelAgent` pipelines, SQL safety, output_key pattern), and
[ADR-021](docs/adr/021-adk-second-audit.md) (second audit: `Runner` API, KV cache via persistent sessions, `PitwallTracingPlugin` + DuckDB agent telemetry).

## What this is / what this isn't

**What this is:** an in-cabin AI race coach for solo track-day drivers,
running offline on a Pixel + USB-CAN. Hot-path coaching cues at < 50 ms
(sonic_model + canonical phrase library, no LLM), warm-path verbal
coaching from on-device Gemma 4 E2B (paddock pre-brief + post-session
debrief, via litert-lm), and a full 18-agent ADK paddock coaching system
backed by Gemma 4 E4B. Coach avatar emotion (12-state taxonomy) is
**Gemma-controlled** — the LLM emits an `[EMOTION: ...]` tag with each
reply, surfaced to the PWA so the sprite plays the matching animation.
Telemetry sinks into local DuckDB (including agent traces); analytics
run client-side; **no cloud is required for the core coaching loop.**

**What this isn't:** a Garmin Catalyst (post-session debrief, fixed
heuristics), an AiM Solo (pure logger, no coaching), or a cloud-only LLM
tool. The differentiator is *real-time coaching that works without cell
coverage*, grounded in published pedagogy (Ross Bentley) and adapted to
driver skill level.

## Status

- **Bridge**: shipped. 56 HTTP endpoints, 358 tests passing, 0 skipped,
  smoke-tested end-to-end against a real 8273-frame Sonoma VBO with 51
  assertions green.
- **CAN pipeline**: shipped. `python-can` reader + simulator + DBC; 6
  round-trip tests on `interface='virtual'`. USB-CAN device enumeration
  via pyserial — adapter VID:PID lookup table covers CANable, Macchina
  M2, Korlan USB2CAN, PEAK, Kvaser, FTDI ELM327, CH340.
- **Termux foreground service**: shipped. Drop-in install package in
  [`deploy/termux/`](deploy/termux/INSTALL.md).
- **Three-tier coach architecture (ADR-017)**: shipped. In-drive cues
  are canonical phrases (no LLM); paddock brief + debrief are
  Gemma-via-litert-lm; cloud Gemini removed entirely.
- **Field-readiness blockers + pedagogy tuning (ADR-018)**: shipped
  2026-04-30. LLM friction sink, audio ducker, 1D Kalman dead-reckoning,
  intermediate-driver pedagogy refit.
- **ADK multi-agent paddock backend (ADR-019–021)**: shipped 2026-05-01.
  18 agents, 15 tools, `PitwallOrchestrator(BaseAgent)`, `DebriefPipeline`
  with `ParallelAgent`, `BriefPipeline`, `Runner` + persistent sessions
  for KV cache reuse, `PitwallTracingPlugin` + `agent_traces` DuckDB
  table, `AgentMetaAgent`, multi-turn Q&A (`/coach/ask`). All phases
  shipped ahead of original post-Sonoma schedule.
- **`pitwall-web` Vue PWA**: design-only. 54 markdown files in
  [`docs/vue/`](docs/vue/README.md) — 38 screens specced, foundation
  + systems + sprite spec + character bible + Gemma-controlled emotion
  taxonomy + god navigation map. Code lives in a future sibling repo;
  implementation hasn't started.
- **Sonoma field test**: May 23, 2026.

## Repo layout

```
pitwall/
├── tools/
│   ├── pitwall_bridge.py             # 56-route Flask app, CAN reader hookup, SSE
│   ├── adk_agents.py                 # 18 ADK agents + PitwallOrchestrator + Runner
│   ├── adk_tools.py                  # 15 @_adk_tool functions backed by DuckDB
│   ├── audio_cache/                  # TTS phrase cache written by VoiceScriptAgent
│   ├── can_reader.py                 # python-can consumer → DuckDB sink
│   ├── can_simulator.py              # VBO replay or synthetic CAN producer
│   ├── dead_reckoning.py             # ADR-018: 1D Kalman filter for distance
│   ├── smoke_test_endpoints.py       # end-to-end test against a real VBO
│   ├── generate_sample_vbo.py        # 3-lap synthetic VBO
│   └── …                             # bulk import, gold-lap extraction, etc.
├── src/simulator/
│   ├── sonic_model.py + sonic_model_v2.py    # hot-path audio cue engine
│   ├── coach_engine.py               # rule + LiteRT-LM coach, capability gating
│   ├── session_analyzer.py           # post-session orchestrator
│   ├── analytics.py + corner_grader.py + highlight_finder.py
│   ├── driver_profile.py             # event-sourced profile store
│   ├── gold_standard.py + track_loader.py + track_builder.py
│   ├── sonoma.py + sonoma.json       # track constants + geometry
│   ├── pitwall_app.py + simulator.py # standalone replay TUI
│   └── lstm_predictor.py + lstm_predictor_v3.py + models/
├── data/
│   ├── dbc/pitwall.dbc               # synthetic DBC (29 signals)
│   ├── registry/obd2_pids.json       # signal registry seed (54 entries)
│   ├── tracks/sonoma.json + sonoma_real_gps.json
│   ├── reference/                    # gold-standard lap traces
│   └── markers/sonoma/               # marker thumbnails
├── deploy/
│   └── termux/                       # foreground-service install package
│       ├── INSTALL.md
│       ├── service/pitwall-bridge/{run, log/run}
│       └── boot/start-pitwall
├── docs/
│   ├── api.md                        # 56-endpoint reference
│   ├── adk-agent-architecture.md     # ADK 18-agent topology, tools, KV cache, tracing
│   ├── adk-implementation-plan.md    # as-built status + remaining backlog
│   ├── internal_architecture.md      # post-2026-04-28 backend topology
│   ├── vue/                          # CANONICAL pitwall-web design — 54 files
│   │   ├── README.md                 #   index of the journey
│   │   ├── screens/                  #   38 screen docs
│   │   └── …
│   ├── adr/                          # 21 ADRs (019–021 cover ADK)
│   └── …                             # pedagogy, telemetry-pipeline, etc.
├── tests/                            # 358 passing, 0 skipped
├── android/  +  android-app/         # FROZEN v1 native; deletes post-PWA
├── .claude/commands/adk.md           # /adk slash command — ADK reference + audit tool
├── mkdocs.yml
├── CHANGELOG.md
└── README.md
```

## Quick start

### Install

```bash
pip install flask duckdb requests python-can cantools pyserial
```

**On-device LLM coach — Gemma 4 E2B (warm path, in-drive):**

```bash
pip install litert-lm

# Download the model (~2.4 GB, public HuggingFace repo, no token needed):
litert-lm import \
    --from-huggingface-repo litert-community/gemma-4-E2B-it-litert-lm \
    gemma-4-E2B-it.litertlm gemma-4-e2b
# Lands at ~/.litert-lm/models/gemma-4-e2b/model.litertlm
```

**ADK multi-agent paddock backend — Gemma 4 E4B (pre-brief, debrief, Q&A):**

```bash
pip install google-adk

# Download E4B (~4 GB) and start the server:
lit pull gemma-4-e4b
lit serve --port 8001   # run in a separate terminal before starting the bridge
```

Both backends fall back silently when unavailable — the hot-path canonical
phrase coach always works.

> **Note:** earlier docs referenced `mediapipe` + `.task` files. We use
> `litert-lm` (cross-platform macOS / Linux / Termux) with `.litertlm`
> format. Per ADR-017.

### Run the bridge

```bash
# HTTP-only (no live CAN, no E4B server needed for basic operation)
python3 tools/pitwall_bridge.py --track src/simulator/sonoma.json
# →  ✓  sonic_model loaded
#    ✓  Track: Sonoma Raceway (12 corners)
#    ✓  ADK coach_orchestrator loaded — 18 agents (LiteRT-LM E4B)   ← if lit serve running
#    🏁  Pitwall Bridge v2 on http://127.0.0.1:8765

curl -s http://127.0.0.1:8765/health | python3 -m json.tool
```

### Live CAN ingest

```bash
# Dev/test (virtual bus, no hardware needed)
python3 tools/pitwall_bridge.py --can-channel pitwall_dev

# Replay a VBO file as CAN frames
python3 tools/can_simulator.py \
    --vbo "/path/Sonoma Intermediate - 1_47.5.vbo" \
    --channel pitwall_dev --speed 2.0

# Production — USB-CAN on Pixel via Termux
python3 tools/pitwall_bridge.py \
    --can-interface slcan --can-channel /dev/ttyACM0 \
    --can-dbc data/dbc/pitwall.dbc
```

### Run the test suite

```bash
python3 -m pytest tests/ -q
# → 358 passed, 0 skipped

python3 tools/smoke_test_endpoints.py --keep-db
# Ingests 8273-frame Sonoma VBO → exercises 56 endpoints → 51 assertions
```

### ADK paddock Q&A

```bash
# Start a session, run a debrief, ask a question:
curl -X POST http://127.0.0.1:8765/session/start \
  -H 'Content-Type: application/json' \
  -d '{"driver": "taha", "track": "sonoma"}'

curl -X POST http://127.0.0.1:8765/coach/debrief \
  -H 'Content-Type: application/json' \
  -d '{"session_id": "<sid>", "driver_id": "taha"}'

curl -X POST http://127.0.0.1:8765/coach/ask \
  -H 'Content-Type: application/json' \
  -d '{"driver_id": "taha", "session_id": "<sid>", "question": "Why was lap 4 faster than lap 2?"}'

# Discover available agents:
curl http://127.0.0.1:8765/coach/agents | python3 -m json.tool
```

## Architecture

```
┌──────────────────────────── Pixel 10 (in cabin) ─────────────────────────┐
│                                                                          │
│  USB-CAN adapter (CANable Pro / Macchina M2)                             │
│        │  slcan @ 500 kbps                                               │
│        ▼                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  Termux foreground service (deploy/termux/)                         │ │
│  │                                                                     │ │
│  │  ┌──────────────┐                                                   │ │
│  │  │ can_reader   │  python-can + cantools DBC decode                 │ │
│  │  └──────┬───────┘                                                   │ │
│  │         ▼                                                           │ │
│  │  ┌─────────────────────────────────────────────────────────────┐   │ │
│  │  │ DuckDB (pitwall_sessions.duckdb)                             │   │ │
│  │  │  telemetry · laps · coaching_notes · driver_events          │   │ │
│  │  │  llm_friction · conversations · agent_traces                │   │ │
│  │  └──────────────────────────────┬──────────────────────────────┘   │ │
│  │                                 ▼                                   │ │
│  │  ┌──────────────────────────────────────────────────────────────┐  │ │
│  │  │ HOT PATH  <50ms                                              │  │ │
│  │  │   sonic_model + RuleCoach + canonical phrase library         │  │ │
│  │  ├──────────────────────────────────────────────────────────────┤  │ │
│  │  │ WARM PATH  <100ms                                            │  │ │
│  │  │   LitertCoach (Gemma 4 E2B, litert_lm.Engine in-process)    │  │ │
│  │  ├──────────────────────────────────────────────────────────────┤  │ │
│  │  │ PADDOCK  2–15s                                               │  │ │
│  │  │   PitwallOrchestrator → 18 ADK agents → Gemma 4 E4B         │  │ │
│  │  │   (lit serve --port 8001, Tensor G5 NPU)                    │  │ │
│  │  │   brief · debrief · Q&A · voice scripts · agent telemetry   │  │ │
│  │  └──────────────────────────────┬───────────────────────────────┘  │ │
│  │                                 ▼                                   │ │
│  │  ┌──────────────────────────────────────────────────────────────┐  │ │
│  │  │ Flask HTTP server  127.0.0.1:8765  (56 endpoints)            │  │ │
│  │  │   GET  /cues/stream          → live coaching cues (SSE)      │  │ │
│  │  │   GET  /notifications        → async inbox (SSE)             │  │ │
│  │  │   POST /coach/brief          → pre-session brief             │  │ │
│  │  │   POST /coach/debrief        → post-session debrief          │  │ │
│  │  │   POST /coach/ask            → multi-turn Q&A                │  │ │
│  │  │   GET  /coach/agents         → agent registry (PWA)          │  │ │
│  │  │   GET  /session/<sid>/export.parquet                         │  │ │
│  │  └──────────────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────┬────────────────────────────────────────┘ │
│                               │                                          │
│                               ▼                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ pitwall-web (Vue 3 PWA)             — IN DESIGN —                  │  │
│  │ 38 screens specced in docs/vue/                                    │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

## ADK multi-agent coaching

The paddock tier uses [Google ADK](https://adk.dev/) with 18 specialist
agents routed by a deterministic keyword classifier — no LLM routing.

```
PitwallOrchestrator(BaseAgent)         ← keyword classifier, no LLM routing
├── DebriefPipeline (SequentialAgent)
│     ├── DebriefDataPhase (ParallelAgent)  ← 3 agents run concurrently
│     │     ├── HighlightFinderAgent        output_key=highlights_data
│     │     ├── TelemetryAgent              output_key=telemetry_data
│     │     └── PedagogyAgent               output_key=pedagogy_data
│     └── NarrativeAgentDebrief            reads {highlights_data}
│                                               {telemetry_data}
│                                               {pedagogy_data}
├── BriefPipeline (SequentialAgent)
│     ├── PedagogyAgent                    output_key=pedagogy_data
│     └── NarrativeAgentBrief
└── 15 QA specialist agents  →  GoldLapAgent · WeatherAdaptationAgent
                                SessionPlannerAgent · IncidentReviewAgent
                                RacePaceAgent · GoalSettingAgent
                                MentalMapAgent · VoiceScriptAgent
                                LapComparisonAgent · CornerCoachAgent
                                ProgressTrackerAgent · SetupAdvisorAgent
                                MindsetCoachAgent · AgentMetaAgent
                                TelemetryAgent (default)
```

**Key properties:**
- All 15 tools query DuckDB `read_only=True` with `LIMIT 500` enforced
- `PitwallTracingPlugin` logs every agent run + tool call to `agent_traces` DuckDB table
- Persistent ADK sessions per driver — `lit serve` clones KV cache across calls (~30–50% prefill reduction on warm requests)
- `POST /session/start` triggers `reset_driver_session()` for a clean session baseline

See [`docs/adk-agent-architecture.md`](docs/adk-agent-architecture.md) for the full topology, tool specs, KV cache design, and agent telemetry schema.

## Performance budget

| Path | Budget | Notes |
|---|---|---|
| Hot path — sonic_model cue | **< 50 ms** | [ADR-002](docs/adr/002-split-brain-arbiter.md) |
| In-drive canonical phrase (RuleCoach) | **< 100 ms** | [ADR-017](docs/adr/017-three-tier-coach-architecture.md) |
| Pre-brief LLM (Gemma 4 E2B, cold) | **6–8 s** | measured on Apple Silicon CPU |
| Post-session debrief LLM (E2B) | **8–12 s** | measured |
| ADK paddock — cold call (new session) | **2–15 s** | E4B via `lit serve`, Tensor G5 NPU |
| ADK paddock — warm call (persistent session) | **~30–50% less** | KV cache clone; session instructions pre-filled |
| CAN frame → DuckDB row | **< 5 ms** | `tools/can_reader.py` |
| Agent trace DuckDB write | **< 1 ms** | batch drain after `run_adk()` |
| End-to-end smoke (8273 frames + every endpoint) | **~12 s** | `tools/smoke_test_endpoints.py` |
| Parquet export (90-min session) | **< 500 ms** | `GET /session/<sid>/export.parquet` |

## Hardware

For the May 23 Sonoma field test:

| Item | Role | Notes |
|---|---|---|
| Pixel 10 | Compute + display + audio gateway | Tensor G5 NPU for E4B + E2B; Termux + bridge + PWA in Chrome |
| USB-CAN adapter | Reads OBD-II powertrain bus | CANable Pro (~$60) or Macchina M2 (~$90) |
| Powered USB-C OTG hub | Charge passthrough during 4-hour session | needs USB-PD passthrough |
| OBD-II cable | Connects USB-CAN adapter to car port | standard OBD-II to DB9 |
| Pixel Earbuds | Audio coaching output (TTS) | ANC + tight fit handle cabin noise |

Test target: 2003 BMW M3 (E46) at Sonoma Raceway.

## Data privacy & residency

**All coaching is on-device.** Every LLM call goes through `litert-lm`
or `lit serve` locally — pre-brief, debrief, Q&A, `/score`. There is no
cloud LLM dependency.

All telemetry, lap data, coaching notes, driver profiles, and **agent
traces** live in a local DuckDB on the device running the bridge. The
bridge's HTTP server binds to `127.0.0.1` only and is not externally
reachable without an explicit tunnel or `POST /spectator/token`.

The PWA's analytics flow uses `GET /session/<sid>/export.parquet` to
hydrate a client-side DuckDB-Wasm instance — Parquet lives in browser
OPFS only, never sent off-device.

## Key documentation

### Backend / bridge

- [`docs/api.md`](docs/api.md) — every endpoint with request/response shapes
- [`docs/adk-agent-architecture.md`](docs/adk-agent-architecture.md) — **ADK topology**: 18 agents, 15 tools, `PitwallOrchestrator`, `Runner`, KV cache, `agent_traces` schema
- [`docs/adk-implementation-plan.md`](docs/adk-implementation-plan.md) — as-built status + remaining backlog (SSE streaming, state scopes, LoopAgent)
- [`docs/internal_architecture.md`](docs/internal_architecture.md) — backend topology with mermaid diagrams
- [`docs/adr/index.md`](docs/adr/index.md) — all 21 architecture decisions

### Recent ADRs

- [ADR-019](docs/adr/019-adk-multi-agent-backend.md) — ADK multi-agent plan (conversations table, agent roster, Q&A endpoints)
- [ADR-020](docs/adr/020-adk-agent-architecture-refactor.md) — 7 structural fixes: `PitwallOrchestrator`, `SequentialAgent`/`ParallelAgent`, `output_key`, SQL safety, `save_voice_scripts`
- [ADR-021](docs/adr/021-adk-second-audit.md) — second audit: `Runner` API, race condition fix, persistent sessions + KV cache, `PitwallTracingPlugin` + `agent_traces`
- [ADR-018](docs/adr/018-field-readiness-blockers-and-pedagogy-tuning.md) — field-readiness: LLM friction sink, audio ducker, Kalman dead-reckoning, pedagogy refit
- [ADR-017](docs/adr/017-three-tier-coach-architecture.md) — three-tier coach; cloud Gemini removed

### Frontend (`pitwall-web`)

- [`docs/vue/README.md`](docs/vue/README.md) — canonical PWA design: 38 screens, emotion taxonomy, navigation map
- [`docs/vue/10-coach-emotions.md`](docs/vue/10-coach-emotions.md) — 12-emotion taxonomy, Gemma prompt contract
- [`docs/vue/11-navigation-map.md`](docs/vue/11-navigation-map.md) — god mermaid + per-screen reference
- [`docs/ux.md`](docs/ux.md) — UX principles: audio-first, silence-is-coaching, fail-open

### On-track operators

- [`deploy/termux/INSTALL.md`](deploy/termux/INSTALL.md) — Pixel + Termux foreground service install
- [`docs/hardware.md`](docs/hardware.md) — sensor + adapter + cable spec
- [`docs/sonoma_track_intelligence.md`](docs/sonoma_track_intelligence.md) — corner-by-corner reference + danger zones + weather phases

### Pedagogy

- [`docs/pedagogy.md`](docs/pedagogy.md) — Ross Bentley curriculum in the coaching engine
- [`docs/coaching-engine.md`](docs/coaching-engine.md) — rule + LiteRT-LM coach composition

### Build the docs site

```bash
pip install mkdocs-material
mkdocs serve -a 127.0.0.1:8889
```

## Test status

| Suite | Count | Notes |
|---|---|---|
| `tests/` (pytest) | **358 passed, 0 skipped** | Unit + integration; vendored fixtures, no network |
| `tests/test_can_pipeline.py` | 9 of 358 | Round-trip on `interface='virtual'` + ADR-018 dead-reckoner wiring |
| `tests/test_dead_reckoning.py` | 13 of 358 | ADR-018 1D Kalman filter unit suite |
| `tests/test_coach_engine_litert.py` | 8 of 358 | Live-Gemma tests; auto-skip when model file is absent |
| `tools/smoke_test_endpoints.py` | **51 assertions, 0 failed** | End-to-end against a real 8273-frame Sonoma VBO |

```bash
python3 -m pytest tests/test_bridge.py -q
python3 -m pytest tests/test_can_pipeline.py -q
python3 -m pytest tests/test_dead_reckoning.py -q
python3 -m pytest tests/test_coach_engine.py -q
python3 -m pytest tests/test_coach_engine_litert.py -q   # auto-skip without model
python3 -m pytest tests/test_session_analyzer.py -q
python3 tools/smoke_test_endpoints.py
```

## Roadmap

The May 23, 2026 Sonoma Raceway field test is the load-bearing milestone.

**Before Sonoma (3 weeks):**

- **Scaffold `pitwall-web`** — Vue 3 + Vite + Tailwind PWA per [`docs/vue/`](docs/vue/README.md). MVP: boot path → session loop → 1 analytics screen → tutorial overlay. Bridge contract is complete.
- **Pre-rendered TTS phrase library** — bake canonical phrases per coach into MP3 clips, packaged into `pitwall-web/public/audio/coaches/<id>/`
- **Per-car DBC packs** — Subaru GR86 next; hardware enumeration table ready to extend

**Post-Sonoma:**

- **ADK SSE streaming** — `POST /coach/ask/stream` with `RunConfig(streaming_mode=SSE)` so the Vue PWA shows tokens as they arrive (critical for 2–15s E4B latency)
- **ADK state scopes** — `user:` prefix for driver preferences persisting across sessions; `app:` for shared track conditions
- **Vue PWA paddock Q&A screen** — connects to `/coach/ask` + `/coach/agents`
- **Multi-track support** — Laguna Seca and Thunderhill slot ready in `data/tracks/`
- **Sprite generation** — nano-banana prompts ready in [`docs/vue/assets/reference-sheet-source.md`](docs/vue/assets/reference-sheet-source.md); ~56 frames for 4 non-T-Rod coaches
- **Public release + license** — TBD post-field-test
- **Framework refactor** — per ADR-018: abstract `CoachContext`, parameterise LSTM input layer, decouple spatial awareness engine

## Frozen artefacts

`android/` and `android-app/` contain the v1 Flutter + native Kotlin
implementation. No longer the active frontend — see
[ADR-016](docs/adr/016-can-bus-ingest-and-frontend-pivot.md). Both will
be removed once `pitwall-web` reaches feature parity.

## Team 2 (Intermediate, BMW M3)

| Role | Person |
|------|--------|
| Tech Lead | Hemanth HM |
| Edge / Telemetry | Simon Margolis |
| AGY Pipeline | Taha Bouhsine |
| Data Reasoning | Vijay Vivekanand |
| UX / Frontend | Aileen Villanueva |

## Acknowledgements

- **Ross Bentley**, *Performance Driving Illustrated* — pedagogical foundation for every coaching rule. Distilled into 9 concepts in `coach_engine.match_bentley_concept`.
- **The T-Rod Sonoma transcript** — track-specific coaching anchors (the bridge, the K-wall bend, Calamity Corner) that make verbal coaching feel native to Sonoma.
- **Google ADK** — multi-agent orchestration framework. `PitwallOrchestrator`, `SequentialAgent`, `ParallelAgent`, `BasePlugin` and `Runner` power the 18-agent paddock backend.
- **Google AI Edge / `litert-lm`** — on-device Gemma 4 inference, cross-platform. Replaced the original MediaPipe Genai approach.
- **Racelogic VBO format** — canonical telemetry file format consumed by simulator and post-session import.
- **python-can** + **cantools** — CAN ingest stack.
- **DuckDB** — single-process SQL persistence with first-class Parquet support. Now stores coaching, conversations, and agent traces in one file.
- **Team 2 architecture review (2026-04-29, "CONDITIONAL PASS")** — punch list that became ADR-018: LLM friction logging, audio ducker, Kalman dead-reckoning, intermediate-driver pedagogy refit.

## Contributing

Pitwall is an internal sprint repository for the May 23, 2026 Sonoma
field test. External contributions are not currently accepted.

For team members:

- Branch from `master`; merge via PR. Tests must pass before merge.
- Run `python3 -m pytest tests/ -q` and `python3 tools/smoke_test_endpoints.py` before opening a PR that touches ingest, lap detection, or the sink.
- New endpoints get an entry in [`docs/api.md`](docs/api.md) plus a test in `tests/test_bridge.py`.
- New ADK agents get an entry in `AGENT_REGISTRY` in `adk_agents.py` and a row in the agent catalogue in [`docs/adk-agent-architecture.md`](docs/adk-agent-architecture.md).
- Architectural changes get a new ADR in `docs/adr/` (ADR-NNN-slug.md). Update `docs/adr/index.md` in the same PR.

## Code style

- Python 3.10+. Type hints encouraged on new code.
- Standard PEP 8; 4-space indent; double quotes; f-strings.
- Module-level docstrings on every public file; short docstrings on exported functions.
- Imports: stdlib → third-party → local, separated by blank lines; no star-imports.
- Side effects in `if __name__ == "__main__":` only.
- Tests: real-shape fixtures over mocks; round-trip tests on virtual buses preferred.
- New ADK tools: `@_adk_tool` decorator, SQL safety (`LIMIT 500`, non-SELECT rejected), file locks for any writes (`fcntl.flock`).

## Changelog

Notable changes by date in [CHANGELOG.md](CHANGELOG.md). Architecture decisions in [`docs/adr/`](docs/adr/index.md).

## License

TBD — to be added before public release.
