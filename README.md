# Pitwall — Trustable AI Racing Coach

[![Tests](https://img.shields.io/badge/tests-289%20passed-2aa198?style=flat-square)](tests/)
[![Smoke](https://img.shields.io/badge/smoke-51%20assertions-2aa198?style=flat-square)](tools/smoke_test_endpoints.py)
[![Routes](https://img.shields.io/badge/routes-50-859900?style=flat-square)](docs/api.md)
[![ADRs](https://img.shields.io/badge/ADRs-16-b58900?style=flat-square)](docs/adr/index.md)
[![Python](https://img.shields.io/badge/python-3.10+-3776ab?style=flat-square&logo=python&logoColor=white)](#install)
[![Platform](https://img.shields.io/badge/platform-macOS%20%7C%20Linux%20%7C%20Termux-1a3a52?style=flat-square)](#hardware)
[![Field test](https://img.shields.io/badge/Sonoma%20field%20test-May%2023%2C%202026-cb4b16?style=flat-square)](#status)
[![License](https://img.shields.io/badge/license-TBD-555555?style=flat-square)](#license)
[![Explainer](https://img.shields.io/badge/explainer-live-0058bd?style=flat-square)](https://storage.googleapis.com/pitwall-demo/pitwall-explainer.html)

> **[→ Project Explainer](https://storage.googleapis.com/pitwall-demo/pitwall-explainer.html)** — architecture, cue distribution, Sonoma corner doctrine, team.

A real-time, on-device coaching system for track-day drivers. Runs on a
Pixel 10 via Termux: ingests CAN telemetry over USB from the car's OBD-II
port, makes coaching decisions with on-device Gemma 4 E2B (via LiteRT-LM)
and a 50-endpoint Python brain backed by DuckDB, and pairs with a Vue PWA
(`pitwall-web`, sibling repo, in design) for presentation. Built for the
May 23, 2026 Sonoma Raceway field test.

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

The architecture is documented across 16 ADRs. The two most recent —
[ADR-015](docs/adr/015-universal-telemetry-sink.md) (universal telemetry
sink) and [ADR-016](docs/adr/016-can-bus-ingest-and-frontend-pivot.md) (USB
CAN ingest + Vue PWA frontend) — record the late-April pivot from BLE +
Flutter to USB-CAN + PWA. The Flutter / Kotlin trees in `android/` and
`android-app/` are frozen as v1 reference; they will be removed once
`pitwall-web` reaches feature parity.

## What this is / what this isn't

**What this is:** an in-cabin AI race coach for solo track-day drivers,
running offline on a Pixel + USB-CAN. Hot-path coaching cues at < 50 ms,
warm-path verbal coaching from on-device Gemma 4 E2B, cold-path Gemini
debrief at session end. Telemetry sinks into local DuckDB; analytics run
client-side; no cloud is required for the core loop.

**What this isn't:** a Garmin Catalyst (post-session debrief, fixed
heuristics), an AiM Solo (pure logger, no coaching), or a cloud-only LLM
tool. The differentiator is *real-time coaching that works without cell
coverage*, grounded in published pedagogy (Ross Bentley) and adapted to
driver skill level.

## Status

- **Bridge**: shipped. 50 HTTP endpoints, 289 tests, smoke-tested
  end-to-end against a real 8273-frame Sonoma VBO with 51 assertions
  green.
- **CAN pipeline**: shipped. `python-can` reader + simulator + DBC,
  6 round-trip tests on `interface='virtual'`.
- **Termux foreground service**: shipped. Drop-in install package in
  [`deploy/termux/`](deploy/termux/INSTALL.md).
- **Universal telemetry sink (ADR-015)**: shipped. Phase 1–4 all
  accepted; capability-aware coach gating live.
- **`pitwall-web` Vue PWA**: design-only. Full UX spec in
  [`docs/pitwall-web-design.md`](docs/pitwall-web-design.md) — screens,
  gamification systems, sprite-sheet generation prompts. Code lives in
  a future sibling repo.
- **Sonoma field test**: 24 days out as of this commit.

## Repo layout

```
pitwall/
├── tools/
│   ├── pitwall_bridge.py             # 50-route Flask app, CAN reader hookup
│   ├── can_reader.py                 # python-can consumer → DuckDB sink
│   ├── can_simulator.py              # VBO replay or synthetic CAN producer
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
│   ├── api.md                        # 50-endpoint reference
│   ├── internal_architecture.md      # post-2026-04-28 backend topology
│   ├── pitwall-web-design.md         # GBA-style PWA UX spec
│   ├── ux.md                         # underlying UX principles
│   ├── adr/                          # 16 ADRs
│   └── …                             # pedagogy, telemetry-pipeline, etc.
├── tests/                            # 289 passing, 0 skipped
├── android/  +  android-app/         # FROZEN v1 native; deletes post-PWA
├── mkdocs.yml
└── README.md
```

## Quick start

### Install

```bash
pip install flask duckdb requests python-can cantools
```

Optional for on-device LLM (Gemma 4 E2B via MediaPipe Genai):

```bash
pip install mediapipe
# Place gemma-4-E2B-it.task on the device path
# referenced by coach_engine.LitertCoach
```

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
# → 289 passed, 0 skipped
```

End-to-end smoke against a real Racelogic VBO:

```bash
python3 tools/smoke_test_endpoints.py --keep-db
# Ingests 8273-frame Sonoma VBO → exercises all 50 endpoints → 51 assertions
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
│  │  │ 50 endpoints (api.md)                            │              │ │
│  │  └──────────────────────────────────────────────────┘              │ │
│  └────────────────────────────────┬───────────────────────────────────┘ │
│                                   │                                     │
│                                   ▼                                     │
│  ┌───────────────────────────────────────────────────────┐              │
│  │ pitwall-web (Vue PWA)            — IN DESIGN —        │              │
│  │ HUD · Paddock dashboard · Replay · Trainer card       │              │
│  │ See docs/pitwall-web-design.md                        │              │
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
| Coach rule fire (RuleCoach, in-process) | **< 100 ms** | `src/simulator/coach_engine.py` |
| Coach LLM (LiteRT-LM Gemma 4 E2B, on-device) | **2–4 s** | [ADR-012](docs/adr/012-coach-engine-adapter.md) |
| Cloud Gemini debrief (post-session, optional) | **8–15 s** | `_gemini_insights()` in `pitwall_bridge.py` |
| CAN frame → DuckDB row | **< 5 ms** | `tools/can_reader.py` |
| End-to-end smoke (8273 frames + every endpoint) | **~12 s** | `tools/smoke_test_endpoints.py` |
| Capability recompute on session import | **< 200 ms** for a 90-min session | `_compute_capabilities` |

## Data privacy & residency

All telemetry, lap data, coaching notes, and driver profiles live in a
local DuckDB on the device that runs the bridge. Nothing is uploaded to
any cloud unless you explicitly:

- Call `POST /score` (Gemini-graded scoring) with `GEMINI_API_KEY` set —
  sends *aggregate* session stats only, never raw GPS / video / per-frame
  telemetry.
- Trigger a Gemini debrief through `POST /coach/debrief` — same scope.

The on-device coaching path (Gemma 4 E2B via MediaPipe Genai) requires no
network connection. The bridge's HTTP server binds to `127.0.0.1` only and
is not externally reachable without an explicit `adb reverse` tunnel or a
deliberate firewall rule.

DuckDB files live at `tools/pitwall_sessions.duckdb` (dev) or wherever
the Termux service writes them (production). Backups, exports, and
sharing are user-driven; the bridge never auto-syncs.

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
- [`docs/adr/index.md`](docs/adr/index.md) — all 16 architecture decision
  records.
- [`docs/adr/015-universal-telemetry-sink.md`](docs/adr/015-universal-telemetry-sink.md)
  — the registry + tall sink + capability model.
- [`docs/adr/016-can-bus-ingest-and-frontend-pivot.md`](docs/adr/016-can-bus-ingest-and-frontend-pivot.md)
  — the USB-CAN + Vue PWA pivot.

### For frontend devs (`pitwall-web`)

- [`docs/pitwall-web-design.md`](docs/pitwall-web-design.md) — full UX
  spec: 12 screens, GBA visual language, character system, gamification,
  sprite-sheet generation prompts, Vue tech stack.
- [`docs/ux.md`](docs/ux.md) — the underlying UX principles
  (audio-first, silence-is-coaching, fail-open, confidence-shapes-phrasing,
  no-number-chasing).
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
| `tests/` (pytest) | **289 passed, 0 skipped** | Unit + integration; vendored fixtures, no network |
| `tests/test_can_pipeline.py` | 6 of the 289 | Round-trip on `interface='virtual'`; no kernel modules required |
| `tools/smoke_test_endpoints.py` | **51 assertions, 0 failed** | End-to-end against a real 8273-frame Sonoma VBO |

Run individually:

```bash
python3 -m pytest tests/test_bridge.py -q                # 101 routes-level tests
python3 -m pytest tests/test_can_pipeline.py -q          # 6 CAN round-trip tests
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

Post-Sonoma open work, no committed dates:

- **Scaffold and ship `pitwall-web`** — Vue 3 + Vite + Tailwind PWA per
  [`docs/pitwall-web-design.md`](docs/pitwall-web-design.md). MVP cut: 13
  days for the gameloop entry → exit + score screen.
- **Video × telemetry sync** — `--video` flag on `tools/can_simulator.py`
  + byte-range MP4 serving on the bridge. Deferred from 2026-04-29.
- **Live `/cues/stream` SSE endpoint** — feeds the on-track HUD with
  coaching cues at native rate.
- **Per-car DBC packs** — Subaru GR86 next, then user-contributed DBCs
  for additional cars. Plug-in via `--can-dbc <path>`.
- **Multi-track support** — Laguna Seca and Thunderhill have their own
  `data/tracks/<id>.json` slot ready; awaits gold-standard reference
  laps and corner geometry.
- **Public release + license** — TBD post-field-test.

The May 23, 2026 Sonoma Raceway field test is the load-bearing milestone;
everything above is post-Sonoma.

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
- **MediaPipe Genai** + **LiteRT-LM** — on-device Gemma 4 E2B inference
  on the Pixel.

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
