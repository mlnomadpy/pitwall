# Sprint Plan

April 8 -- May 30, 2026. Engineering-first with a hard technical gate.

---

## Timeline

```mermaid
gantt
    title Trustable AI Sprint
    dateFormat YYYY-MM-DD
    
    section Milestones
    Technical Kickoff              :milestone, m1, 2026-04-08, 0d
    Architecture Review (HARD GATE):milestone, crit, m2, 2026-04-29, 0d
    Field Test - Sonoma Raceway    :milestone, crit, m3, 2026-05-23, 0d
    Sprint Wrap                    :milestone, m4, 2026-05-30, 0d
    
    section Phase 1 - Foundation
    Sensor fusion engine           :a1, 2026-04-08, 10d
    Antigravity pipeline Tx/Rx     :a2, 2026-04-08, 14d
    Confidence-annotated frame     :a3, 2026-04-08, 7d
    Racelogic + OBDLink adapters   :a4, 2026-04-10, 10d
    Message arbiter                :a5, 2026-04-14, 7d
    
    section Phase 2 - Intelligence
    Gemma 4 hot path on Pixel TPU  :b1, 2026-04-18, 10d
    Pedagogical vector DB          :b2, 2026-04-18, 7d
    Gemini 3.0 warm path on Vertex :b3, 2026-04-21, 10d
    Gold Standard baseline (AJ lap):b4, 2026-04-21, 5d
    
    section Phase 3 - Integration
    End-to-end integration test    :c1, 2026-04-28, 7d
    Signal Light HUD               :c2, 2026-05-01, 7d
    Audio UX tuning                :c3, 2026-05-01, 10d
    Per-pod persona tuning         :c4, 2026-05-05, 10d
    
    section Phase 4 - Field Prep
    Sonoma track definition        :d1, 2026-05-12, 5d
    Regression test suite          :d2, 2026-05-12, 7d
    Dry run (local track)          :d3, 2026-05-15, 5d
    Travel + setup at Sonoma       :d4, 2026-05-20, 3d
    Field test day                 :d5, 2026-05-23, 1d
    Debrief + documentation        :d6, 2026-05-24, 7d
```

## The Hard Gate: April 29

**No code, no track.** Architecture review must demonstrate:

- [ ] Sensor fusion engine running with Racelogic + OBDLink data
- [ ] Confidence-annotated frames flowing through the pipeline
- [ ] Antigravity store-and-forward working with Vertex AI
- [ ] Gemma 4 inference on Pixel 10 TPU at <50ms
- [ ] Message arbiter preventing conflicting coaching
- [ ] At least 3 pedagogical vectors firing correctly on recorded telemetry
- [ ] End-to-end: sensor → fusion → coaching → audio on a replayed session

---

## Team Structure: F1 Garage Matrix

### Vertical Pods

Each pod owns a complete user experience for one driver skill level.

| Role | Team 1: Beginner (Rental) | Team 2: Intermediate (M3) | Team 3: Advanced (Race Car) |
|------|---------------------------|---------------------------|----------------------------|
| **Tech Lead** | Jigyasa Grover | Hemanth HM | Vikram Tiwari |
| **Edge / Telemetry** | Madona Wambua | Simon Margolis | Austin Bennett (Mentor) |
| **AGY Pipeline** | Mike Wolfson | **Taha Bouhsine** | Henry A Ruiz Guzman |
| **Data Reasoning** | Adrian Catalan | Vijay Vivekanand (Founder) | Vinicius F. Caridá |
| **UX / Frontend** | Rabimba Karanjai (Mentor) | Aileen Villanueva | Francisco Mere (Founder) |

### Horizontal Guilds

Cross-pod collaboration to build shared infrastructure once.

```mermaid
graph TB
    subgraph Edge Guild
        E1[Madona] --> SHARED_EDGE[Shared: Racelogic + OBDLink<br/>adapters, sensor fusion,<br/>confidence annotation]
        E2[Simon]  --> SHARED_EDGE
        E3[Austin] --> SHARED_EDGE
    end

    subgraph Pipeline Guild
        P1[Mike]  --> SHARED_PIPE[Shared: Antigravity Tx/Rx,<br/>store-and-forward,<br/>burst format, Vertex ingestion]
        P2[Taha]  --> SHARED_PIPE
        P3[Henry] --> SHARED_PIPE
    end

    subgraph Reasoning Guild
        R1[Adrian]   --> SHARED_REASON[Shared: Pedagogical vectors,<br/>Gold Standard comparison,<br/>driver profile schema]
        R2[Vijay]    --> SHARED_REASON
        R3[Vinicius] --> SHARED_REASON
    end

    SHARED_EDGE --> PLATFORM[Shared Platform]
    SHARED_PIPE --> PLATFORM
    SHARED_REASON --> PLATFORM

    PLATFORM --> POD1[Pod 1: Beginner tuning]
    PLATFORM --> POD2[Pod 2: Intermediate tuning]
    PLATFORM --> POD3[Pod 3: Pro tuning]
```

**Build horizontally first, tune vertically second.**

Guilds build the shared platform (sensor fusion, Antigravity pipeline, pedagogical vectors) in Phase 1-2. Once the platform works end-to-end, each pod returns to vertical tuning: adjusting coaching language, thresholds, and persona for their specific driver level.

---

## Taha's Role: AGY Pipeline, Team 2

Your scope on the Pipeline Guild:

### Phase 1 (Apr 8-21): Build the Antigravity Pipeline

1. **Antigravity Tx** on Pixel 10: Buffer fused frames, pack into bursts, send via 5G
2. **Antigravity Rx** on Vertex AI: Receive bursts, parse, validate, store, trigger Gemini 3.0
3. **Store-and-forward reliability**: Persist to local disk when 5G drops, send when restored
4. **Burst format**: Confidence-annotated frames in JSON, session metadata, driver level

### Phase 2 (Apr 18-28): Connect to Reasoning

5. **Feed Gemini 3.0**: Pass telemetry burst + Gold Standard + pedagogical vectors to Gemini
6. **Return warm path coaching**: Route Gemini response back to Pixel 10 via 5G → arbiter

### Phase 3 (Apr 28 - May 15): Team 2 Vertical Tuning

7. **Tune for M3 / Intermediate**: Adjust Antigravity burst cadence, Gemini prompt, coaching language for Team 2's BMW M3 and intermediate driver
8. **CAN configuration**: Ensure OBDLink MX reads M3-specific CAN signals correctly

### Phase 4 (May 15-23): Field Prep

9. **Sonoma dry run**: End-to-end test on a local track or parking lot
10. **Regression tests**: Verify pipeline on recorded Sonoma reference data

---

## Deliverables

### Completed (Pre-Sprint Data Analysis & Model Training)

- [x] **VBO parser** — parses all 183 Racelogic .vbo files (535K frames, 14.9 hours)
- [x] **Track builder** — auto-generates track definitions from GPS curvature. 3 tracks built: Sonoma (11 corners), Track 2 (9 corners), Track 8 (11 corners)
- [x] **Data analysis** — 52 hot lap sessions profiled. Driving phase distribution: 43.7% cornering, 8.8% braking, 6.3% coasting, 2.2% trail braking
- [x] **Signal audit** — 11 usable coaching signals, 7 broken/unmapped CAN signals documented
- [x] **LSTM v3 sequence predictor** — trained on 140K sequences, tested on unseen track. Speed MAE: 3.3 km/h at 1s. Brake MAE: 2.7 bar at 1s.
- [x] **Phase classifier** — XGBoost, 100% accuracy (labels are deterministic from features)
- [x] **Brake point predictor** — linear regression, 15.9m MAE, each m/s adds 2.36m to brake zone
- [x] **Style fingerprint** — K-Means 4 archetypes (aggressive, smooth, heavy braker, cautious)
- [x] **Sonic model v1** — hand-tuned audio cues (grip, brake approach, trail brake, throttle, coast)
- [x] **Sonic model v2** — LSTM-driven delta cues. Tested on Sonoma: fires speed_delta, brake_delta, lookahead, grip, corner score
- [x] **Simulator** — replays VBO with real track data + LSTM model, exports labeled CSV
- [x] **Data documentation** — 6 docs covering VBO format, signal reference, derived features, dataset overview, data quality
- [x] **Architecture docs** — 12 pages + 9 ADRs for sprint edition
- [x] **Kaggle training pipeline** — preprocessed data uploaded, training script tested on 2x T4 GPU

### By April 29 (Architecture Gate)

- [ ] Antigravity Tx/Rx working end-to-end
- [x] Confidence-annotated frames flowing through pipeline (simulator demonstrates this)
- [ ] Store-and-forward tested (simulate 5G dropout)
- [ ] Gemini 3.0 receiving bursts and generating coaching
- [ ] Warm path response delivered to arbiter → earbuds
- [x] Track definitions auto-generated for Sonoma (validated against real data)
- [x] LSTM model predicting speed/brake/throttle 2 seconds ahead on unseen track

### By May 23 (Field Test)

- [ ] Full system running on Pixel 10 in an M3
- [ ] Audio coaching audible and coherent via Pixel Earbuds
- [ ] Signal Light HUD showing grip bars
- [ ] Lap times computed from GPS crossing
- [x] Corner report card framework built (corner scorer in train_models.py)
- [ ] Driver profile updated from session data
- [ ] System survives 5G dropouts without data loss
- [x] Pedagogical vectors defined with real Sonoma corner data

### By May 30 (Sprint Wrap)

- [ ] Session recordings from Sonoma field test
- [ ] Post-session analysis comparing 3 pods
- [x] Architecture documentation updated with field test findings (data analysis complete)
- [ ] Reference architecture ready for Google I/O narrative
