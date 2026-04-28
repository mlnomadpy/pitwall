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
pip install flask duckdb

# Clone the repo or copy src/simulator/ + tools/ to the device, then:
python tools/pitwall_bridge.py --track src/simulator/sonoma.json &
```

The app connects to `127.0.0.1:8765` on the same device loopback — no port forwarding needed.

#### Warm Path Priority

Every 7.5 seconds of telemetry is analysed in this order:

| Tier | Transport | Latency | Requires |
|------|-----------|---------|----------|
| 1 | `127.0.0.1:8765/analyze` (bridge) | < 50ms | Bridge running |
| 2 | Gemini API (`gemini-2.5-flash`) | 1–3s | `GEMINI_API_KEY` set |
| 3 | Mock fallback | 0ms | Nothing (always works) |

### 4 — Gemma On-Device Model (Hot Path)

The reflexive hot path uses Gemma 3 1B INT4 running on the Pixel 10 TPU. Install it once:

```bash
# Download gemma-3-1b-it-int4.bin from Google AI Hub, then:
~/Library/Android/sdk/platform-tools/adb push gemma-3-1b-it-int4.bin \
    /data/data/com.pitwall.app/files/
```

Without the model file, `GemmaEngine` logs a warning and the hot path is disabled — the warm path and mock coaching still work normally.

### 5 — Live Hardware (On-Track)

Pair the following devices via Bluetooth before tapping **START SESSION**:

| Device | Role |
|--------|------|
| Racelogic Mini | 10Hz GPS + IMU telemetry (VBO format) |
| OBDLink MX+ | CAN bus — throttle, brake, RPM, steering |
| Pixel Earbuds | Audio coaching output via TTS |

The `PitwallService` foreground service manages all three connections and keeps coaching active with the screen off.

### 6 — Gemini API Key (Warm Path Tier 2)

When the Python bridge is not running, the app falls back to Google's Gemini API.
Credentials live in `flutter/android/local.properties` — **gitignored, never commit this file**.


**Step 1 — Add it to `local.properties`:**

```properties
# flutter/android/local.properties
GEMINI_API_KEY=AIzaSy...
```

**Step 2 — Rebuild:**

```bash
cd flutter && flutter run
```

Gradle bakes the key into `BuildConfig` at compile time. The key is used as
`?key=` query parameter on `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`.

> **Tip:** The bridge (Tier 1) intercepts every burst when running — Gemini is only called if the bridge is unreachable. For on-track use, run the bridge in Termux and skip the API key entirely.

## Team 2 (Intermediate, BMW M3)

| Role | Person |
|------|--------|
| Tech Lead | Hemanth HM |
| Edge / Telemetry | Simon Margolis |
| AGY Pipeline | Taha Bouhsine |
| Data Reasoning | Vijay Vivekanand |
| UX / Frontend | Aileen Villanueva |
