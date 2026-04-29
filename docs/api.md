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
- `pitwall_app.py --simple --replay <vbo>` runs the same `coach_engine` in-process for development without the bridge.

---

## Roadmap (proposed, not yet built)

| Endpoint | Purpose | ADR |
|---|---|---|
| `POST /session/<sid>/frame` | Append a single telemetry frame (per-corner replay foundation) | TBD |
| `GET /session/<sid>/corners` | Per-corner aggregates: best pass, grade A–F, gold-standard delta | TBD |
| `POST /score` | Gemini-graded session score (0–100 + one-sentence why) | TBD |
| `GET /markers?corner=Turn+11` | Track marker lookup (replaces hard-coded corner data on Flutter side) | [ADR-011](adr/011-named-marker-schema.md) |
| `GET /coach/concepts` | List active pedagogical-vector concepts the coach can fire | [ADR-012](adr/012-coach-engine-adapter.md) |
