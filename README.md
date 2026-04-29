# Pitwall Sprint — Trustable AI Racing Coach

**Prove that a split-brain AI system can be trusted at 130 mph.**

Production deployment of the [Pitwall](../forza/) architecture for the Trustable AI sprint (April--May 2026). Coaches drivers in real time at Sonoma Raceway using Gemma 4 on Pixel 10 TPU (reflexive hot path) and Gemini 3.0 on Vertex AI (strategic warm path), connected by the Antigravity store-and-forward pipeline.

## Architecture

```
Racelogic Mini ──┐
                  ├── Sensor Fusion ── Gemma 4 (Pixel 10 TPU, <50ms) ──┐
OBDLink MX ──────┘        │                                             ├── Arbiter ── Pixel Earbuds
                           │                                             │
                      Antigravity ──5G──► Gemini 3.0 (Vertex AI, 2-5s) ─┘
                      (store & forward)
```

## What's Different from Pitwall Open Source

| Pitwall | Sprint |
|---------|--------|
| Hardcoded rules (hot path) | **Gemma 4 LLM on Pixel 10 TPU** |
| SSE + UDP streaming | **Antigravity store-and-forward** |
| Generic coaching rules | **Ross Bentley pedagogical vectors** |
| Commodity hardware ($40-230) | **Racelogic Mini + OBDLink MX + Pixel 10** |
| Laptop + phone + tablet | **Single device: Pixel 10** |
| Driver's personal best | **Gold Standard: AJ's pro reference lap** |

What we **keep from Pitwall** (improvements over V1):

- Confidence-annotated telemetry frame
- Message arbiter (priority, conflict, corner suppression)
- Sensor fusion engine (Kalman, Butterworth, complementary)
- Event-sourced driver profile
- Regression testing for coaching vectors
- Graceful degradation protocol

## Key Dates

- **April 8:** Kickoff
- **April 29:** Architecture gate (no code, no track)
- **May 23:** Field test at Sonoma Raceway
- **May 30:** Sprint wrap

## Docs

```bash
pip install mkdocs-material
cd pitwall-sprint
mkdocs serve -a 127.0.0.1:8889
```

## Getting Started

### Prerequisites

- Flutter SDK ≥ 3.3 — [install](https://docs.flutter.dev/get-started/install)
- Android Studio with an emulator (Pixel 10 API 35) **or** a physical Pixel device
- Python 3.10+ (for the local coaching bridge)
- `adb` on your PATH: add `~/Library/Android/sdk/platform-tools` to `~/.zshrc`

### Jetpack “Paddock” app (optional)

A standalone **native** client lives in [`android-app/`](android-app/README.md): Compose, **Google Maps**, **WebView** pre-brief, and the bridge `/health` check. Use it for Sonoma map UX and Pixel-only flows alongside the Flutter build.

### 1 — Run the Flutter App

```bash
cd flutter
flutter pub get
flutter run            # picks up the connected emulator or device automatically
```

On first launch, the Setup screen shows hardware status. Tap **START SESSION** to begin a live session (requires Racelogic Mini + OBDLink paired via Bluetooth), or **REPLAY VBO** to load a recorded session file.

### 2 — Replay a VBO File

Generate a synthetic test session (3 laps, Sonoma Raceway, BMW M3):

```bash
python3 tools/generate_sample_vbo.py > /tmp/session.vbo
~/Library/Android/sdk/platform-tools/adb push /tmp/session.vbo /sdcard/Download/
```

Or push your own recorded `.vbo` file from a Racelogic VBOX unit:

```bash
~/Library/Android/sdk/platform-tools/adb push /path/to/VBOX0133.vbo /sdcard/Download/
```

In the app, tap **REPLAY VBO** → navigate to **Downloads** → select the file. The parser handles the real VBOX hardware format (space-separated, `HHMMSS.SSS` timestamps, `DDMM.MMMM` coordinates).

### 3 — Python Coaching Bridge (Warm Path Tier 1)

The `tools/pitwall_bridge.py` server is the **primary warm path**. It integrates the full
`src/simulator` coaching stack — the same `sonic_model` and `track_loader` used in
`pitwall_app.py` — and exposes it over HTTP on port `8765`. No cloud credentials needed.

| Engine | When active | Coaching quality |
|--------|------------|------------------|
| `sonic_model` (real) | `src/simulator/` importable | Full per-frame audio cue analysis |
| Rule fallback | Import fails | Basic threshold rules |

#### On the Emulator

```bash
# Install once
pip3 install flask duckdb

# Terminal A — run from repo root (required for src/simulator imports)
python3 tools/pitwall_bridge.py --track src/simulator/sonoma.json
# Expected:
# ✓  sonic_model loaded
# ✓  Track: Sonoma Raceway (12 corners)
# 🏁  Pitwall Bridge v2 on http://127.0.0.1:8765
#     Engine: sonic_model (real cues)

# Terminal B — forward port into the emulator (once per adb session)
~/Library/Android/sdk/platform-tools/adb reverse tcp:8765 tcp:8765

# Terminal B — run the app
cd flutter && flutter run
```

Verify the bridge and confirm which engine is active:

```bash
# From your Mac (bridge runs here — no adb needed)
curl -s http://127.0.0.1:8765/health | python3 -m json.tool
# → {"engine": "sonic_model", "status": "ok", "track": "Sonoma Raceway", ...}

# Or verify from inside the emulator (wget is available, curl is not)
~/Library/Android/sdk/platform-tools/adb shell wget -qO- http://127.0.0.1:8765/health
```

#### On a Real Pixel Device (Termux)

```bash
# In Termux on the device:
pkg install python git
pip install flask duckdb mediapipe

# Push the on-device LLM artifact into shared storage, e.g.:
mkdir -p ~/storage/shared/Pitwall/models/
# Download gemma-4-E2B-it.task from huggingface.co/litert-community/gemma-4-E2B-it-litert-lm

# Clone the repo or copy src/simulator/ + tools/ to the device, then:
python tools/pitwall_bridge.py --coach litert \
    --litert-model ~/storage/shared/Pitwall/models/gemma-4-E2B-it.task &
```

The Flutter app connects to `127.0.0.1:8765` on the same device loopback — no port forwarding needed.

#### Warm Path Priority

The project committed to **on-device coaching only** (per [`docs/adr/012-coach-engine-adapter.md`](docs/adr/012-coach-engine-adapter.md)). Every 7.5 seconds of telemetry is analysed in this order:

| Tier | Transport | Latency | Requires |
|------|-----------|---------|----------|
| 1 | `127.0.0.1:8765/analyze` (bridge) | < 50ms | Bridge running |
| 2 | Mock fallback | 0ms | Nothing (always works, used for tests / when bridge is unreachable) |

### 4 — Gemma On-Device Model (Hot Path)

Per [`docs/adr/013-frontend-backend-boundary.md`](docs/adr/013-frontend-backend-boundary.md), the backend owns inference. `LitertCoach` runs Gemma 4 E2B in-process via MediaPipe Genai's LiteRT-LM runtime; the legacy Kotlin `GemmaEngine.kt` is deprecated.

```bash
# Download gemma-4-E2B-it.task from huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
mkdir -p ~/storage/shared/Pitwall/models/
# adb push (from desktop) or wget (in Termux) the .task file there
```

Without the `.task` file, `LitertCoach` logs a warning and the bridge falls back to `RuleCoach` (templated pace notes). The system still works, just without the LLM voice.

### 5 — Live Hardware (On-Track)

Pair the following devices via Bluetooth before tapping **START SESSION**:

| Device | Role |
|--------|------|
| Racelogic Mini | 10Hz GPS + IMU telemetry (VBO format) |
| OBDLink MX+ | CAN bus — throttle, brake, RPM, steering |
| Pixel Earbuds | Audio coaching output via TTS |

The `PitwallService` foreground service manages all three connections and keeps coaching active with the screen off.

## Team 2 (Intermediate, BMW M3)

| Role | Person |
|------|--------|
| Tech Lead | Hemanth HM |
| Edge / Telemetry | Simon Margolis |
| AGY Pipeline | Taha Bouhsine |
| Data Reasoning | Vijay Vivekanand |
| UX / Frontend | Aileen Villanueva |
