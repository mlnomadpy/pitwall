# API — Pitwall HTTP Bridge

The bridge (`tools/pitwall_bridge.py`) is the **Tier 1 warm path** for the Flutter Pixel 10 app and the canonical integration surface for any external client (telemetry adapters, dashboards, replay tools). It runs locally on `127.0.0.1:8765`, wraps the full Python coaching stack (`sonic_model`, `coach_engine`, `track_loader`), and persists session state to DuckDB.

```
client (Flutter / curl / Termux) ──► HTTP :8765 ──► sonic_model.compute_cues  ─┐
                                            └────► coach_engine.propose       ├─► /analyze response
                                            └────► duckdb (sessions/laps/notes)
```

See [ADR-010](adr/010-http-bridge-warm-path.md) for the design rationale.

---

## Run

```bash
pip3 install flask duckdb requests          # one-time
python3 tools/pitwall_bridge.py             # uses default track + auto coach
```

Common variants:

```bash
# Force the rule coach (no LLM dependency)
python3 tools/pitwall_bridge.py --coach rule

# Force on-device LiteRT-LM inference (Gemma 4 E2B via MediaPipe Genai)
python3 tools/pitwall_bridge.py --coach litert \
    --litert-model ~/storage/shared/Pitwall/models/gemma-4-E2B-it.task

# Tune phrasing per pod
python3 tools/pitwall_bridge.py --driver-level beginner
```

Emulator tunnel:

```bash
~/Library/Android/sdk/platform-tools/adb reverse tcp:8765 tcp:8765
```

---

## Endpoints

### `GET /health`

Liveness probe and engine status.

```jsonc
// 200 OK
{
  "status":       "ok",
  "version":      "2.1",
  "engine":       "sonic_model",       // or "rules" if compute_cues import failed
  "coach":        "rule",              // or "litert" / null
  "driver_level": "intermediate",
  "track":        "Sonoma Raceway",    // or null
  "duckdb":       true,                // false if duckdb not installed
  "timestamp":    "2026-04-28T17:00:00Z"
}
```

### `POST /analyze`

The main coaching endpoint. Receives a telemetry burst from `AntigravityPipeline.kt` and returns coaching text + audio cues + a rally-style pace note.

**Request body** (subset; extra fields ignored):

```jsonc
{
  "session_id":         "sonoma-2026-04-28-team2",
  "burst_id":           7,
  "avg_speed_kmh":      104.0,
  "max_combo_g":        1.82,
  "max_lateral_g":      1.35,           // optional, derived from combo if absent
  "max_long_g":         -0.92,
  "max_brake_bar":      45.0,
  "avg_throttle_pct":   38.0,
  "avg_steering_deg":   12.0,
  "coast_frames":       12,
  "trail_brake_frames": 4,
  "frame_count":        75,
  "corners_visited":    ["Turn 3"],
  "distance_m":         1450.0,
  "in_corner":          false,
  "past_apex":          false
}
```

**Response**:

```jsonc
{
  "coaching":     "Trail brake — hold pressure. Trail braking: 24.3 bar (33%)",   // sonic_model summary
  "pace_note":    "128, the Carousel, brake at just after the slight crest, downhill",
  "coach_source": "rule",        // "rule" | "litert" | "" (disabled)
  "cues":         [ /* serialized AudioCue list */ ],
  "burst_id":     7,
  "source":       "sonic_model"  // "sonic_model" | "bridge_rules"
}
```

If the request includes `session_id` and the pace note is non-empty, the bridge writes a row to the `coaching_notes` DuckDB table so `GET /session/<id>` can replay the coaching timeline.

### Session import (bulk historical data)

#### `POST /session/import`

Parse a `.vbo` file from disk, create a `sessions` row, persist every frame into the `telemetry` table — one call to ingest a full historical session into the backend. Use this when loading an existing recording (e.g. forza dataset, prior session reviewed offline).

```jsonc
// Request
{
  "vbo_path":     "/path/to/lap.vbo",
  "driver":       "Taha",            // optional, defaults to ""
  "driver_level": "intermediate",     // optional, default
  "session_id":   "my-custom-id",     // optional, auto-generated otherwise
  "note":         "Sunday testing"    // optional
}

// Response — 200 OK
{
  "session_id": "sonoma-raceway-20260428-201503",
  "n_frames":   8273,
  "duration_s": 1387.2,
  "distance_m": 95234.1,
  "vbo_source": "Sonoma Intermediate - 1_47.5.vbo"
}
```

**Status codes:**
- `200` — ingested successfully.
- `400` — `vbo_path` missing/invalid or no frames parsed.
- `409` — the requested `session_id` already has frames (idempotent guard). Use a different `session_id` or delete first.
- `503` — DuckDB unavailable.

The bulk equivalent for a directory of VBOs is `tools/bulk_import_sonoma_vbos.py`, which calls this same path internally per file.

### Session management

#### `POST /session/start`

Open a new session row in DuckDB. The caller may supply their own `session_id`; otherwise the bridge generates `<track-slug>-<UTC-YYYYMMDD-HHMMSS>`.

```jsonc
// Request
{
  "driver":       "Taha",
  "driver_level": "intermediate",
  "track":        "Sonoma Raceway",
  "car":          "BMW M3 (E46)",
  "note":         "Pre-sprint test"
}

// Response — 200 OK
{ "started": true, "session_id": "sonoma-raceway-20260428-170015" }
```

#### `POST /session/<sid>/end`

Close a session by stamping `ended_at = now()`. Idempotent — re-ending an already-closed session is a no-op.

```jsonc
{ "ended": true, "session_id": "sonoma-raceway-20260428-170015" }
```

#### `GET /sessions?limit=50&active_only=false`

List recent sessions, newest first. `active_only=true` filters to sessions without `ended_at`.

Each row carries derived `lap_count` and `best_lap_s` — useful for a leaderboard or session picker.

#### `GET /session/<sid>`

Full session detail: the session row, all `laps` belonging to it (ordered by `lap_number`), and the most recent 50 `coaching_notes`. Returns `404` when `sid` is unknown.

```jsonc
{
  "session":   { "session_id": "...", "driver": "...", "started_at": "...", ... },
  "laps":      [ { "lap_number": 1, "lap_time_s": 107.5, ... } ],
  "notes":     [ { "burst_id": 7, "distance_m": 350, "text": "185, left 6, brake at the bridge", "source": "rule", ... } ],
  "lap_count": 8,
  "best_lap_s": 107.5
}
```

### Lap CRUD

#### `POST /lap`

Save a completed lap.

```jsonc
{
  "session_id":    "sonoma-raceway-20260428-170015",
  "lap_number":    3,
  "lap_time_s":    107.5,
  "best_sector":   34.2,
  "avg_speed_kmh": 115.3,
  "max_combo_g":   1.84,
  "coast_pct":     5.4
}
```

#### `GET /laps?session_id=&limit=20`

Read lap history. Without `session_id`, returns the most recent `limit` laps across all sessions.

---

## DuckDB schema

The bridge initialises three tables on first use (`tools/pitwall_sessions.duckdb`):

```sql
CREATE TABLE sessions (
    session_id    VARCHAR PRIMARY KEY,
    driver        VARCHAR,
    driver_level  VARCHAR,
    track         VARCHAR,
    car           VARCHAR,
    started_at    TIMESTAMP DEFAULT now(),
    ended_at      TIMESTAMP,
    note          VARCHAR
);

CREATE TABLE laps (
    id            INTEGER PRIMARY KEY DEFAULT nextval('laps_id_seq'),
    session_id    VARCHAR,
    lap_number    INTEGER,
    lap_time_s    DOUBLE,
    best_sector   DOUBLE,
    avg_speed_kmh DOUBLE,
    max_combo_g   DOUBLE,
    coast_pct     DOUBLE,
    recorded_at   TIMESTAMP DEFAULT now()
);

CREATE TABLE coaching_notes (
    id            INTEGER PRIMARY KEY DEFAULT nextval('notes_id_seq'),
    session_id    VARCHAR,
    burst_id      INTEGER,
    distance_m    DOUBLE,
    text          VARCHAR,
    source        VARCHAR,
    recorded_at   TIMESTAMP DEFAULT now()
);
```

`session_id` is the join key. `coaching_notes.distance_m` lets the off-track review screen scrub coaching back-to-back with telemetry on the lap timeline.

Future: a `telemetry` table for per-frame persistence (foundation for the per-corner replay feature) — not yet built.

---

## Warm path

The Flutter app consumes coaching from one source — the bridge. Per [ADR-013](adr/013-frontend-backend-boundary.md), the backend owns all LLM logic and system prompts; the frontend visualizes only.

| Tier | Transport | Latency | Requires |
|---|---|---|---|
| 1 | Bridge `127.0.0.1:8765/analyze` | < 50 ms | Bridge running (`tools/pitwall_bridge.py`) |
| 2 | Mock | 0 ms | Always works (used in tests / when bridge is unreachable) |

The bridge runs the full `sonic_model` + `coach_engine` pipeline locally. With `--coach litert`, it executes Gemma 4 E2B inference in-process via MediaPipe Genai — no cloud round-trip, no API quota.

---

## Backwards compatibility

- The `/analyze` response is **additive** — `pace_note` and `coach_source` are new fields; existing Flutter deserializers that ignore unknown keys keep working.
- The new session endpoints are **opt-in** — existing flows that POST `/lap` without first calling `/session/start` continue to work; sessions just won't be linked.
- `version` in `/health` is bumped from `2.0` → `2.1` to signal these additions.

---

## Tested clients

- Flutter Pixel 10 app (`flutter/lib/platform/pitwall_channel.dart` → Kotlin `MessageArbiter`).
- `python3 tools/pitwall_bridge.py` smoke test: 5 synthetic bursts produce one valid pace note before the 3-second arbiter cooldown silences the rest (works as designed).
- `python3 tools/smoke_test_endpoints.py` end-to-end: ingests the Sonoma Intermediate VBO (8273 frames, 6.83 cumulative laps), streams 4 tall-store CAN-style signals into the ADR-015 sink, and asserts shape on every documented endpoint. 51/51 assertions green.
- `pitwall_app.py --simple --replay <vbo>` runs the same `coach_engine` in-process for development without the bridge.

---

## Roadmap endpoints — shipped 2026-04-29

| Endpoint | Purpose | ADR |
|---|---|---|
| `POST /session/<sid>/frame` | Append a single telemetry frame (per-corner replay foundation); returns assigned `frame_idx` | — |
| `GET /session/<sid>/corners` | Per-corner aggregates: best pass + averages over all passes, optional A–F grade + gold-standard delta when `data/reference/sonoma_gold.json` is loadable | — |
| `POST /score` | Gemini-graded session score (0–100 + one-sentence why); 503 when `GEMINI_API_KEY` unset | — |
| `GET /markers?corner=&kind=` | Filterable view over the track JSON's marker list | [ADR-011](adr/011-named-marker-schema.md) |
| `GET /coach/concepts` | The 9 Bentley pedagogical concepts the coach can fire, with description + when-fires hint | [ADR-012](adr/012-coach-engine-adapter.md) |

---

## Lap performance & analysis

A second batch of analytical endpoints, inspired by [WarmBed/PITWALL](https://github.com/WarmBed/PITWALL)'s F1 race-weekend tool, adapted for **single-driver multi-lap track-day** workflows. Most are thin SQL aggregations against the `telemetry` and `laps` tables — they exist to give the frontend ready-to-render data instead of a raw frame stream.

All eleven of these endpoints follow a common contract:

- **Authentication**: none (loopback only).
- **404** when the session has no frames in `telemetry` (caller should ingest with `POST /session/import` or `POST /session/<sid>/frames` first).
- **400** when the session exists but has < 1 complete lap (lap-detection requires at least one S/F crossing).
- **200** with the JSON body documented per endpoint.

### Lap detection model

Several endpoints (all the lap-time and sector ones) need to slice the per-frame stream into laps. The backend (`tools/pitwall_bridge.py:_detect_laps`) tries three strategies in order, picking the first that yields any laps. This handles the three real shapes of session data: cumulative-distance Racelogic VBOs, per-lap-resetting synthetic frames, and any data without reliable distance integration.

**Strategy 1 — cumulative distance (Racelogic VBO, default).** Used when the final frame's `distance_m > 1.5 × track_length`. Lap boundary = `floor(d / track_length)` increments. Each multiple of `track_length` is one full lap; the pre-first-boundary segment (out-lap from pit lane) is discarded.

**Strategy 2 — distance wraparound (synthetic / per-lap data).** Used when distance never exceeds `~1.5 × track_length` (i.e. `distance_m` resets toward 0 each lap). Lap boundary = a drop in `distance_m` greater than half the track length. The first segment is treated as lap 1 (synthetic data starts at distance 0, no out-lap).

**Strategy 3 — GPS perpendicular S/F crossing (fallback).** Used when neither distance pattern is present:

1. Take the S/F coordinate from the loaded track JSON's `start_finish` field (anonymized to match the dataset's GPS frame), falling back to `sonoma.py` constants (`SF_LAT = 38.16152`, `SF_LON = -122.45472`, `SF_HEADING_DEG = 354.2`) if the JSON has none.
2. For each frame `f`, project its (lat, lon) onto a local XY plane centred at S/F (small-angle approximation):
   $$x = (\text{lon}_f - \text{lon}_{\text{SF}}) \cdot \cos(\text{lat}_{\text{SF}}) \cdot R,\quad y = (\text{lat}_f - \text{lat}_{\text{SF}}) \cdot R,\quad R = 111{,}320\text{ m}$$
3. Compute signed perpendicular distance to the S/F line (line orientation = `SF_HEADING_DEG`):
   $$d_f = -x \sin(\theta) + y \cos(\theta),\quad \theta = \text{SF\_HEADING\_DEG}$$
4. A lap boundary is a frame where `d` changes from negative to non-negative, **and** the radial distance to the S/F point $\sqrt{x^2 + y^2} < 50$ m. The 50 m gate excludes crossings of the *infinite* perpendicular line that occur far from the physical S/F marker (e.g. parallel sections of the back straight).
5. Only complete crossing-to-crossing intervals are counted as laps. The pre-first-crossing segment (out-lap) and the post-last-crossing segment (in-lap) are discarded.

**Final filter (all strategies):** lap time = `t_end - t_start`. Laps shorter than 60 s or longer than 300 s are rejected as parser noise. Accepted laps are renumbered 1..N in time order.

Sector boundaries come from `sonoma.SECTORS`:

| Sector | Name | Start (m) | End (m) |
|---|---|---|---|
| 1 | Front Loop | 0 | 1294 |
| 2 | Carousel & Back | 1294 | 2752 |
| 3 | T10 to Calamity | 2752 | 4258 |

A frame "enters sector i" when its `distance_m` first crosses the sector's `start_m` within a lap. Sector time = `t_exit - t_enter`.

---

### `GET /session/<sid>/lap_time_table`

Per-lap times with sector splits, with the best lap and best sector flagged.

**Math.** For each detected lap *j*:

$$T_j = t^{j}_{\text{end}} - t^{j}_{\text{start}},\quad S^{j}_i = t^{j}_{\text{exit sector }i} - t^{j}_{\text{enter sector }i}$$

Best-lap and best-sector flags:

$$j^* = \arg\min_j T_j,\quad i^*_k = \arg\min_j S^{j}_k\text{ for each sector }k$$

`delta_to_best_s` for lap *j* is $T_j - T_{j^*}$.

**Response — 200**:

```jsonc
{
  "session_id":      "sonoma-raceway-20260428-201503",
  "lap_count":       22,
  "best_lap_s":      105.32,
  "best_lap_number": 7,
  "laps": [
    {
      "lap_number":      1,
      "lap_time_s":      110.48,
      "delta_to_best_s": 5.16,
      "is_best":         false,
      "sectors": [
        { "name": "Front Loop",       "time_s": 33.21, "is_best": false },
        { "name": "Carousel & Back",  "time_s": 38.45, "is_best": false },
        { "name": "T10 to Calamity",  "time_s": 38.82, "is_best": false }
      ]
    },
    {
      "lap_number":      7,
      "lap_time_s":      105.32,
      "delta_to_best_s": 0.0,
      "is_best":         true,
      "sectors": [
        { "name": "Front Loop",       "time_s": 32.45, "is_best": true },
        { "name": "Carousel & Back",  "time_s": 38.95, "is_best": false },
        { "name": "T10 to Calamity",  "time_s": 33.92, "is_best": true }
      ]
    }
    /* … */
  ]
}
```

**Frontend consumption.** Render as a leaderboard-style table with the best lap highlighted (purple in motorsport convention). Per cell, colour-code on `is_best` (purple) vs. session-best for that sector (green) vs. neither (default).

```dart
final laps = (await api.get('/session/$sid/lap_time_table'))['laps'] as List;
return ListView(children: laps.map((l) => LapRow(
  number: l['lap_number'],
  totalSec: l['lap_time_s'],
  deltaSec: l['delta_to_best_s'],
  isBest: l['is_best'],
  sectors: (l['sectors'] as List).map((s) => SectorCell(
    timeSec: s['time_s'],
    purple:  s['is_best'],
  )).toList(),
)).toList());
```

```bash
curl -s 127.0.0.1:8765/session/sonoma-raceway-20260428-201503/lap_time_table | jq '.best_lap_s'
```

---

### `GET /session/<sid>/lap_time_distribution`

Box-plot statistics over the session's lap times. Useful for "how consistent was I" panels.

**Math (Tukey's box-plot).** Sort lap times $T_1 \le T_2 \le \dots \le T_n$. Quantiles use linear interpolation:

$$Q_p = T_{\lfloor h \rfloor} + (h - \lfloor h \rfloor) \cdot (T_{\lceil h \rceil} - T_{\lfloor h \rfloor}),\quad h = p \cdot (n-1) + 1$$

Then:

$$\text{IQR} = Q_3 - Q_1$$

$$\text{whisker}_{\text{low}} = \min\{T_i : T_i \ge Q_1 - 1.5 \cdot \text{IQR}\}$$

$$\text{whisker}_{\text{high}} = \max\{T_i : T_i \le Q_3 + 1.5 \cdot \text{IQR}\}$$

$$\text{outlier}_i \iff T_i < Q_1 - 1.5 \cdot \text{IQR}\ \lor\ T_i > Q_3 + 1.5 \cdot \text{IQR}$$

Population mean and stddev:

$$\mu = \frac{1}{n}\sum_i T_i,\quad \sigma = \sqrt{\frac{1}{n}\sum_i (T_i - \mu)^2}$$

**Response — 200**:

```jsonc
{
  "session_id":      "sonoma-raceway-20260428-201503",
  "lap_count":       22,
  "min_s":           105.32,
  "max_s":           115.78,
  "q1_s":            107.45,
  "median_s":        108.92,
  "q3_s":            110.18,
  "iqr_s":           2.73,
  "whisker_low_s":   105.32,
  "whisker_high_s":  113.20,
  "outliers":        [{ "lap_number": 14, "lap_time_s": 115.78 }],
  "mean_s":          109.03,
  "stddev_s":        2.41
}
```

**Frontend consumption.** Render as a vertical box-plot widget. The numeric `stddev_s` doubles as a one-glance "consistency score".

---

### `GET /session/<sid>/ideal_lap`

Theoretical fastest lap = sum of best sector times across the session. Highlights how much the driver leaves on the table by never stringing all three best sectors into one lap.

**Math.** Let $S^{j}_i$ be lap *j*'s time in sector *i*. Then:

$$T^{*}_i = \min_j S^{j}_i,\quad T_{\text{ideal}} = \sum_{i=1}^{3} T^{*}_i$$

$$\Delta_{\text{gain}} = T_{\text{best actual}} - T_{\text{ideal}}\quad (\ge 0)$$

**Response — 200**:

```jsonc
{
  "session_id":          "sonoma-raceway-20260428-201503",
  "ideal_lap_s":         104.18,
  "best_actual_lap_s":   105.32,
  "gain_potential_s":    1.14,
  "best_sectors": [
    { "name": "Front Loop",      "time_s": 32.45, "from_lap": 7  },
    { "name": "Carousel & Back", "time_s": 37.81, "from_lap": 12 },
    { "name": "T10 to Calamity", "time_s": 33.92, "from_lap": 7  }
  ]
}
```

**Frontend consumption.** Display as a banner over the lap table — *"Your ideal lap is 1:44.18 — 1.14s under your best of 1:45.32"*. Tapping a sector card jumps the lap-table view to the lap that produced that sector best.

---

### `GET /session/<sid>/sector_times`

Thinner per-lap-per-sector view (no totals, no best flags). Useful when the frontend already has lap times from another endpoint and only wants splits.

**Math.** Same sector boundaries and timing as the lap-time table.

**Response — 200**:

```jsonc
{
  "session_id": "sonoma-raceway-20260428-201503",
  "sector_definitions": [
    { "name": "Front Loop",      "start_m": 0,    "end_m": 1294 },
    { "name": "Carousel & Back", "start_m": 1294, "end_m": 2752 },
    { "name": "T10 to Calamity", "start_m": 2752, "end_m": 4258 }
  ],
  "laps": [
    { "lap_number": 1,  "s1": 33.21, "s2": 38.45, "s3": 38.82 },
    { "lap_number": 2,  "s1": 33.05, "s2": 38.12, "s3": 38.61 }
    /* … */
  ]
}
```

**Frontend consumption.** Drives the "sector trend" chart — three lines (S1/S2/S3) on a shared x-axis (lap number).

---

### `GET /session/<sid>/pedal_behavior`

Distribution of frames across the four canonical pedal states. Reveals whether a driver coasts too much, never trail-brakes, etc.

**Math (per frame).** Two thresholds, both configurable via query params (`?throttle_th=5&brake_th=1.0`):

| State | Throttle (%) | Brake (bar) |
|---|---|---|
| `throttle_only` | > 5 | ≤ 1.0 |
| `brake_only` | ≤ 5 | > 1.0 |
| `trail_brake` | > 5 | > 1.0 |
| `coast` | ≤ 5 | ≤ 1.0 |

(Trail-braking is the *overlap region* — both pedals modulating simultaneously. This deviates from WarmBed's F1 model, which uses 95% as the throttle threshold; we use 5% because road-car drivers rarely sit at full WOT and instead modulate.)

For each state $s$:

$$n_s = |\{f : \text{state}(f) = s\}|,\quad \text{pct}_s = 100 \cdot \frac{n_s}{N},\quad \text{time}_s = n_s \cdot \overline{\Delta t}$$

where $\overline{\Delta t}$ is the median frame interval (≈ 0.1 s for Racelogic 10 Hz).

**Response — 200**:

```jsonc
{
  "session_id":  "sonoma-raceway-20260428-201503",
  "frame_count": 8273,
  "thresholds": { "throttle_pct": 5.0, "brake_bar": 1.0 },
  "frame_dt_s":  0.1,
  "states": {
    "throttle_only": { "frames": 4523, "pct": 54.7, "time_s": 452.3 },
    "brake_only":    { "frames": 1240, "pct": 15.0, "time_s": 124.0 },
    "trail_brake":   { "frames":  847, "pct": 10.2, "time_s":  84.7 },
    "coast":         { "frames": 1663, "pct": 20.1, "time_s": 166.3 }
  }
}
```

**Frontend consumption.** Render as a stacked horizontal bar (one bar = the lap, four colored segments). High `coast` % is the alarm signal — a coaching cue can pop up *"You're coasting 20% of the time. Trail-brake to corner entry instead of lifting early."*

---

### `GET /session/<sid>/throttle_corner_box`

Per-corner throttle-application statistics over all passes through that corner. Box-plot data per corner.

**Math (per corner *c*).** Collect `throttle_pct` from every frame where `entry_dist_c ≤ distance_m ≤ exit_dist_c`. Compute `min, q1, median, q3, max, mean` using the same quantile formula as the lap-time distribution.

**Response — 200**:

```jsonc
{
  "session_id": "sonoma-raceway-20260428-201503",
  "corners": [
    {
      "name":       "Turn 1",
      "n_passes":   22,
      "n_samples":  1430,
      "min_pct":    0.0,
      "q1_pct":     12.4,
      "median_pct": 35.1,
      "q3_pct":     67.8,
      "max_pct":    100.0,
      "mean_pct":   38.7
    }
    /* … one entry per corner in sonoma.json … */
  ]
}
```

**Frontend consumption.** Render as 11 vertical box-plots in a horizontal row. Low median + high IQR = "indecisive throttle" → a coaching focus.

---

### `GET /session/<sid>/corner_classification`

Group the track's corners into low/medium/high-speed bands by **apex speed** (the slowest point inside the corner). Lets the coach say *"you're losing time in the slow stuff, not the fast stuff"*.

**Math.** Apex speed for corner *c*:

$$v^{*}_c = \min_{f \in c} v_f$$

Bands (configurable via `?low_max=80&med_max=130`):

$$\text{band}(c) = \begin{cases} \text{low} & v^{*}_c < 80\text{ km/h} \\ \text{med} & 80 \le v^{*}_c < 130\text{ km/h} \\ \text{high} & v^{*}_c \ge 130\text{ km/h} \end{cases}$$

Per band: list of corners + mean apex speed across all passes in band.

**Response — 200**:

```jsonc
{
  "session_id": "sonoma-raceway-20260428-201503",
  "thresholds": { "low_max_kmh": 80, "med_max_kmh": 130 },
  "bands": [
    {
      "band":           "low_speed",
      "corners":        ["Turn 7", "Turn 11"],
      "mean_apex_kmh":  64.3,
      "median_apex_kmh": 65.0
    },
    {
      "band":           "med_speed",
      "corners":        ["Turn 1", "Turn 2", "Turn 3a", "Turn 4", "Turn 8a", "Turn 9"],
      "mean_apex_kmh":  101.2,
      "median_apex_kmh": 99.5
    },
    {
      "band":           "high_speed",
      "corners":        ["Turn 6", "Turn 10"],
      "mean_apex_kmh":  158.7,
      "median_apex_kmh": 160.1
    }
  ]
}
```

**Frontend consumption.** Drives a track-map overlay where each corner is shaded by its band. A pre-brief view summarises *"You're a low-speed corner driver — focus on T7/T11 to go faster"*.

---

### `GET /session/<sid>/straight_line_speed`

Top speed per named straight. Reveals draft / corner-exit-momentum issues without wading through all 8000+ frames.

**Named straights for Sonoma** (defined in `sonoma.STRAIGHTS`):

| Name | Start (m) | End (m) | Description |
|---|---|---|---|
| Front Straight | 4080 | 4258 (wraps to 60) | T11 exit through S/F into T1 brake zone |
| T4 Run | 600 | 880 | Short squirt out of T4 toward T5 |
| T7 → T8a | 1620 | 1820 | Downhill section — fastest sustained speed |

**Math (per straight *s*).**

$$v^{\text{top}}_s = \max_{f \in s} v_f,\quad f_s^{*} = \arg\max_{f \in s} v_f$$

Returned alongside the lap that produced the top speed.

**Response — 200**:

```jsonc
{
  "session_id": "sonoma-raceway-20260428-201503",
  "straights": [
    { "name": "Front Straight", "start_m": 4080, "end_m": 4258, "top_speed_kmh": 198.4, "from_lap": 7 },
    { "name": "T4 Run",         "start_m": 600,  "end_m": 880,  "top_speed_kmh": 138.7, "from_lap": 12 },
    { "name": "T7 → T8a",       "start_m": 1620, "end_m": 1820, "top_speed_kmh": 187.2, "from_lap": 11 }
  ]
}
```

**Frontend consumption.** Three speed badges on the track map, one per straight, coloured by improvement vs. last session.

---

### `GET /session/<sid>/brake_acceleration`

Heavy-braking deceleration scatter + corner-exit longitudinal acceleration. Two halves of a "how hard is the driver attacking the limit" view.

**Math — heavy-brake zones.** A frame is in a heavy-brake zone iff `brake_bar > 25`. For each contiguous run, compute peak deceleration:

$$a^{\text{decel}}_z = \min_{f \in z} g_{\text{long},f}\quad (\text{negative; in g})$$

Group runs by their nearest corner (smallest `|distance_m - entry_dist_c|`). Per corner: `max_decel_g = mean over zones`, `n_passes`.

**Math — corner exits.** A frame is "in a corner exit" iff `past_apex_c == True` AND `throttle_pct > 50%`. For each pass through corner *c*:

$$a^{\text{accel}}_c = \max_{f \in \text{exit}_c} g_{\text{long},f}$$

`exit_speed_kmh` = speed at the moment we cross the corner's `exit_distance_m`.

**Response — 200**:

```jsonc
{
  "session_id": "sonoma-raceway-20260428-201503",
  "brake_zones": [
    { "corner": "Turn 7",  "max_decel_g": -1.45, "duration_s": 1.8, "n_passes": 22 },
    { "corner": "Turn 10", "max_decel_g": -1.32, "duration_s": 2.2, "n_passes": 22 },
    { "corner": "Turn 11", "max_decel_g": -1.51, "duration_s": 1.6, "n_passes": 22 }
  ],
  "corner_exits": [
    { "corner": "Turn 11", "max_long_accel_g": 0.85, "exit_speed_kmh": 145.2, "n_passes": 22 },
    { "corner": "Turn 4",  "max_long_accel_g": 0.71, "exit_speed_kmh": 118.4, "n_passes": 22 }
  ]
}
```

**Frontend consumption.** Render as paired horizontal bar charts: top half decel-per-corner (red, negative), bottom half exit-accel-per-corner (green). Tappable bars filter the lap-time table to passes in that corner.

---

### `GET /track/<id>/elevation`

Elevation profile along the centerline of any track in `data/tracks/<id>.json`.

**Track ID.** The `<id>` path parameter is the JSON filename stem in `data/tracks/`. At time of writing, only `sonoma` ships, but the parameterization lets us drop additional tracks in without code changes.

```bash
ls data/tracks/
# sonoma.json  →  GET /track/sonoma/elevation
# laguna_seca.json (future) →  GET /track/laguna_seca/elevation
```

**Math.** The track JSON's `reference_line` is sampled at every `step_m` (default 10 m, configurable via `?step_m=20`):

$$\text{samples}_k = (d_k = k \cdot \text{step},\ z_k = \text{interp}(\text{reference\_line.elevation}, d_k))$$

If the JSON has no per-sample elevation (legacy data), the bridge falls back to `[null]` for `elevation_m` and the response carries `"elevation_source": "missing"`.

**Response — 200**:

```jsonc
{
  "track_id":         "sonoma",
  "name":             "Sonoma Raceway",
  "track_length_m":   4258,
  "step_m":           10,
  "elevation_source": "osm_srtm",
  "min_elevation_m":  173.2,
  "max_elevation_m":  214.8,
  "samples": [
    { "distance_m": 0,    "elevation_m": 198.4 },
    { "distance_m": 10,   "elevation_m": 199.1 },
    /* … 426 samples for a 4.26km track at 10m step … */
    { "distance_m": 4250, "elevation_m": 197.9 }
  ]
}
```

**Status codes:**
- `200` — profile returned.
- `404` — `data/tracks/<id>.json` not found.
- `422` — track JSON exists but has no `reference_line` (cannot derive a profile).

**Frontend consumption.** Render as an area chart under the lap-trace plot. Drives the "downhill into T9, watch the entry speed" coaching cue.

---

### `GET /driver/<id>/evolution?track=sonoma`

Multi-session driver evolution on a track. Built on top of `driver_profile.compute_profile` (which already tracks events) but exposed as a flat time-series for chart rendering.

**Math.** Find all sessions $\{S_1, S_2, \dots, S_n\}$ where `driver_id = <id>` AND (`track = <track>` OR `<track>` unspecified), ordered by `started_at` ascending.

For each session $S_k$:

- $T_{\text{best},k} = \min_{j} T_{j,k}$ (best lap time)
- $T_{\text{med},k} = \text{median}_j T_{j,k}$ (median lap time)
- For each sector *i*: $T_{\text{pb},i,k} = \min_j S^{j,k}_i$
- Lap count, smoothness score (from `analytics.smoothness_per_corner`)

Summary deltas:

$$\Delta_{\text{best}} = T_{\text{best},1} - T_{\text{best},n},\quad \Delta_{\text{med}} = T_{\text{med},1} - T_{\text{med},n}$$

For the biggest-corner-gain heuristic: per corner *c*, regress `apex_speed_c` against session index. Pick the corner with steepest positive slope:

$$c^{*} = \arg\max_c \frac{\sum_k (k - \bar{k})(v^{*}_{c,k} - \bar{v}^{*}_c)}{\sum_k (k - \bar{k})^2}$$

**Response — 200**:

```jsonc
{
  "driver_id":     "taha",
  "track":         "Sonoma Raceway",
  "session_count": 22,
  "evolution": [
    {
      "session_id":    "sonoma-raceway-20240301-150000",
      "started_at":    "2024-03-01T15:00:00Z",
      "session_index": 1,
      "best_lap_s":    110.45,
      "median_lap_s":  113.20,
      "lap_count":     12,
      "sector_pbs":    { "s1": 34.20, "s2": 39.10, "s3": 37.40 }
    }
    /* … 21 more sessions … */
  ],
  "summary": {
    "first_best_s":           110.45,
    "latest_best_s":          105.32,
    "improvement_s":          5.13,
    "biggest_corner_gain": {
      "corner":               "Turn 11",
      "delta_kmh":            8.2,
      "from_session_index":   1,
      "to_session_index":     22,
      "regression_slope_kmh_per_session": 0.43
    }
  }
}
```

**Status codes:**
- `200` — evolution data computed.
- `404` — driver has zero sessions for the requested track.
- `204` — driver has < 5 sessions for the requested track (evolution requires a baseline; the frontend should hide the panel).

**Frontend consumption.** Render as a multi-line time-series (best lap + median lap + S1/S2/S3 PBs) with the session index on the x-axis. The `biggest_corner_gain` summary bubbles up to a hero card: *"You've gained 8.2 km/h apex speed at Turn 11 since session #1"*.

```dart
final r = await api.get('/driver/$id/evolution', params: {'track': 'sonoma'});
if (r.statusCode == 204) return EvolutionEmptyState(needsMoreSessions: 5 - r['session_count']);
final hero = r['summary']['biggest_corner_gain'];
final chart = LineChart.fromSeries(r['evolution'], xKey: 'session_index', ySeries: [
  ('Best lap',   (s) => s['best_lap_s'],   purple),
  ('Median lap', (s) => s['median_lap_s'], grey),
  ('S1 PB',      (s) => s['sector_pbs']['s1'], red),
  ('S2 PB',      (s) => s['sector_pbs']['s2'], green),
  ('S3 PB',      (s) => s['sector_pbs']['s3'], blue),
]);
```

---

## Quick reference — full endpoint catalogue (post Phase 6)

| Group | Endpoint | Method | Returns |
|---|---|---|---|
| meta | `/health` | GET | engine + coach status |
| meta | `/insights` | GET | aggregate ML/coaching insights |
| meta | `/track/markers` | GET | 16 named markers |
| meta | `/track/danger_zones` | GET | 3 known danger zones |
| meta | `/track/weather` | GET | weather phase by `?hour_local=` |
| meta | `/track/<id>/elevation` | GET | **NEW** elevation profile |
| coach | `/analyze` | POST | per-burst coaching |
| coach | `/coach/brief` | GET | pre-session narrative |
| coach | `/coach/debrief` | POST | post-session bundle |
| lifecycle | `/session/start` | POST | open a session row (custom `session_id` optional) |
| lifecycle | `/session/<sid>/end` | POST | stamp `ended_at = now()` (idempotent) |
| lifecycle | `/sessions` | GET | list sessions; `?active_only=true` hides ended |
| lifecycle | `/session/<sid>` | GET | full detail: session + laps + recent notes |
| ingest | `/session/<sid>/frames` | POST | append telemetry batch |
| ingest | `/session/<sid>/frame` | POST | **NEW** append a single frame; returns `frame_idx` |
| ingest | `/session/<sid>/video_frames` | POST | append video frame metadata |
| ingest | `/session/<sid>/signals` | POST | **ADR-015** append (name, t, value) tuples to tall sink |
| ingest | `/session/import` | POST | parse VBO + persist |
| ingest | `/session/reset` | POST | clear in-memory bundles |
| sink | `/signals/registry` | GET | **ADR-015** full signal catalog (54 seeded + discovered) |
| sink | `/session/<sid>/capabilities` | GET | **ADR-015** per-session signals + `coaches_available`/`disabled` (Phase 4) |
| sink | `/session/<sid>/capabilities/recompute` | POST | **ADR-015** trigger capability recomputation |
| sink | `/session/<sid>/signals` | GET | **ADR-015** synchroniser: `?names=&axis=&rate_hz=&interp=&t_from=&t_to=` |
| analysis | `/session/<sid>/sync` | GET | telemetry × video at ±50ms |
| analysis | `/session/<sid>/scorecard` | GET | A–F per corner |
| analysis | `/session/<sid>/highlights` | GET | top 8 moments |
| analysis | `/session/<sid>/stats` | GET | session aggregates |
| analysis | `/session/<sid>/friction_circle` | GET | gLat × gLong scatter |
| analysis | `/session/<sid>/hustle_map` | GET | per-segment 100% throttle frac |
| analysis | `/session/<sid>/eob` | GET | end-of-braking summary |
| analysis | `/session/<sid>/incidents` | GET | flagged events |
| analysis | `/session/<sid>/map` | GET | track + lap GPS overlay |
| analysis | `/session/<sid>/clips` | GET | video clip cuts |
| analysis | `/session/<sid>/lap_time_table` | GET | **NEW** per-lap + sector splits |
| analysis | `/session/<sid>/lap_time_distribution` | GET | **NEW** Tukey box-plot stats |
| analysis | `/session/<sid>/ideal_lap` | GET | **NEW** sum of best sectors |
| analysis | `/session/<sid>/sector_times` | GET | **NEW** thin S1/S2/S3 view |
| analysis | `/session/<sid>/pedal_behavior` | GET | **NEW** 4-state distribution |
| analysis | `/session/<sid>/throttle_corner_box` | GET | **NEW** per-corner throttle box-plot |
| analysis | `/session/<sid>/corner_classification` | GET | **NEW** low/med/high speed bands |
| analysis | `/session/<sid>/straight_line_speed` | GET | **NEW** top speed per straight |
| analysis | `/session/<sid>/brake_acceleration` | GET | **NEW** decel + exit-accel scatter |
| analysis | `/session/<sid>/corners` | GET | **NEW** per-corner aggregates + best pass + optional gold delta |
| profile | `/driver/<id>/profile` | GET | event-sourced profile |
| profile | `/driver/<id>/evolution` | GET | **NEW** multi-session time-series (204 if <5 sessions) |
| laps | `/lap` | POST | save a completed lap |
| laps | `/laps` | GET | list laps |
| roadmap | `/score` | POST | **NEW** Gemini-graded session score (503 without `GEMINI_API_KEY`) |
| roadmap | `/markers` | GET | **NEW** filterable markers `?corner=&kind=` |
| roadmap | `/coach/concepts` | GET | **NEW** 9 Bentley pedagogical concepts with description + `fires_when` |
