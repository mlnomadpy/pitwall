# Pitwall — Trustable AI Racing Coach

[![Tests](https://img.shields.io/badge/tests-358%20passed-2aa198?style=flat-square)](tests/)
[![Smoke](https://img.shields.io/badge/smoke-51%20assertions-2aa198?style=flat-square)](tools/smoke_test_endpoints.py)
[![Routes](https://img.shields.io/badge/routes-56-859900?style=flat-square)](docs/api.md)
[![ADRs](https://img.shields.io/badge/ADRs-18-b58900?style=flat-square)](docs/adr/index.md)
[![PWA design](https://img.shields.io/badge/PWA%20design-38%20screens-d33682?style=flat-square)](docs/vue/README.md)
[![Python](https://img.shields.io/badge/python-3.10+-3776ab?style=flat-square&logo=python&logoColor=white)](#install)
[![Platform](https://img.shields.io/badge/platform-macOS%20%7C%20Linux%20%7C%20Termux-1a3a52?style=flat-square)](#hardware)
[![Field test](https://img.shields.io/badge/Sonoma%20field%20test-May%2023%2C%202026-cb4b16?style=flat-square)](#status)
[![License](https://img.shields.io/badge/license-TBD-555555?style=flat-square)](#license)
[![Explainer](https://img.shields.io/badge/explainer-live-0058bd?style=flat-square)](https://storage.googleapis.com/pitwall-demo/pitwall-explainer.html)

> **[→ Project Explainer](https://storage.googleapis.com/pitwall-demo/pitwall-explainer.html)** — architecture, cue distribution, Sonoma corner doctrine, team.

A real-time, on-device coaching system for track-day drivers. Runs on a
Pixel 10 via Termux: ingests CAN telemetry over USB from the car's OBD-II
port, makes coaching decisions with on-device Gemma 4 E2B (via litert-lm)
and a 56-endpoint Python brain backed by DuckDB, and pairs with a Vue PWA
(`pitwall-web`, sibling repo, fully designed in
[`docs/vue/`](docs/vue/README.md), implementation pending) for
presentation. Built for the May 23, 2026 Sonoma Raceway field test —
**no cloud dependency** for the core coaching loop (per ADR-017, all
LLM work runs on-device through litert-lm).

<details>
<summary><strong>Table of contents</strong></summary>

- [Status](#status)
- [What this is / what this isn't](#what-this-is--what-this-isnt)
- [Repo layout](#repo-layout)
- [Quick start](#quick-start)
- [Architecture](#architecture)
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

The architecture is documented across 18 ADRs. The four most recent —
[ADR-015](docs/adr/015-universal-telemetry-sink.md) (universal telemetry
sink), [ADR-016](docs/adr/016-can-bus-ingest-and-frontend-pivot.md) (USB
CAN ingest + Vue PWA frontend), [ADR-017](docs/adr/017-three-tier-coach-architecture.md)
(three-tier coach: in-drive canonical phrases, paddock LLM brief +
debrief; cloud Gemini removed), and
[ADR-018](docs/adr/018-field-readiness-blockers-and-pedagogy-tuning.md)
(field-readiness blockers + pedagogy tuning from the 2026-04-29 Team 2
review) — record the late-April pivot from BLE + Flutter to USB-CAN +
PWA, the on-device-only LLM commitment, and the May-23-driven shift to
transition-focused intermediate-driver coaching. The Flutter / Kotlin
trees in `android/` and `android-app/` are frozen as v1 reference; they
will be removed once `pitwall-web` reaches feature parity.

## What this is / what this isn't

**What this is:** an in-cabin AI race coach for solo track-day drivers,
running offline on a Pixel + USB-CAN. Hot-path coaching cues at < 50 ms
(sonic_model + canonical phrase library, no LLM), warm-path verbal
coaching from on-device Gemma 4 E2B (paddock pre-brief + post-session
debrief, both via litert-lm). Coach avatar emotion (12-state taxonomy)
is **Gemma-controlled** — the LLM emits an `[EMOTION: ...]` tag with
each reply, surfaced to the PWA so the sprite plays the matching
animation. Telemetry sinks into local DuckDB; analytics run
client-side; **no cloud is required for the core coaching loop.**

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
- **Universal telemetry sink (ADR-015)**: shipped. Phase 1–4 all
  accepted; capability-aware coach gating live.
- **Three-tier coach architecture (ADR-017)**: shipped. In-drive cues
  are canonical phrases (no LLM); paddock brief + debrief are
  Gemma-via-litert-lm; cloud Gemini removed entirely. Verified
  end-to-end on Apple Silicon CPU — `LitertCoach.brief()` ~6.7 s,
  `.debrief()` ~10 s.
- **Field-readiness blockers + pedagogy tuning (ADR-018)**: shipped
  2026-04-30 in response to the Team 2 architecture review. LLM
  friction sink (`GET /diagnostics/llm_friction` + `llm_friction`
  DuckDB table); audio ducker (4-layer audio model with tactical-tone
  duck during TTS, `expected_tts_ms` cue hint); 1D Kalman dead-reckoning
  for distance (`tools/dead_reckoning.py` fuses CAN speed + IMU + GPS,
  closes the 5.8 m / 100 ms GPS gap at 130 mph); intermediate-driver
  pedagogy refit (nothing-time penalty, brake-release prompts,
  slip-angle oscillation rule, highlight-reel debrief opener).
  Long-term framework refactor deferred post-Sonoma.
- **PWA bridge contract**: shipped. The 7 endpoints the upcoming
  `pitwall-web` PWA depends on are all in place — `?include_can_state`
  on `/signals/registry`, `_live` synthetic session, `[EMOTION:]` tag
  on `/coach/brief` + `/coach/debrief`, `GET /session/<sid>/export.parquet`,
  `GET /cues/stream` SSE, `GET /notifications` SSE, `POST /spectator/token`.
- **`pitwall-web` Vue PWA**: design-only. 54 markdown files in
  [`docs/vue/`](docs/vue/README.md) — 38 screens specced, foundation
  + systems + sprite spec + character bible + Gemma-controlled emotion
  taxonomy + god navigation map. Code lives in a future sibling repo;
  implementation hasn't started.
- **Sonoma field test**: 23 days out (May 23, 2026).

## Repo layout

```
pitwall/
├── tools/
│   ├── pitwall_bridge.py             # 56-route Flask app, CAN reader hookup, SSE
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
│   ├── internal_architecture.md      # post-2026-04-28 backend topology
│   ├── ux.md                         # underlying UX principles
│   ├── pitwall-web-design.md         # legacy GBA-style PWA UX sketch
│   ├── vue/                          # CANONICAL pitwall-web design — 54 files
│   │   ├── README.md                 #   index of the journey
│   │   ├── 00..03                    #   philosophy / visual / sprites / characters
│   │   ├── 04..09                    #   state / routing / audio / controls / etc.
│   │   ├── 10-coach-emotions.md      #   Gemma-controlled emotion taxonomy
│   │   ├── 11-navigation-map.md      #   god mermaid + per-screen reference
│   │   ├── screens/                  #   38 screen docs
│   │   ├── assets/                   #   sprite naming + nano-banana prompts
│   │   └── journal/
│   ├── adr/                          # 18 ADRs
│   └── …                             # pedagogy, telemetry-pipeline, etc.
├── tests/                            # 358 passing, 0 skipped
├── android/  +  android-app/         # FROZEN v1 native; deletes post-PWA
├── mkdocs.yml
├── CHANGELOG.md
└── README.md
```

## Quick start

### Install

```bash
pip install flask duckdb requests python-can cantools pyserial
```

Optional but recommended — the on-device LLM coach (Gemma 4 E2B via
litert-lm). Without it the coach falls back to canonical phrases for
every path; with it pre-brief and debrief get real generated narratives:

```bash
pip install litert-lm

# Download the model (~2.4 GB, public HuggingFace repo, no token needed):
litert-lm import \
    --from-huggingface-repo litert-community/gemma-4-E2B-it-litert-lm \
    gemma-4-E2B-it.litertlm gemma-4-e2b
# Lands at ~/.litert-lm/models/gemma-4-e2b/model.litertlm
# coach_engine.LitertCoach finds it automatically
```

> **Note:** earlier docs referenced `mediapipe` + `gemma-4-E2B-it.task`.
> The desktop pip `mediapipe` package doesn't ship the LLM Inference
> submodule; we switched to Google's official `litert-lm` package which
> works cross-platform on macOS / Linux / Termux. The model file format
> is `.litertlm`, not `.task`. Per ADR-017.

### Run the bridge

```bash
# HTTP-only (no live CAN)
python3 tools/pitwall_bridge.py --track src/simulator/sonoma.json
# →  ✓  sonic_model loaded
#    ✓  Track: Sonoma Raceway (12 corners)
#    ✓  signal_registry seeded (54 entries from data/registry/obd2_pids.json)
#    🏁  Pitwall Bridge v2 on http://127.0.0.1:8765

# Verify
curl -s http://127.0.0.1:8765/health | python3 -m json.tool
```

### Live CAN ingest

The bridge can spawn a `python-can` reader thread on startup. Same code
path for development, testing, and production — only the python-can
interface differs.

```bash
# Cross-platform dev/test (no kernel modules, no permissions, runs in CI)
python3 tools/pitwall_bridge.py --can-channel pitwall_dev

# In another terminal: replay a VBO file as CAN frames
python3 tools/can_simulator.py \
    --vbo "/path/Sonoma Intermediate - 1_47.5.vbo" \
    --channel pitwall_dev --speed 2.0

# Or synthesise three Sonoma laps
python3 tools/can_simulator.py --synthetic --channel pitwall_dev --speed 0
```

For Linux dev box with `vcan0`:

```bash
sudo modprobe vcan && sudo ip link add dev vcan0 type vcan && sudo ip link set up vcan0
python3 tools/pitwall_bridge.py --can-interface socketcan --can-channel vcan0
```

For production over USB-CAN on the Pixel via Termux:

```bash
python3 tools/pitwall_bridge.py \
    --can-interface slcan --can-channel /dev/ttyACM0 \
    --can-dbc data/dbc/pitwall.dbc
```

### Run the test suite

```bash
python3 -m pytest tests/ -q
# → 358 passed, 0 skipped
```

End-to-end smoke against a real Racelogic VBO:

```bash
python3 tools/smoke_test_endpoints.py --keep-db
# Ingests 8273-frame Sonoma VBO → exercises 56 endpoints → 51 assertions
```

### PWA bridge contract (the 7 routes the upcoming Vue PWA depends on)

```bash
# Live CAN reader status (PWA's Pit Stall Setup screen):
curl -s 'http://127.0.0.1:8765/signals/registry?include_can_state=true' \
  | jq '.can_state | {loaded, connected, frames_per_second, usb_devices}'

# Live car state (after ?include_can_state=true is verified):
curl -s 'http://127.0.0.1:8765/session/_live/signals?names=rpm,speed_ms,oil_temp_c'

# Stream coaching cues (PWA's on-track HUD subscribes to this):
curl -N 'http://127.0.0.1:8765/cues/stream?session_id=demo'

# Async event inbox (PWA's notification center):
curl -N 'http://127.0.0.1:8765/notifications?driver=taha'

# Export a session as Parquet for client-side DuckDB-Wasm queries:
curl -o session.parquet 'http://127.0.0.1:8765/session/<sid>/export.parquet'

# Generate a read-only spectator token:
curl -X POST -H 'Content-Type: application/json' \
  -d '{"session_id":"<sid>"}' 'http://127.0.0.1:8765/spectator/token'

# /coach/brief and /coach/debrief responses include a Gemma-emitted
# `emotion` field driving the avatar animation in the PWA.
```

## Architecture

```
┌──────────────────────────── Pixel 10 (in cabin) ─────────────────────────┐
│                                                                          │
│  USB-C OTG hub                                                           │
│        │                                                                 │
│        ▼                                                                 │
│  USB-CAN adapter (CANable Pro / Macchina M2)                             │
│        │  slcan @ 500 kbps                                               │
│        ▼                                                                 │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  Termux foreground service (deploy/termux/)                        │ │
│  │  ──────────────────────────────────────                            │ │
│  │                                                                    │ │
│  │  ┌──────────────┐                                                  │ │
│  │  │ can_reader   │  python-can + cantools DBC decode                │ │
│  │  └──────┬───────┘                                                  │ │
│  │         ▼                                                          │ │
│  │  ┌──────────────────────┐  ┌──────────────────────────┐            │ │
│  │  │ telemetry table      │  │ telemetry_signals table  │  (DuckDB)  │ │
│  │  │ (11 wide canonicals) │  │ (ADR-015 tall sink)      │            │ │
│  │  └──────────┬───────────┘  └────────────┬─────────────┘            │ │
│  │             ▼                           ▼                          │ │
│  │  ┌──────────────────────────────────────────────────┐              │ │
│  │  │ coach_engine + sonic_model                       │              │ │
│  │  │ + LiteRT-LM Gemma 4 E2B (on-device)              │              │ │
│  │  └─────────────────────────┬────────────────────────┘              │ │
│  │                            ▼                                       │ │
│  │  ┌──────────────────────────────────────────────────┐              │ │
│  │  │ Flask HTTP server on 127.0.0.1:8765              │              │ │
│  │  │ 56 endpoints (api.md) incl. SSE streams:         │              │ │
│  │  │   GET /cues/stream      → live coaching cues     │              │ │
│  │  │   GET /notifications    → async inbox            │              │ │
│  │  │   POST /spectator/token → read-only mirror auth  │              │ │
│  │  │   GET /session/<sid>/export.parquet              │              │ │
│  │  └──────────────────────────────────────────────────┘              │ │
│  └────────────────────────────────┬───────────────────────────────────┘ │
│                                   │                                     │
│                                   ▼                                     │
│  ┌───────────────────────────────────────────────────────┐              │
│  │ pitwall-web (Vue 3 PWA)         — IN DESIGN —         │              │
│  │ Title · Pit Stall Setup · Pre-Brief · Track Walk ·    │              │
│  │ On-Track HUD · Stage Clear · Trainer Card ·           │              │
│  │ Analysis Hub (lap-times / corners / straights /       │              │
│  │   track / evolution / pedals) · Replay · Coach Codex  │              │
│  │ 38 screens specced in docs/vue/                       │              │
│  └───────────────────────────────────────────────────────┘              │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

See [ADR-016](docs/adr/016-can-bus-ingest-and-frontend-pivot.md) for the
detailed rationale and migration plan.

## Performance budget

Concrete latency targets the architecture is engineered against. All
numbers are wall-clock from telemetry frame to spoken cue or to first
visible HUD pixel.

| Path | Budget | Source |
|---|---|---|
| Hot path — sonic_model cue | **< 50 ms** | [ADR-002](docs/adr/002-split-brain-arbiter.md) |
| In-drive canonical phrase (RuleCoach, no LLM) | **< 100 ms** | `src/simulator/coach_engine.py`, [ADR-017](docs/adr/017-three-tier-coach-architecture.md) |
| Pre-brief LLM (Gemma 4 E2B via litert-lm, on-device) | **6–8 s** measured on Apple Silicon CPU | `LitertCoach.brief()` |
| Post-session debrief LLM (same engine) | **8–12 s** measured | `LitertCoach.debrief()` |
| Short Gemma cue (~30 tokens out) | **~470 ms** measured | `LitertCoach._generate()` |
| CAN frame → DuckDB row | **< 5 ms** | `tools/can_reader.py` |
| End-to-end smoke (8273 frames + every endpoint) | **~12 s** | `tools/smoke_test_endpoints.py` |
| Capability recompute on session import | **< 200 ms** for a 90-min session | `_compute_capabilities` |
| Parquet export (90-min session, telemetry table) | **< 500 ms** | `GET /session/<sid>/export.parquet` |

## Data privacy & residency

**All coaching is on-device.** Per
[ADR-017](docs/adr/017-three-tier-coach-architecture.md), every LLM
call goes through `litert-lm` running locally — pre-brief, debrief,
`/score`. There is no cloud LLM dependency in the bridge as of
2026-04-29; cloud Gemini code paths and the `GEMINI_API_KEY`
environment variable were removed entirely.

All telemetry, lap data, coaching notes, and driver profiles live in a
local DuckDB on the device that runs the bridge. The bridge's HTTP
server binds to `127.0.0.1` only and is not externally reachable
without an explicit `adb reverse` tunnel, a `POST /spectator/token`
read-only token, or a deliberate firewall rule.

DuckDB files live at `tools/pitwall_sessions.duckdb` (dev) or wherever
the Termux service writes them (production). Backups, exports, and
sharing are user-driven; the bridge never auto-syncs.

The PWA's analytics flow uses `GET /session/<sid>/export.parquet` to
hydrate a client-side DuckDB-Wasm instance — Parquet files live in
the browser's OPFS cache only, never sent off-device.

## Hardware

For the May 23 Sonoma field test:

| Item | Role | Notes |
|---|---|---|
| Pixel 10 | Compute + display + audio gateway | runs Termux + bridge + PWA in Chrome |
| USB-CAN adapter | Reads OBD-II powertrain bus | CANable Pro (~$60) or Macchina M2 (~$90); both present as `/dev/ttyACM0` via slcan |
| Powered USB-C OTG hub | Charge passthrough during 4-hour session | needs USB-PD passthrough |
| OBD-II cable | Connects USB-CAN adapter to car port | standard OBD-II to DB9 |
| Pixel Earbuds | Audio coaching output (TTS) | optional but recommended; ANC + tight fit handle cabin noise |

Test target: 2003 BMW M3 (E46) at Sonoma Raceway. Other cars supported by
adding their DBC alongside `data/dbc/pitwall.dbc` via `--can-dbc`.

## Key documentation

### For bridge / backend devs

- [`docs/api.md`](docs/api.md) — every endpoint with request / response
  shapes and the `--can-channel` startup flags.
- [`docs/internal_architecture.md`](docs/internal_architecture.md) — the
  as-shipped backend topology with mermaid diagrams.
- [`docs/adr/index.md`](docs/adr/index.md) — all 18 architecture decision
  records.
- [`docs/adr/015-universal-telemetry-sink.md`](docs/adr/015-universal-telemetry-sink.md)
  — the registry + tall sink + capability model.
- [`docs/adr/016-can-bus-ingest-and-frontend-pivot.md`](docs/adr/016-can-bus-ingest-and-frontend-pivot.md)
  — the USB-CAN + Vue PWA pivot.
- [`docs/adr/017-three-tier-coach-architecture.md`](docs/adr/017-three-tier-coach-architecture.md)
  — three-tier coach (canonical / paddock-LLM / paddock-LLM); cloud Gemini
  removed.
- [`docs/adr/018-field-readiness-blockers-and-pedagogy-tuning.md`](docs/adr/018-field-readiness-blockers-and-pedagogy-tuning.md)
  — field-readiness blockers + pedagogy tuning from the 2026-04-29 Team 2
  review (LLM friction sink, audio ducker, Kalman dead-reckoning,
  intermediate-driver pedagogy).

### For frontend devs (`pitwall-web`)

- [`docs/vue/README.md`](docs/vue/README.md) — **canonical PWA design**.
  Index of 54 docs covering 38 screens, foundation (philosophy / visual
  language / sprite spec / character bible / Gemma-controlled emotions),
  systems (state / routing / audio / controls / animation / tech stack /
  navigation map), and assets.
- [`docs/vue/11-navigation-map.md`](docs/vue/11-navigation-map.md) — the
  god mermaid + zoomed subgraphs + per-screen incoming/outgoing reference.
- [`docs/vue/10-coach-emotions.md`](docs/vue/10-coach-emotions.md) —
  12-emotion taxonomy, Gemma prompt contract, response shape, per-screen
  mapping.
- [`docs/vue/screens/_coach-speaks-modal.md`](docs/vue/screens/_coach-speaks-modal.md)
  — canonical "LLM is talking" pattern every Gemma moment uses.
- [`docs/ux.md`](docs/ux.md) — underlying UX principles (audio-first,
  silence-is-coaching, fail-open, confidence-shapes-phrasing,
  no-number-chasing).
- [`docs/pitwall-web-design.md`](docs/pitwall-web-design.md) — legacy
  GBA-style sketch superseded by `docs/vue/`; kept for history.
- [`docs/adr/013-frontend-backend-boundary.md`](docs/adr/013-frontend-backend-boundary.md)
  — the contract: frontend visualises, backend reasons.

### For on-track operators

- [`deploy/termux/INSTALL.md`](deploy/termux/INSTALL.md) — install
  procedure for the Pixel + Termux foreground service. Day-to-day
  operation, Doze survival check, troubleshooting.
- [`docs/hardware.md`](docs/hardware.md) — sensor + adapter + cable
  spec.
- [`docs/sonoma_track_intelligence.md`](docs/sonoma_track_intelligence.md)
  — corner-by-corner reference + danger zones + weather phases.

### For pedagogy / coaching content

- [`docs/pedagogy.md`](docs/pedagogy.md) — Ross Bentley curriculum
  distilled into the coaching engine.
- [`docs/coaching-engine.md`](docs/coaching-engine.md) — how rule and
  LiteRT-LM coaches compose.
- [`docs/markers.md`](docs/markers.md) — named-marker schema for
  Sonoma corner references ("the bridge", "the K-wall bend", …).

### Build the docs site

```bash
pip install mkdocs-material
mkdocs serve -a 127.0.0.1:8889
```

## Test status

| Suite | Count | Notes |
|---|---|---|
| `tests/` (pytest) | **358 passed, 0 skipped** | Unit + integration; vendored fixtures, no network |
| `tests/test_can_pipeline.py` | 9 of the 358 | Round-trip on `interface='virtual'` + ADR-018 dead-reckoner wiring |
| `tests/test_dead_reckoning.py` | 13 of the 358 | ADR-018 1D Kalman filter unit suite |
| `tests/test_coach_engine_litert.py` | 8 of the 358 | Live-Gemma tests; auto-skip when model file is absent |
| `tools/smoke_test_endpoints.py` | **51 assertions, 0 failed** | End-to-end against a real 8273-frame Sonoma VBO |

Run individually:

```bash
python3 -m pytest tests/test_bridge.py -q                # routes-level tests
python3 -m pytest tests/test_can_pipeline.py -q          # CAN round-trip + ADR-018 wiring
python3 -m pytest tests/test_dead_reckoning.py -q        # ADR-018 Kalman unit suite
python3 -m pytest tests/test_coach_engine.py -q          # coach + emotion extractor + new pedagogy rules
python3 -m pytest tests/test_coach_engine_litert.py -q   # live Gemma (skipped without model)
python3 -m pytest tests/test_session_analyzer.py -q      # post-session pipeline
python3 tools/smoke_test_endpoints.py                    # full-VBO smoke
```

The bridge is tested on macOS and Linux. Termux on a Pixel 10 is the
production target; install per
[`deploy/termux/INSTALL.md`](deploy/termux/INSTALL.md).

## Frozen artefacts

`android/` and `android-app/` contain the v1 Flutter + native Kotlin
implementation. They are no longer the active frontend — see
[ADR-016](docs/adr/016-can-bus-ingest-and-frontend-pivot.md) for the
rationale. Both directories will be removed once `pitwall-web` reaches
feature parity. Until then, they are not built, not tested, and not
recommended.

## Roadmap

The May 23, 2026 Sonoma Raceway field test is the load-bearing
milestone (23 days out). Open work in priority order:

- **Scaffold `pitwall-web`** — Vue 3 + Vite + Tailwind PWA per
  [`docs/vue/`](docs/vue/README.md). MVP scope: boot path → session loop
  → 1 analytics screen → tutorial overlay → polish; ~13 days estimated
  per [`docs/vue/README.md`](docs/vue/README.md). The bridge contract
  is complete.
- **Pre-rendered TTS phrase library** — bake the canonical phrases per
  coach (`docs/vue/06-audio-design.md`) into MP3 clips with per-phrase
  `emotion` tags, packaged into `pitwall-web/public/audio/coaches/<id>/`.
- **Per-car DBC packs** — Subaru GR86 next, then user-contributed DBCs
  for additional cars. Plug-in via `--can-dbc <path>`. Hardware
  enumeration table (`_USB_CAN_DEVICE_DB`) ready to extend.
- **Sprite generation for the 4 non-T-Rod coaches** — nano-banana
  prompts ready in [`docs/vue/assets/reference-sheet-source.md`](docs/vue/assets/reference-sheet-source.md);
  ~14 frames × 4 coaches = 56 frames to produce.
- **Video × telemetry sync** — `--video` flag on `tools/can_simulator.py`
  + byte-range MP4 serving on the bridge. Deferred from 2026-04-29.
- **Multi-track support** — Laguna Seca and Thunderhill have their own
  `data/tracks/<id>.json` slot ready; awaits gold-standard reference
  laps and corner geometry.
- **Public release + license** — TBD post-field-test.
- **Framework refactor (post-Sonoma)** — per ADR-018, the Team 2 review's
  long-term recommendations are queued for after May 23: abstract
  `CoachContext` → generic `StateContext`, parameterise the LSTM input
  layer for arbitrary time-series vectors, and decouple the spatial
  awareness engine from its 1D track-length loop to support 3D mapping.
  Don't start until the field test is done.

## Team 2 (Intermediate, BMW M3)

| Role | Person |
|------|--------|
| Tech Lead | Hemanth HM |
| Edge / Telemetry | Simon Margolis |
| AGY Pipeline | Taha Bouhsine |
| Data Reasoning | Vijay Vivekanand |
| UX / Frontend | Aileen Villanueva |

## Acknowledgements

- **Ross Bentley**, *Performance Driving Illustrated* — the pedagogical
  foundation for every coaching rule. Distilled into 9 concepts in
  `coach_engine.match_bentley_concept`.
- **The T-Rod Sonoma transcript** — track-specific coaching anchors
  (the bridge, the K-wall bend, Calamity Corner, the bump, the third
  tire stack) that make the verbal coaching feel native to Sonoma.
- **Racelogic VBO format** — the canonical telemetry file format that
  the simulator and post-session import paths consume.
- **python-can** + **cantools** — the CAN ingest stack.
- **DuckDB** — single-process SQL persistence with first-class Parquet
  support.
- **Google AI Edge / `litert-lm`** — on-device Gemma 4 E2B inference,
  cross-platform (macOS / Linux / Termux). Replaced the original
  MediaPipe Genai approach which doesn't ship LLM Inference for
  desktop Python.
- **Team 2 architecture review (2026-04-29, "CONDITIONAL PASS")** — the
  punch list that became [ADR-018](docs/adr/018-field-readiness-blockers-and-pedagogy-tuning.md):
  LLM friction logging, the audio ducker rule, Kalman dead-reckoning,
  and the intermediate-driver pedagogy refit (transitions over
  locations, lead the debrief with a positive moment).

## Contributing

Pitwall is an internal sprint repository for the May 23, 2026 Sonoma
field test. External contributions are not currently accepted; the
architecture and roadmap live in the ADR index for reference.

For team members:

- Branch from `master`; merge via PR. Tests must pass before merge.
- Run `python3 -m pytest tests/ -q` and `python3 tools/smoke_test_endpoints.py`
  before opening a PR that touches ingest, lap detection, or the sink.
- New endpoints get an entry in [`docs/api.md`](docs/api.md) with example
  request/response shapes plus a corresponding test in `tests/test_bridge.py`.
- Architectural changes get a new ADR in [`docs/adr/`](docs/adr/) following
  the numbering convention (ADR-NNN-slug.md). Update [`docs/adr/index.md`](docs/adr/index.md)
  in the same PR.
- Update the [Changelog](#changelog) for any user-visible change.
- Keep the `flutter/` and `android-app/` trees frozen — see [Frozen artefacts](#frozen-artefacts).

## Code style

Pitwall's Python is conventional and lightweight. No formal linter is
enforced — the goal is consistency with what's already in the repo.

### Python

- Python 3.10+. Type hints encouraged on new code; existing modules vary.
- Standard PEP 8 spacing; 4-space indent; double quotes preferred for
  strings; f-strings over `%` or `.format()`.
- Module-level docstrings on every public file; short docstrings on
  exported functions and dataclasses.
- Imports: stdlib → third-party → local, separated by blank lines; no
  star-imports.
- Side effects in `if __name__ == "__main__":` only. Importing a module
  must not start a server, open a file, or hit the network.

### Tests

- Tests live in `tests/`, mirror module structure where practical.
- Shared fixtures in `tests/conftest.py`. New fixtures go there, not in
  the test file that needs them, if they could be reused.
- Round-trip tests on virtual buses (`interface='virtual'` for CAN, Flask
  test client for HTTP) preferred over mocking. Real-shape data > stubs.
- Integration tests against committed fixtures are encouraged when they
  catch contract drift cheaply.

### Documentation

- Architecture decisions go in `docs/adr/<NNN>-<slug>.md` and are
  registered in `docs/adr/index.md`.
- New API surface is documented in `docs/api.md` *before* merge — the
  doc is the contract.
- Mermaid diagrams or ASCII art over external image dependencies. The
  docs render with mkdocs-material; keep them text-based and diff-able.

## Changelog

Notable changes by date in [CHANGELOG.md](CHANGELOG.md). The architecture
decisions live in [`docs/adr/`](docs/adr/index.md); the changelog is the
chronological log of what shipped when, cross-linked to the relevant ADR.

## License

TBD — to be added before public release.
