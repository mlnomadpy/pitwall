# System Architecture

## Overview

The system uses a **split-brain architecture** with two concurrent reasoning paths connected by the Antigravity telemetry pipeline, coordinated by a message arbiter. All compute runs on a single Pixel 10 device (edge) and Vertex AI (cloud).

## Full Architecture

```mermaid
graph TB
    subgraph Sensors
        RL[Racelogic Mini<br/>10Hz VBO output<br/>GPS + IMU + sub-meter accuracy]
        OBD[OBDLink MX<br/>CAN bus via Bluetooth<br/>11 usable signals<br/>7 broken/unmapped]
    end

    subgraph Pixel 10 - Edge
        subgraph Sensor Fusion Engine
            FUSE[Fuse Racelogic + OBDLink<br/>Kalman speed, Butterworth G-force<br/>complementary position<br/>confidence annotation per signal]
        end

        subgraph Hot Path - Gemma 4 on TPU
            GEMMA[Gemma 4<br/>on-device LLM<br/>< 50ms inference]
            PVR_LOCAL[Pedagogical Vectors<br/>local cache<br/>Ross Bentley curriculum]
            RULES[Confidence-Gated Rules<br/>threshold + trail brake +<br/>oversteer + understeer]
        end

        subgraph Message Arbiter
            ARB[Priority Gate<br/>P3 safety: immediate<br/>P2 technique: on straights<br/>P1 strategy: queued<br/>conflict detection<br/>3s global cooldown<br/>stale expiry 5s]
        end

        subgraph Antigravity Tx
            BURST[Store-and-Forward<br/>telemetry burst buffer<br/>guaranteed delivery<br/>survives 5G dropouts]
        end

        subgraph UX Layer
            HUD_SIGNAL[Signal Light HUD<br/>red/green grip potential bars<br/>minimal cognitive load]
            TTS_LOCAL[Local TTS<br/>reflexive cues via earbuds]
        end

        subgraph Local Analytics
            DUCK[DuckDB In-Process<br/>rolling G-force envelope<br/>lap comparison<br/>corner metrics]
        end
    end

    subgraph Vertex AI - Cloud
        subgraph Antigravity Rx
            INGEST[Telemetry Burst Ingestion<br/>parse + validate + store]
        end

        subgraph Warm Path - Gemini 3.0
            GEMINI[Gemini 3.0 Multimodal<br/>strategic analysis<br/>Gold Standard comparison<br/>sector-level coaching]
            PVR_CLOUD[Pedagogical Vectors<br/>full curriculum retrieval<br/>+ Gold Standard baseline<br/>+ T-Rod human coaching reference]
            PROFILE[Event-Sourced<br/>Driver Profile<br/>computed from DuckDB<br/>append-only facts]
        end

        subgraph Cloud Analytics
            VERTEX_DATA[Session Storage<br/>Vertex AI Datasets<br/>cross-session analysis]
        end
    end

    subgraph Driver
        EARBUDS[Pixel Earbuds<br/>primary interface<br/>audio coaching]
    end

    RL --> FUSE
    OBD --> FUSE

    FUSE --> GEMMA
    FUSE --> BURST
    FUSE --> DUCK
    FUSE --> HUD_SIGNAL

    PVR_LOCAL --> GEMMA
    RULES --> GEMMA
    GEMMA --> ARB

    BURST -->|5G store-and-forward| INGEST
    INGEST --> GEMINI
    PVR_CLOUD --> GEMINI
    PROFILE --> GEMINI
    GEMINI -->|5G| ARB
    INGEST --> VERTEX_DATA

    ARB --> EARBUDS
    ARB --> HUD_SIGNAL
    ARB --> TTS_LOCAL

    DUCK --> PROFILE
```

## Data Flow: One Corner

```mermaid
sequenceDiagram
    participant RL as Racelogic Mini
    participant OBD as OBDLink MX
    participant FUSE as Sensor Fusion
    participant GEMMA as Gemma 4 (Edge)
    participant ARB as Arbiter
    participant AGY as Antigravity
    participant GEMINI as Gemini 3.0 (Cloud)
    participant DRIVER as Pixel Earbuds

    Note over RL,OBD: Approaching Turn 3 at 95 mph

    RL->>FUSE: GPS position + IMU accel (20Hz)
    OBD->>FUSE: Brake pressure 0%, throttle 100% (50Hz CAN)
    FUSE->>FUSE: Fuse: speed 42.5 m/s (conf 0.95)<br/>gLat 0.1G (conf 0.95)<br/>brake 0% (conf 0.95)

    FUSE->>GEMMA: Fused frame (every 20ms)
    GEMMA->>GEMMA: Pedagogical vector match:<br/>"Threshold Braking" trigger<br/>driver hasn't started braking<br/>150m from turn-in

    GEMMA->>ARB: "Brake!" (P2, technique)
    ARB->>ARB: On straight (gLat < 0.3G) → deliver
    ARB->>DRIVER: Audio: "Brake!"

    Note over RL,OBD: Driver brakes, enters corner

    RL->>FUSE: gLat rising to 0.8G, speed dropping
    OBD->>FUSE: Brake 75%, throttle 0%
    FUSE->>GEMMA: Frame: gLat 0.8G, brake 75%
    GEMMA->>GEMMA: Match: "Trail Braking"<br/>brake still high at apex

    GEMMA->>ARB: "Trail brake, ease off" (P2)
    ARB->>ARB: Mid-corner (gLat 0.8G > 0.3G) → HOLD
    ARB->>ARB: Wait until gLat < 0.3G...

    FUSE->>AGY: Burst: last 10s of frames
    AGY->>GEMINI: Telemetry burst via 5G

    GEMINI->>GEMINI: Compare to Gold Standard:<br/>AJ braked 15m later,<br/>carried 4mph more through apex,<br/>trail braked 20% lighter

    GEMINI->>ARB: "Turn 3: you braked 15m early.<br/>Next lap, hold brake to the 2-board." (P1)
    
    Note over DRIVER: Now on straight after Turn 3
    ARB->>DRIVER: Deliver held P2: "Trail brake, ease off"
    ARB->>ARB: 3s cooldown...
    ARB->>DRIVER: Deliver queued P1: "Turn 3: you braked 15m early..."
```

## What Pitwall Adds to V1

The original V1 prototype had no coordination between hot and warm paths, no signal quality tracking, and no formal pedagogical structure. This sprint adds:

### From Pitwall: Confidence-Annotated Frame (ADR-001)

Every signal from Racelogic and OBDLink carries confidence metadata. Even with pro hardware, this matters:

- Racelogic GPS in a tunnel or under trees → confidence drops from 0.95 to 0.40
- OBDLink Bluetooth drops momentarily → CAN signals marked stale
- Gemma 4 rules check confidence before firing — no coaching on bad data

### From Pitwall: Message Arbiter (ADR-002)

V1 had both paths sending audio simultaneously. The arbiter prevents:

- Hot and warm coaching overlapping (hot says "brake", warm says "you braked too early" — conflicting)
- Mid-corner audio distraction (non-safety messages held until straight)
- Message spam (3s cooldown between different sources)

### From Pitwall: Sensor Fusion (ADR-006)

Even with Racelogic + OBDLink (both high quality), fusion adds value:

- Racelogic GPS speed vs OBDLink CAN speed → Kalman filter for best estimate
- Racelogic IMU G-forces → Butterworth filter removes road surface vibration
- Racelogic position between 20Hz GPS fixes → dead-reckoning from IMU for smooth 50Hz position

### New for Sprint: Gemma 4 Edge LLM (ADR-003)

The biggest upgrade from Pitwall. Instead of hardcoded rules, the hot path runs a real LLM on the Pixel 10 TPU:

- Evaluates telemetry against Ross Bentley pedagogical vectors
- Generates contextual coaching, not just threshold alerts
- Adapts language to driver skill level (beginner vs pro persona)
- Sub-50ms inference on Pixel 10 TPU

### New for Sprint: Antigravity Pipeline (ADR-004)

Replaces raw SSE/UDP streaming with store-and-forward:

- Telemetry bursts are buffered locally
- Sent to Vertex AI when 5G is available
- Survives cellular dropouts (common at racetracks)
- Guaranteed delivery — no lost frames

### New for Sprint: Pedagogical Vector Retrieval (ADR-005)

Ross Bentley's Speed Secrets curriculum encoded as structured vectors matched to telemetry triggers:

| Telemetry Trigger | Pedagogical Concept | Level |
|-------------------|-------------------|-------|
| gLong < -0.8G, brake > 50% | Threshold Braking | Beginner |
| brake > 10%, \|gLat\| > 0.4G | Trail Braking (weight transfer to front) | Intermediate |
| \|gLat\| > 1.0G, throttle < 20% | Commitment (trust the grip circle) | Intermediate |
| yaw_rate > expected_yaw | Oversteer (look where you want to go, modulate throttle) | All |
| yaw_rate < expected_yaw, steering > 30° | Understeer (ease throttle, straighten wheel slightly) | All |
| speed decreasing, no brake, no throttle | Coasting (wasted time — brake or accelerate) | Beginner |
| approaching apex, early turn-in | Late Apex reminder (wait for turn-in, accelerate earlier) | Intermediate |
| exiting corner, throttle < 50% | Exit Speed (speed on the straight matters more than speed in the corner) | All |

## Validated Against Real Data

The architecture has been validated against 183 VBO sessions (535K frames, 14.9 hours) from 8 tracks. Key findings that shaped design decisions:

### Dataset Summary

| Metric | Value |
|--------|-------|
| Total sessions | 183 (52 hot laps, 127 transit, 4 warmup/short) |
| Total frames | 535,366 (14.9 hours) |
| Usable hot lap frames | 456,711 (12.7 hours) |
| Tracks | 8 (3 primary: Sonoma 407 min, Track 2 225 min, Track 8 180 min) |
| Auto-generated track definitions | 3 (Sonoma 11 corners, Track 2 9 corners, Track 8 11 corners) |
| Actual sample rate | **10Hz** (not 20Hz — VBO output is downsampled from Racelogic hardware) |

### Trained ML Models

| Model | Architecture | Accuracy (unseen Track 8, 1.0s horizon) | Size |
|-------|-------------|----------------------------------------|------|
| **LSTM v3 Sequence Predictor** | Multi-scale BiLSTM + corner embedding + attention + residual | Speed: **3.3 km/h** MAE, Brake: **2.7 bar** MAE | 1.1 MB |
| Phase Classifier | GradientBoosting 200 trees | 100% (deterministic labels) | 3.7 MB |
| Brake Point Predictor | Linear regression | 15.9m MAE, R²=0.515 | 436 B |
| Style Fingerprint | K-Means k=4 | 4 archetypes: aggressive, smooth, heavy braker, cautious | 23 KB |

### Sonoma Track Profile (Auto-Generated)

| Corner | Dir | Entry km/h | Apex km/h | Exit km/h | Brake Zone | Elevation |
|--------|-----|-----------|-----------|-----------|-----------|-----------|
| Turn 1 | L | 111 | 113 | 117 | — | flat |
| Turn 3 | R | 104 | 87 | 102 | 50m, 12 bar | +11m uphill |
| Turn 6 | R | 92 | 77 | 105 | 86m, 29 bar | -11m downhill |
| Turn 9 | L | 121 | 116 | 132 | 66m, 25 bar | -16m downhill |
| Turn 10 | R | 106 | **73** | 108 | **124m, 47 bar** | flat |
| Turn 11 | R | 88 | **64** | 95 | **134m, 34 bar** | flat |

Turn 10 and Turn 11 are the heaviest braking corners — primary targets for trail brake coaching.

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Edge compute | Pixel 10 TPU (Gemma 4 inference) |
| Edge telemetry | Racelogic Mini SDK (10Hz VBO) + OBDLink MX Bluetooth (11 usable CAN signals) |
| Sensor fusion | Python / numpy (Kalman, Butterworth, complementary filter) |
| Edge LLM | Gemma 4 via on-device inference API |
| Sequence predictor | LSTM v3 (272K params, 1.1 MB, ~10ms CPU / ~3ms GPU) |
| Store-and-forward | Antigravity SDK (telemetry burst protocol) |
| Cloud LLM | Gemini 3.0 on Vertex AI |
| Cloud storage | Vertex AI Datasets |
| Local analytics | DuckDB in-process |
| Audio output | Pixel Earbuds via Android Audio API |
| HUD | Android Canvas on Pixel 10 display |
| Pedagogical vectors | JSON knowledge base with real Sonoma corner data |
| Driver profile | Event-sourced JSON (DuckDB-computed) |
| Rule testing | pytest + reference Sonoma laps |
