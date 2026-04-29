# Internal Architecture

This is the **as-shipped** view of the Python backend (post 2026-04-28). [`architecture.md`](architecture.md) is the original sprint-design diagram (split-brain hot/warm path conceptually); this doc shows what the code actually does today, with mermaid diagrams generated against the live codebase.

---

## High-level system

The Python backend is the source of truth. The Flutter Pixel app is a renderer per [ADR-013](adr/013-frontend-backend-boundary.md). All LLM logic, system prompts, and analytics live in `src/simulator/`. The bridge (`tools/pitwall_bridge.py`) exposes them over HTTP.

```mermaid
flowchart LR
  subgraph SENSORS["📡 Sensors (on-car)"]
    RL[Racelogic Mini<br/>10 Hz GPS+IMU<br/>VBO format]
    OBD[OBDLink MX<br/>CAN brake/throttle/RPM]
    CAM[Pixel dashcam<br/>MP4 chunks]
  end

  subgraph BRIDGE["🌉 tools/pitwall_bridge.py — 26 endpoints"]
    direction TB
    INGEST["/session/&lt;id&gt;/frames<br/>/session/&lt;id&gt;/video_frames<br/>/analyze (burst)"]
    QUERY["/session/&lt;id&gt;/scorecard<br/>/highlights /map /sync<br/>/coach/brief /debrief"]
    META["/track/markers<br/>/track/danger_zones<br/>/track/weather"]
  end

  subgraph BACKEND["🐍 src/simulator/ — analytics + coach"]
    direction TB
    SONOMA[(sonoma.py<br/>hardcoded constants)]
    GRADER[corner_grader.py<br/>A-F + time-loss]
    ANALYTICS[analytics.py<br/>13 analysers]
    HIGHLIGHTER[highlight_finder.py<br/>7 Sonoma categories]
    ANALYZER[session_analyzer.py<br/>orchestrator]
    COACH[coach_engine.py<br/>RuleCoach + LitertCoach]
    GOLD[(gold_standard.py<br/>per-corner reference)]
    PROFILE[(driver_profile.py<br/>event-sourced)]
  end

  subgraph STORE["💾 DuckDB (single-process source of truth)"]
    direction TB
    T_SESSIONS[(sessions)]
    T_LAPS[(laps)]
    T_FRAMES[(telemetry)]
    T_VIDEO[(video_frames)]
    T_NOTES[(coaching_notes)]
    T_EVENTS[(driver_events)]
  end

  subgraph FRONTEND["📱 Flutter / Kotlin (renderer only)"]
    HUD[on-track HUD]
    PADDOCK[paddock review]
    MAP[Mapbox/Maplibre overlay]
  end

  subgraph DATA["📂 data/"]
    TRACK_JSON[/sonoma.json<br/>+ markers + tips/]
    REAL_GPS[/sonoma_real_gps.json<br/>OSM-derived/]
    GOLD_REF[/sonoma_gold.json<br/>+ trace.json/]
    THUMBS[/markers/sonoma/*.jpg/]
  end

  RL --> INGEST
  OBD --> INGEST
  CAM --> INGEST
  INGEST --> T_FRAMES & T_VIDEO & T_NOTES & T_LAPS
  INGEST --> COACH
  COACH --> SONOMA
  COACH --> ANALYZER
  ANALYZER --> GRADER & ANALYTICS & HIGHLIGHTER & GOLD & PROFILE
  ANALYZER --> COACH
  GRADER --> SONOMA
  HIGHLIGHTER --> SONOMA
  PROFILE <--> T_EVENTS
  GOLD <-- reads --> GOLD_REF
  SONOMA <-- reads --> TRACK_JSON
  QUERY --> ANALYZER
  QUERY --> META
  META --> SONOMA
  QUERY --> FRONTEND
  MAP <-- real GPS --> REAL_GPS
  MAP <-- markers + thumbs --> THUMBS

  classDef store fill:#1a3a52,stroke:#4a6e8a,color:#e0e0e0
  classDef code fill:#2e5d3a,stroke:#5a8a6e,color:#e0e0e0
  classDef sensor fill:#5d4a1a,stroke:#8a6e3a,color:#e0e0e0
  classDef ui fill:#5d1a3a,stroke:#8a3a5e,color:#e0e0e0
  classDef data fill:#3a3a3a,stroke:#6e6e6e,color:#e0e0e0
  class T_SESSIONS,T_LAPS,T_FRAMES,T_VIDEO,T_NOTES,T_EVENTS,SONOMA,GOLD,PROFILE store
  class GRADER,ANALYTICS,HIGHLIGHTER,ANALYZER,COACH,INGEST,QUERY,META code
  class RL,OBD,CAM sensor
  class HUD,PADDOCK,MAP ui
  class TRACK_JSON,REAL_GPS,GOLD_REF,THUMBS data
```

---

## Module dependency graph (`src/simulator/`)

```mermaid
graph TD
  sonoma[sonoma.py<br/>constants + lore]
  vbo[vbo_parser.py<br/>VBO → frames]
  track[track_loader.py<br/>JSON → TrackMap]
  gold[gold_standard.py<br/>per-corner reference]
  grader[corner_grader.py<br/>A-F + time-loss]
  analytics[analytics.py<br/>smoothness / friction / etc.]
  hl[highlight_finder.py<br/>moments]
  profile[driver_profile.py<br/>events]
  analyzer[session_analyzer.py<br/>orchestrator]
  coach[coach_engine.py<br/>RuleCoach / LitertCoach]
  sonic[sonic_model_v2.py<br/>LSTM-driven cues]
  app[pitwall_app.py<br/>TUI / replay]

  vbo --> gold
  track --> gold
  vbo --> analyzer
  track --> analyzer
  sonoma --> grader
  gold --> grader
  grader --> analyzer
  sonoma --> analytics
  grader --> analytics
  analytics --> analyzer
  sonoma --> hl
  grader --> hl
  gold --> hl
  hl --> analyzer
  profile --> analyzer
  analyzer --> coach
  coach --> sonoma
  app --> coach
  app --> sonic
  app --> track
  app --> vbo

  classDef hot fill:#5d2a1a,stroke:#8a4e3a,color:#e0e0e0
  classDef warm fill:#1a4a5d,stroke:#3a6e8a,color:#e0e0e0
  classDef data fill:#1a3a52,stroke:#4a6e8a,color:#e0e0e0
  class coach,grader,hl,analytics warm
  class sonic,sonoma hot
  class profile,gold,track,vbo data
```

---

## DuckDB schema

```mermaid
erDiagram
  SESSIONS ||--o{ LAPS : "has many"
  SESSIONS ||--o{ TELEMETRY : "has many"
  SESSIONS ||--o{ VIDEO_FRAMES : "has many"
  SESSIONS ||--o{ COACHING_NOTES : "has many"
  SESSIONS {
    string session_id PK
    string driver
    string driver_level
    string track
    string car
    timestamp started_at
    timestamp ended_at
    string note
  }
  LAPS {
    int id PK
    string session_id FK
    int lap_number
    double lap_time_s
    double best_sector
    double avg_speed_kmh
    double max_combo_g
    double coast_pct
    timestamp recorded_at
  }
  TELEMETRY {
    string session_id FK
    int frame_idx PK
    double timestamp
    double distance_m
    double speed_ms
    double g_lat
    double g_long
    double combo_g
    double brake_bar
    double throttle_pct
    double steering_deg
    double rpm
    double lat
    double lon
  }
  VIDEO_FRAMES {
    string session_id FK
    double timestamp
    bigint avitime_ms
    string file_path
    double file_offset_s
    int width
    int height
  }
  COACHING_NOTES {
    int id PK
    string session_id FK
    int burst_id
    double distance_m
    string text
    string source
    timestamp recorded_at
  }
  DRIVER_EVENTS {
    int id PK
    string driver_id
    string session_id FK
    string corner
    string event_kind
    double value_num
    string value_str
    timestamp recorded_at
  }
```

`session_id` is the universal join key. `timestamp` (epoch seconds) is the universal clock for telemetry × video sync.

---

## Three coaching modes

```mermaid
flowchart TB
  classDef pre fill:#1a4a5d,stroke:#3a6e8a,color:#e0e0e0
  classDef during fill:#5d2a1a,stroke:#8a4e3a,color:#e0e0e0
  classDef post fill:#2e5d3a,stroke:#5a8a6e,color:#e0e0e0
  classDef shared fill:#3a3a3a,stroke:#6e6e6e,color:#e0e0e0

  subgraph PRE["🟦 PRE_BRIEF — paddock, before session"]
    direction LR
    PRE_IN["GET /coach/brief<br/>?driver=&date=<br/>&focus=T4,T7,T11"]
    PRE_PROFILE[driver_profile<br/>compute_profile]
    PRE_WX[sonoma.<br/>weather_phase_for_hour]
    PRE_LLM[LitertCoach.brief<br/>~150 words<br/>≤300 tokens]
    PRE_OUT[narrative_md +<br/>3 focus items]
    PRE_IN --> PRE_PROFILE & PRE_WX --> PRE_LLM --> PRE_OUT
  end

  subgraph DURING["🟥 DURING_DRIVE — every burst (7.5s)"]
    direction LR
    DUR_IN["POST /analyze<br/>{burst summary}"]
    DUR_CTX[build_context<br/>+ marker lookup]
    DUR_BENTLEY[match_<br/>bentley_concept]
    DUR_PROPOSE[coach.propose<br/>≤14 words]
    DUR_ARB[CoachArbiter<br/>P3/P2/P1 + cooldown 3s]
    DUR_OUT[pace_note +<br/>cues + coaching]
    DUR_NOTES[(coaching_notes)]
    DUR_IN --> DUR_CTX --> DUR_BENTLEY --> DUR_PROPOSE --> DUR_ARB --> DUR_OUT
    DUR_OUT --> DUR_NOTES
  end

  subgraph POST["🟩 POST_SESSION — paddock, after session"]
    direction LR
    POST_IN["POST /coach/debrief<br/>{session_id}"]
    POST_FR[(load frames<br/>from telemetry)]
    POST_GR[corner_grader<br/>A-F + Δt loss]
    POST_AN[analytics<br/>13 metrics]
    POST_HL[highlight_finder<br/>top 8 moments]
    POST_LLM[LitertCoach.debrief<br/>~300 words]
    POST_BUNDLE[Visualization<br/>bundle JSON]
    POST_EVENTS[(driver_events)]
    POST_IN --> POST_FR --> POST_GR & POST_AN & POST_HL --> POST_LLM --> POST_BUNDLE
    POST_GR --> POST_EVENTS
  end

  SHARED[(coach_engine<br/>build_system_prompt<br/>+ build_user_prompt<br/>+ sonoma.SYSTEM_PROMPT_LORE)]:::shared
  PRE_LLM -.uses.-> SHARED
  DUR_PROPOSE -.uses.-> SHARED
  POST_LLM -.uses.-> SHARED

  class PRE,PRE_IN,PRE_PROFILE,PRE_WX,PRE_LLM,PRE_OUT pre
  class DURING,DUR_IN,DUR_CTX,DUR_BENTLEY,DUR_PROPOSE,DUR_ARB,DUR_OUT,DUR_NOTES during
  class POST,POST_IN,POST_FR,POST_GR,POST_AN,POST_HL,POST_LLM,POST_BUNDLE,POST_EVENTS post
```

---

## Session lifecycle (sequence)

```mermaid
sequenceDiagram
  participant App as Flutter app
  participant Bridge as bridge :8765
  participant Coach as coach_engine
  participant DB as DuckDB
  participant Analyzer as session_analyzer

  Note over App,DB: Pre-session
  App->>Bridge: POST /session/start
  Bridge->>DB: INSERT INTO sessions
  Bridge-->>App: {session_id}

  App->>Bridge: GET /coach/brief?driver=...
  Bridge->>DB: SELECT events for driver
  Bridge->>Coach: brief(driver, focus, weather)
  Coach-->>Bridge: narrative + 3 focus
  Bridge-->>App: pre-brief bundle

  Note over App,DB: During session (every 7.5s)
  loop For each burst
    App->>Bridge: POST /session/&lt;id&gt;/frames {batch}
    Bridge->>DB: INSERT INTO telemetry
    App->>Bridge: POST /session/&lt;id&gt;/video_frames {meta}
    Bridge->>DB: INSERT INTO video_frames
    App->>Bridge: POST /analyze {burst}
    Bridge->>Coach: propose(ctx)
    Coach-->>Bridge: pace_note
    Bridge->>DB: INSERT INTO coaching_notes
    Bridge-->>App: {pace_note, cues, coaching}
  end

  Note over App,DB: End of session
  App->>Bridge: POST /session/&lt;id&gt;/end
  Bridge->>DB: UPDATE sessions SET ended_at

  App->>Bridge: POST /coach/debrief {session_id}
  Bridge->>Analyzer: analyze_session(sid)
  Analyzer->>DB: SELECT * FROM telemetry WHERE session_id
  Analyzer->>Analyzer: grade + analyze + find highlights
  Analyzer->>Coach: debrief(bundle)
  Coach-->>Analyzer: narrative + next_focus
  Analyzer-->>Bridge: bundle JSON
  Bridge->>DB: INSERT into driver_events (longitudinal)
  Bridge-->>App: bundle

  Note over App,DB: Off-track review
  App->>Bridge: GET /session/&lt;id&gt;/scorecard
  Bridge-->>App: A-F per corner

  App->>Bridge: GET /session/&lt;id&gt;/sync?from=&to=
  Bridge->>DB: JOIN telemetry × video_frames on time
  Bridge-->>App: telemetry + video offsets

  App->>Bridge: GET /session/&lt;id&gt;/highlights
  Bridge-->>App: 8 ranked moments + clip cuts
```

---

## Coach-engine internals

```mermaid
classDiagram
  class CoachEngine {
    <<interface>>
    +str name
    +propose(ctx CoachContext) CoachingMessage
  }
  class RuleCoach {
    +str driver_level
    -_render(ctx) str
  }
  class LitertCoach {
    +str driver_level
    -LlmInference _llm
    -RuleCoach _fallback
    +health() dict
    +brief(args) tuple
    +debrief(bundle) tuple
    -_infer(ctx) str
    -_generate(sys, usr) str
  }
  class CoachContext {
    +str driver_level
    +str track_name
    +str next_corner_name
    +str next_brake_marker_label
    +str next_corner_tip
    +float meters_to_entry
    +str bentley_concept
  }
  class CoachingMessage {
    +str text
    +int priority
    +str layer
  }
  class CoachArbiter {
    +float cooldown_s
    +float stale_s
    +submit(msg, now, on_straight) Optional[CoachingMessage]
  }
  class CoachMode {
    <<enum>>
    DURING_DRIVE
    PRE_BRIEF
    POST_SESSION
  }
  CoachEngine <|-- RuleCoach
  CoachEngine <|-- LitertCoach
  LitertCoach o-- RuleCoach : fallback
  CoachEngine ..> CoachContext : consumes
  CoachEngine ..> CoachingMessage : produces
  CoachArbiter ..> CoachingMessage : gates
  LitertCoach ..> CoachMode : uses
```

`make_coach(kind="auto"|"litert"|"rule")` is the factory. `auto` tries `LitertCoach`; if MediaPipe isn't installed or the `.task` file is missing, it falls back to `RuleCoach`. `LitertCoach` itself also falls back per-call when its runtime fails — calling code can always rely on getting *something* back.

---

## Bridge endpoint topology

```mermaid
flowchart LR
  classDef coach fill:#5d2a1a,stroke:#8a4e3a,color:#e0e0e0
  classDef sess fill:#1a4a5d,stroke:#3a6e8a,color:#e0e0e0
  classDef telem fill:#2e5d3a,stroke:#5a8a6e,color:#e0e0e0
  classDef analyze fill:#5d4a1a,stroke:#8a6e3a,color:#e0e0e0
  classDef meta fill:#3a3a3a,stroke:#6e6e6e,color:#e0e0e0

  subgraph C["coaching"]
    direction TB
    H[GET /health]
    A[POST /analyze]
    BR[GET /coach/brief]
    DB[POST /coach/debrief]
  end

  subgraph S["sessions"]
    direction TB
    SLIST[GET /sessions]
    SSTART[POST /session/start]
    SDETAIL[GET /session/&lt;id&gt;]
    SEND[POST /session/&lt;id&gt;/end]
  end

  subgraph T["telemetry + video"]
    direction TB
    FRAMES[POST /session/&lt;id&gt;/frames]
    VFRAMES[POST /session/&lt;id&gt;/video_frames]
    SYNC[GET /session/&lt;id&gt;/sync]
    LAP[POST /lap]
    LAPS[GET /laps]
  end

  subgraph V["analysis bundles"]
    direction TB
    SC[GET /session/&lt;id&gt;/scorecard]
    HL[GET /session/&lt;id&gt;/highlights]
    ST[GET /session/&lt;id&gt;/stats]
    FC[GET /session/&lt;id&gt;/friction_circle]
    HM[GET /session/&lt;id&gt;/hustle_map]
    EOB[GET /session/&lt;id&gt;/eob]
    INC[GET /session/&lt;id&gt;/incidents]
    MAP[GET /session/&lt;id&gt;/map]
    CL[GET /session/&lt;id&gt;/clips]
  end

  subgraph M["track + driver metadata"]
    direction TB
    TM[GET /track/markers]
    TD[GET /track/danger_zones]
    TW[GET /track/weather]
    DP[GET /driver/&lt;id&gt;/profile]
  end

  class H,A,BR,DB coach
  class SLIST,SSTART,SDETAIL,SEND sess
  class FRAMES,VFRAMES,SYNC,LAP,LAPS telem
  class SC,HL,ST,FC,HM,EOB,INC,MAP,CL analyze
  class TM,TD,TW,DP meta
```

---

## File tree (post 2026-04-28)

```
pitwall/
├── data/
│   ├── reference/
│   │   ├── sonoma_gold.json          (per-corner gold)
│   │   └── sonoma_gold_trace.json    (986-frame trace)
│   ├── markers/sonoma/
│   │   ├── manifest.json             (16 thumbnail cut points)
│   │   └── *.jpg                     (when ffmpeg run)
│   └── tracks/
│       ├── sonoma.json               (canonical, w/ markers + GPS)
│       ├── sonoma_real_gps.json      (OSM real coords)
│       ├── sonoma.json.bak
│       └── training_data/
│           ├── track2.json           (ML-only, not deployed)
│           └── track8.json
├── docs/                              (mkdocs site)
│   ├── architecture.md                (sprint design — concept)
│   ├── internal_architecture.md      (this file — code)
│   ├── api.md                         (endpoint reference)
│   ├── markers.md
│   ├── sonoma_track_intelligence.md
│   ├── sonoma_maneuvers.md            (Part A/B/C attribution)
│   ├── trod_sonoma_session.md
│   ├── litert_termux_validation.md
│   ├── AUDIT.md                       (this turn's audit)
│   └── adr/
│       ├── 001…013-*.md               (sprint ADRs)
│       └── 014-sonoma-as-the-product.md
├── flutter/                           (Pixel 10 deployment, ADR-013)
├── src/
│   └── simulator/
│       ├── sonoma.py                  (constants + lore)
│       ├── track_loader.py
│       ├── vbo_parser.py
│       ├── gold_standard.py
│       ├── corner_grader.py
│       ├── analytics.py
│       ├── highlight_finder.py
│       ├── driver_profile.py
│       ├── session_analyzer.py
│       ├── coach_engine.py            (RuleCoach + LitertCoach + 3 modes)
│       ├── audio_engine.py
│       ├── sonic_model_v2.py
│       └── pitwall_app.py
├── tests/                             (this turn's audit suite)
└── tools/
    ├── pitwall_bridge.py              (Flask, 26 endpoints)
    ├── enrich_sonoma_track.py
    ├── extract_gold_lap.py
    ├── best_sonoma_lap.py             (S/F line-projection)
    ├── import_sonoma_real_gps.py      (OSM Overpass)
    ├── extract_marker_thumbnails.py
    └── validate_litert.py             (Pixel-side smoke)
```

---

## Key invariants the architecture enforces

1. **Backend owns inference** ([ADR-013](adr/013-frontend-backend-boundary.md)). Frontend never imports `mediapipe`, never builds prompts, never grades a corner.
2. **One source of truth** for system prompts: `coach_engine.build_system_prompt(driver_level, track, mode)`. Every coach (RuleCoach, LitertCoach, future GeminiCoach) consumes the same composer.
3. **Sonoma is the product** ([ADR-014](adr/014-sonoma-as-the-product.md)). `sonoma.py` is hardcoded; track JSON is the only data file the bridge needs at runtime.
4. **DuckDB is the source-of-truth store** for sessions, laps, telemetry, video metadata, coaching notes, and driver events. `session_id` is the universal join key. `timestamp` (epoch seconds) is the universal clock.
5. **Markers carry both anonymized and real GPS** so analytics that join against the dataset's anonymized frame and frontend that renders on a real-world map both work without conflict.
6. **Three-tier graceful degradation** for the LLM: LitertCoach → RuleCoach → mock. Anything that calls a coach can always rely on a string back.
