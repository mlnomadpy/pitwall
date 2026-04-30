#!/usr/bin/env python3
"""
pitwall_bridge.py — Local HTTP bridge server for the Pitwall Android app.

Integrates the full src/simulator coaching stack (sonic_model, track_loader,
vbo_parser) so Android gets real cues — not stub rules.

Endpoints:
    GET  /health           → {"status": "ok", "engine": "sonic_model" | "rules"}
    POST /analyze          → telemetry burst JSON → coaching message + cues
    GET  /laps             → lap history from DuckDB
    POST /lap              → save a completed lap record

Install:
    pip3 install flask duckdb

Run from repo root (so imports resolve):
    python3 tools/pitwall_bridge.py --track src/simulator/sonoma.json

Emulator tunnel (once per adb session):
    ~/Library/Android/sdk/platform-tools/adb reverse tcp:8765 tcp:8765
"""

import argparse
import json
import os
import sys
import threading
import json
import time
from datetime import datetime
from typing import Optional
# Cloud Gemini was removed 2026-04-29 — every LLM call now goes through
# coach_engine.LitertCoach (on-device Gemma 4 E2B via litert-lm). The flag
# is kept only so older code paths can short-circuit cleanly.
HAS_GENAI = False

# Load .env file
env_path = os.path.join(os.path.dirname(__file__), "..", ".env")
if os.path.exists(env_path):
    with open(env_path) as f:
        for line in f:
            if line.strip() and not line.startswith("#"):
                key, val = line.strip().split("=", 1)
                os.environ[key.strip()] = val.strip().strip('"\'')

from flask import Flask, request, jsonify, Response, stream_with_context

# ── Add src/simulator to path so we can import the real coaching engine ────────
SIM_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "simulator")
sys.path.insert(0, os.path.abspath(SIM_DIR))

try:
    from sonic_model import compute_cues, AudioCue, Pattern
    from track_loader import load_track, find_nearest_corner, distance_to_corner
    HAS_SONIC = True
    print("✓  sonic_model loaded")
except ImportError as e:
    HAS_SONIC = False
    print(f"⚠  sonic_model not available ({e}) — falling back to rule engine")

try:
    from coach_engine import (
        make_coach, CoachArbiter, build_context, set_friction_logger,
    )
    _coach = make_coach(kind="auto")
    _arbiter = CoachArbiter()
    print(f"✓  coach_engine loaded ({_coach.name})")
except ImportError as e:
    _coach = None
    _arbiter = None
    set_friction_logger = None  # type: ignore[assignment]
    print(f"⚠  coach_engine not available ({e})")

try:
    import sonoma                                               # noqa: F401
    from session_analyzer import analyze_session
    from driver_profile import (
        ensure_schema as ensure_driver_schema, append_session_events,
        compute_profile,
    )
    HAS_ANALYZER = True
    print("✓  session_analyzer + driver_profile loaded")
except ImportError as e:
    HAS_ANALYZER = False
    print(f"⚠  session_analyzer not available ({e}) — debrief disabled")

try:
    import duckdb
    HAS_DUCKDB = True
except ImportError:
    HAS_DUCKDB = False

# coach_engine integration — track whether the coach hooks loaded
HAS_COACH = False
try:
    from coach_engine import make_coach as _make_coach   # noqa: F401
    HAS_COACH = True
except ImportError:
    pass
    print("⚠  duckdb not installed — lap history disabled. pip3 install duckdb")

# ── Global state ───────────────────────────────────────────────────────────────
app = Flask(__name__)
_track = None           # loaded on startup via --track
_db_lock = threading.Lock()
DB_PATH = os.path.join(os.path.dirname(__file__), "pitwall_sessions.duckdb")

# Session burst accumulator — cleared by POST /session/reset
_session_bursts: list = []
_burst_lock = threading.Lock()


# ── DuckDB helpers ─────────────────────────────────────────────────────────────
def get_db():
    if not HAS_DUCKDB:
        return None
    conn = duckdb.connect(DB_PATH)
    conn.execute("""
        CREATE SEQUENCE IF NOT EXISTS laps_id_seq;
        CREATE TABLE IF NOT EXISTS laps (
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
        CREATE SEQUENCE IF NOT EXISTS notes_id_seq;
        CREATE TABLE IF NOT EXISTS coaching_notes (
            id            INTEGER PRIMARY KEY DEFAULT nextval('notes_id_seq'),
            session_id    VARCHAR,
            burst_id      INTEGER,
            distance_m    DOUBLE,
            text          VARCHAR,
            source        VARCHAR,
            recorded_at   TIMESTAMP DEFAULT now()
        );
        CREATE TABLE IF NOT EXISTS telemetry (
            session_id   VARCHAR,
            frame_idx    INTEGER,
            timestamp    DOUBLE,
            distance_m   DOUBLE,
            speed_ms     DOUBLE,
            g_lat        DOUBLE,
            g_long       DOUBLE,
            combo_g      DOUBLE,
            brake_bar    DOUBLE,
            throttle_pct DOUBLE,
            steering_deg DOUBLE,
            rpm          DOUBLE,
            lat          DOUBLE,
            lon          DOUBLE
        );
        CREATE INDEX IF NOT EXISTS idx_telemetry_session
            ON telemetry(session_id, frame_idx);
        CREATE TABLE IF NOT EXISTS video_frames (
            session_id    VARCHAR,
            timestamp     DOUBLE,    -- epoch seconds, canonical clock
            avitime_ms    BIGINT,    -- VBO avitime when synced from Racelogic
            file_path     VARCHAR,   -- on-disk MP4 (chunked, e.g. ~10s segments)
            file_offset_s DOUBLE,    -- seconds into file_path
            width         INTEGER,
            height        INTEGER
        );
        CREATE INDEX IF NOT EXISTS idx_video_frames_session_t
            ON video_frames(session_id, timestamp);

        -- ADR-015: Universal Telemetry Sink (Phase 1 — schema + registry) ──
        CREATE SEQUENCE IF NOT EXISTS signal_registry_id_seq;
        CREATE TABLE IF NOT EXISTS signal_registry (
            signal_id     INTEGER PRIMARY KEY DEFAULT nextval('signal_registry_id_seq'),
            name          VARCHAR UNIQUE NOT NULL,
            units         VARCHAR,
            semantics     VARCHAR,
            "group"       VARCHAR,
            expected_hz   DOUBLE,
            min_useful_hz DOUBLE,
            discovery     VARCHAR,
            obd2_pid      VARCHAR,
            discovered_at TIMESTAMP DEFAULT now()
        );
        CREATE TABLE IF NOT EXISTS telemetry_signals (
            session_id  VARCHAR NOT NULL,
            signal_id   INTEGER NOT NULL,
            t           DOUBLE  NOT NULL,
            value       DOUBLE  NOT NULL,
            PRIMARY KEY (session_id, signal_id, t)
        );
        CREATE INDEX IF NOT EXISTS idx_signals_sess_sig_t
            ON telemetry_signals (session_id, signal_id, t);
        CREATE TABLE IF NOT EXISTS session_capabilities (
            session_id  VARCHAR NOT NULL,
            signal_id   INTEGER NOT NULL,
            n_samples   INTEGER NOT NULL,
            mean_hz     DOUBLE  NOT NULL,
            t_start     DOUBLE  NOT NULL,
            t_end       DOUBLE  NOT NULL,
            PRIMARY KEY (session_id, signal_id)
        );

        -- Phase-6: lightweight sessions catalog. Auto-upserted on ingest.
        -- Powers /driver/<id>/evolution; otherwise optional metadata.
        CREATE TABLE IF NOT EXISTS sessions (
            session_id    VARCHAR PRIMARY KEY,
            driver        VARCHAR,
            driver_level  VARCHAR,
            track         VARCHAR,
            car           VARCHAR,
            started_at    TIMESTAMP DEFAULT now(),
            ended_at      TIMESTAMP,
            note          VARCHAR
        );

        -- ADR-018: LLM friction sink. Every LitertCoach._generate call lands
        -- a row here so we can spot edge degradation (latency spikes,
        -- truncations, fallbacks) at 130 mph instead of after the fact.
        CREATE SEQUENCE IF NOT EXISTS llm_friction_id_seq;
        CREATE TABLE IF NOT EXISTS llm_friction (
            id               INTEGER PRIMARY KEY DEFAULT nextval('llm_friction_id_seq'),
            session_id       VARCHAR,
            role             VARCHAR,           -- 'brief' | 'cue' | 'debrief'
            mode             VARCHAR,           -- DURING_DRIVE | PRE_BRIEF | POST_SESSION
            backend          VARCHAR,
            prompt_chars     INTEGER,
            completion_chars INTEGER,
            latency_ms       DOUBLE,
            truncated        BOOLEAN,
            fell_back        BOOLEAN,           -- true if templated fallback was used
            error            VARCHAR,
            emotion          VARCHAR,
            ts               TIMESTAMP DEFAULT now()
        );
        CREATE INDEX IF NOT EXISTS idx_llm_friction_session_ts
            ON llm_friction (session_id, ts);
    """)
    return conn


# ── ADR-015: Signal registry seeding ───────────────────────────────────────────

REGISTRY_SEED_PATH = os.path.abspath(os.path.join(
    os.path.dirname(__file__), "..", "data", "registry", "obd2_pids.json",
))


def seed_signal_registry() -> int:
    """Idempotently seed signal_registry from data/registry/obd2_pids.json.

    Returns the number of rows inserted (0 on subsequent calls — INSERT OR
    IGNORE preserves any unit-stamping a human did on previously-discovered
    signals).
    """
    if not HAS_DUCKDB or not os.path.exists(REGISTRY_SEED_PATH):
        return 0
    with open(REGISTRY_SEED_PATH) as fh:
        seed = json.load(fh)
    rows = seed.get("signals", [])
    inserted = 0
    with _db_lock:
        conn = get_db()
        if conn is None:
            return 0
        for s in rows:
            try:
                conn.execute(
                    """INSERT INTO signal_registry
                       (name, units, semantics, "group", expected_hz,
                        min_useful_hz, discovery, obd2_pid)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                       ON CONFLICT (name) DO NOTHING""",
                    [s["name"], s.get("units"), s.get("semantics"),
                     s.get("group"), s.get("expected_hz"),
                     s.get("min_useful_hz"), s.get("discovery"),
                     s.get("obd2_pid")],
                )
                inserted += 1
            except Exception:
                pass
        conn.close()
    return inserted


# Wide-table columns that double as registry signals — used by capability
# computation to advertise canonical fields without round-tripping through
# the tall store.
_WIDE_SIGNAL_NAMES = (
    "distance_m", "speed_ms", "g_lat", "g_long", "combo_g",
    "brake_bar", "throttle_pct", "steering_deg", "rpm", "lat", "lon",
)


def _resolve_signal_id(conn, name: str) -> int:
    """Look up signal_id by name; auto-register a novel signal as 'discovered'.

    Discovered signals get units=NULL — the coach treats them as logged but
    not coachable until a human stamps the units in the registry.
    """
    row = conn.execute(
        "SELECT signal_id FROM signal_registry WHERE name = ?", [name],
    ).fetchone()
    if row is not None:
        return row[0]
    conn.execute(
        """INSERT INTO signal_registry (name, units, discovery)
           VALUES (?, NULL, 'discovered')""",
        [name],
    )
    return conn.execute(
        "SELECT signal_id FROM signal_registry WHERE name = ?", [name],
    ).fetchone()[0]


def _compute_capabilities(sid: str) -> int:
    """Aggregate (signal_id, n_samples, mean_hz, t_start, t_end) per session.

    Reads from BOTH the wide telemetry table (for canonical fields) and
    telemetry_signals (for everything else) and rewrites session_capabilities
    for the session. Returns the number of capability rows written.
    """
    if not HAS_DUCKDB:
        return 0
    with _db_lock:
        conn = get_db()
        if conn is None:
            return 0

        conn.execute("DELETE FROM session_capabilities WHERE session_id = ?", [sid])

        rows_written = 0

        # 1. Wide-table canonical fields — every present session has all 11.
        n, t_start, t_end = conn.execute(
            "SELECT COUNT(*), MIN(timestamp), MAX(timestamp) "
            "FROM telemetry WHERE session_id = ?",
            [sid],
        ).fetchone()
        if n and n > 0 and t_start is not None and t_end is not None:
            duration = max(t_end - t_start, 1e-6)
            mean_hz = n / duration
            placeholders = ",".join(["?"] * len(_WIDE_SIGNAL_NAMES))
            sigs = conn.execute(
                f"SELECT signal_id FROM signal_registry WHERE name IN ({placeholders})",
                list(_WIDE_SIGNAL_NAMES),
            ).fetchall()
            for (sig_id,) in sigs:
                conn.execute(
                    "INSERT INTO session_capabilities VALUES (?, ?, ?, ?, ?, ?)",
                    [sid, sig_id, n, mean_hz, t_start, t_end],
                )
                rows_written += 1

        # 2. Tall store — variable rate per signal; tall wins on overlap.
        tall = conn.execute(
            """SELECT signal_id, COUNT(*), MIN(t), MAX(t)
               FROM telemetry_signals
               WHERE session_id = ?
               GROUP BY signal_id""",
            [sid],
        ).fetchall()
        for sig_id, ns, ts, te in tall:
            duration = max((te - ts), 1e-6)
            hz = ns / duration
            conn.execute(
                """INSERT INTO session_capabilities VALUES (?, ?, ?, ?, ?, ?)
                   ON CONFLICT (session_id, signal_id) DO UPDATE SET
                       n_samples = excluded.n_samples,
                       mean_hz   = excluded.mean_hz,
                       t_start   = excluded.t_start,
                       t_end     = excluded.t_end""",
                [sid, sig_id, ns, hz, ts, te],
            )
            rows_written += 1

        conn.close()
    return rows_written


# ── ADR-018: LLM friction sink ─────────────────────────────────────────────

def _log_llm_friction(rec: dict) -> None:
    """Persist one LitertCoach friction record. Called from coach_engine via
    `set_friction_logger`, so it must be silent on failure — a misbehaving
    sink mustn't stall the inference call."""
    if not HAS_DUCKDB:
        return
    try:
        with _db_lock:
            conn = get_db()
            if conn is None:
                return
            try:
                conn.execute(
                    """INSERT INTO llm_friction
                       (session_id, role, mode, backend, prompt_chars,
                        completion_chars, latency_ms, truncated, fell_back,
                        error, emotion)
                       VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                    [
                        rec.get("session_id"),
                        rec.get("role", ""),
                        rec.get("mode", ""),
                        rec.get("backend", ""),
                        int(rec.get("prompt_chars") or 0),
                        int(rec.get("completion_chars") or 0),
                        float(rec.get("latency_ms") or 0.0),
                        bool(rec.get("truncated", False)),
                        bool(rec.get("fell_back", False)),
                        (rec.get("error") or "")[:512],
                        rec.get("emotion") or "",
                    ],
                )
            finally:
                conn.close()
    except Exception:
        pass


# Register at import — only if both coach + duckdb are available.
if HAS_COACH and HAS_DUCKDB and set_friction_logger is not None:
    try:
        set_friction_logger(_log_llm_friction)
    except Exception:
        pass


# ── ADR-015 Phase 3: synchroniser helpers ──────────────────────────────────

def _read_signal(conn, sid: str, name: str,
                 t_from=None, t_to=None) -> list:
    """Return sorted [(t, value), ...] for a signal in either store.

    Resolves wide-table canonicals (speed_ms, brake_bar, …) directly off
    the wide column; everything else routes through telemetry_signals.
    Returns [] if the signal is unknown or has no samples for this session.
    """
    if name in _WIDE_SIGNAL_NAMES:
        sql = (f"SELECT timestamp, {name} FROM telemetry "
               "WHERE session_id = ?")
        params: list = [sid]
        if t_from is not None:
            sql += " AND timestamp >= ?"
            params.append(t_from)
        if t_to is not None:
            sql += " AND timestamp <= ?"
            params.append(t_to)
        sql += " ORDER BY timestamp"
        return [(float(t), float(v)) for t, v in conn.execute(sql, params).fetchall()
                if t is not None and v is not None]
    row = conn.execute(
        "SELECT signal_id FROM signal_registry WHERE name = ?", [name],
    ).fetchone()
    if row is None:
        return []
    sig_id = row[0]
    sql = ("SELECT t, value FROM telemetry_signals "
           "WHERE session_id = ? AND signal_id = ?")
    params = [sid, sig_id]
    if t_from is not None:
        sql += " AND t >= ?"
        params.append(t_from)
    if t_to is not None:
        sql += " AND t <= ?"
        params.append(t_to)
    sql += " ORDER BY t"
    return [(float(t), float(v)) for t, v in conn.execute(sql, params).fetchall()]


def _interp_hold(axis_ts: list, samples: list) -> list:
    """ASOF: for each axis_t, return v of last (t,v) with t ≤ axis_t; else None."""
    if not samples:
        return [None] * len(axis_ts)
    out = []
    j = 0
    n = len(samples)
    for at in axis_ts:
        while j < n and samples[j][0] <= at:
            j += 1
        out.append(None if j == 0 else samples[j - 1][1])
    return out


def _interp_lerp(axis_ts: list, samples: list) -> list:
    """Linear interp between bracketing samples; None outside the sample range."""
    if not samples:
        return [None] * len(axis_ts)
    out = []
    n = len(samples)
    j = 0
    for at in axis_ts:
        while j < n and samples[j][0] < at:
            j += 1
        if j == 0:
            out.append(samples[0][1] if samples[0][0] == at else None)
        elif j == n:
            out.append(samples[-1][1] if samples[-1][0] == at else None)
        else:
            t0, v0 = samples[j - 1]
            t1, v1 = samples[j]
            out.append(v0 if t1 == t0 else v0 + (v1 - v0) * (at - t0) / (t1 - t0))
    return out


def _interp(axis_ts: list, samples: list, kind: str) -> list:
    return _interp_lerp(axis_ts, samples) if kind == "lerp" else _interp_hold(axis_ts, samples)


# ── Phase-6: lap detection + sector splitting + session helpers ─────────────

_LAP_MIN_S = 60.0
_LAP_MAX_S = 300.0


def _session_has_telemetry(sid: str) -> bool:
    if not HAS_DUCKDB:
        return False
    with _db_lock:
        conn = get_db()
        if conn is None:
            return False
        n = conn.execute(
            "SELECT COUNT(*) FROM telemetry WHERE session_id = ?", [sid],
        ).fetchone()[0]
        conn.close()
    return bool(n)


def _reset_live_session():
    """Drop all rows for the synthetic `_live` session.

    Called on bridge boot when the CAN reader is launched without an
    explicit `--can-session-id`. Keeps stale values from a previous run
    out of the Pit Stall Setup live-state view.
    """
    if not HAS_DUCKDB:
        return
    with _db_lock:
        conn = get_db()
        if conn is None:
            return
        try:
            conn.execute("DELETE FROM telemetry            WHERE session_id = ?", ["_live"])
            conn.execute("DELETE FROM telemetry_signals    WHERE session_id = ?", ["_live"])
            conn.execute("DELETE FROM session_capabilities WHERE session_id = ?", ["_live"])
            conn.execute("DELETE FROM coaching_notes       WHERE session_id = ?", ["_live"])
        finally:
            conn.close()


def _ensure_session_row(sid: str, *, driver=None, driver_level=None,
                        track=None, car=None, note=None):
    """Idempotently upsert a sessions row. Called on every ingest path."""
    if not HAS_DUCKDB:
        return
    with _db_lock:
        conn = get_db()
        if conn is None:
            return
        existing = conn.execute(
            "SELECT driver, driver_level, track, car, note "
            "FROM sessions WHERE session_id = ?", [sid],
        ).fetchone()
        if existing is None:
            conn.execute(
                "INSERT INTO sessions (session_id, driver, driver_level, track, car, note) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                [sid, driver, driver_level, track, car, note],
            )
        else:
            cur = dict(zip(
                ["driver", "driver_level", "track", "car", "note"], existing,
            ))
            merged = {
                "driver":       driver       if driver       is not None else cur["driver"],
                "driver_level": driver_level if driver_level is not None else cur["driver_level"],
                "track":        track        if track        is not None else cur["track"],
                "car":          car          if car          is not None else cur["car"],
                "note":         note         if note         is not None else cur["note"],
            }
            conn.execute(
                """UPDATE sessions SET driver = ?, driver_level = ?,
                                       track = ?, car = ?, note = ?
                   WHERE session_id = ?""",
                [merged["driver"], merged["driver_level"], merged["track"],
                 merged["car"], merged["note"], sid],
            )
        conn.close()


def _detect_laps(sid: str) -> list:
    """Detect complete laps from the wide telemetry table.

    Two strategies, tried in order:
      1. Distance wraparound — `distance_m` resets toward 0 after passing
         the track length. Works for synthetic per-lap data.
      2. GPS perpendicular S/F crossing — uses the loaded track's
         start_finish lat/lon/heading. Works for cumulative-distance data
         (real Racelogic VBO output).

    Lap times outside [60, 300] s are rejected as parser noise.
    """
    if not HAS_DUCKDB:
        return []
    with _db_lock:
        conn = get_db()
        if conn is None:
            return []
        rows = conn.execute(
            "SELECT timestamp, distance_m, lat, lon "
            "FROM telemetry WHERE session_id = ? "
            "ORDER BY frame_idx",
            [sid],
        ).fetchall()
        conn.close()
    if len(rows) < 10:
        return []

    # Three strategies, picked by data shape:
    # 1. Cumulative distance (Racelogic VBO): distance_m grows past
    #    track_length without wrapping — detect lap boundaries at multiples
    #    of track_length.
    # 2. Distance wraparound: distance_m resets toward 0 each lap (synthetic
    #    per-lap data).
    # 3. GPS perpendicular S/F crossing — fallback when neither distance
    #    pattern is present.
    final_d = next((r[1] for r in reversed(rows) if r[1] is not None), 0.0) or 0.0
    track_len = float(getattr(sonoma, "TRACK_LENGTH_M", 4258))
    if final_d > track_len * 1.5:
        laps = _laps_via_cumulative_distance(rows, track_len)
    else:
        laps = _laps_via_distance_wrap(rows)
    if not laps:
        laps = _laps_via_gps_crossing(rows)

    accepted: list = []
    for l in laps:
        if _LAP_MIN_S <= l["lap_time_s"] <= _LAP_MAX_S:
            accepted.append({**l, "lap_number": len(accepted) + 1})
    return accepted


def _laps_via_cumulative_distance(rows: list, track_len: float) -> list:
    """Lap boundary = `floor(distance_m / track_len)` increments.

    Real Racelogic VBOs report distance as a monotonically increasing
    cumulative sum from session start. Each time this crosses a new
    multiple of track_length, the car has completed one full lap.

    Discards the pre-first-boundary segment as the out-lap.
    """
    laps: list = []
    start_idx = None
    for i in range(1, len(rows)):
        prev_d = rows[i - 1][1]
        curr_d = rows[i][1]
        if prev_d is None or curr_d is None:
            continue
        if int(curr_d // track_len) > int(prev_d // track_len):
            if start_idx is not None:
                t_start = rows[start_idx][0]
                laps.append({
                    "lap_number":  0,
                    "t_start":     t_start,
                    "t_end":       rows[i][0],
                    "lap_time_s":  rows[i][0] - t_start,
                    "frame_start": start_idx,
                    "frame_end":   i,
                })
            start_idx = i
    return laps


def _laps_via_distance_wrap(rows: list) -> list:
    """Lap = run between two distance_m wraparound points (drop > L/2)."""
    track_len = float(getattr(sonoma, "TRACK_LENGTH_M", 4258))
    threshold = track_len / 2
    laps: list = []
    start_idx = 0
    for i in range(1, len(rows)):
        prev_d = rows[i - 1][1] or 0.0
        curr_d = rows[i][1] or 0.0
        if (prev_d - curr_d) > threshold:
            t_start = rows[start_idx][0]
            t_end   = rows[i - 1][0]
            laps.append({
                "lap_number":  0,
                "t_start":     t_start,
                "t_end":       t_end,
                "lap_time_s":  t_end - t_start,
                "frame_start": start_idx,
                "frame_end":   i - 1,
            })
            start_idx = i
    return laps


def _laps_via_gps_crossing(rows: list) -> list:
    """Negative→positive sign-change of perpendicular distance to S/F line."""
    import math

    track_path = os.path.abspath(os.path.join(
        SIM_DIR, "..", "..", "data", "tracks", "sonoma.json",
    ))
    sf_lat = sonoma.SF_LAT
    sf_lon = sonoma.SF_LON
    sf_hdg = sonoma.SF_HEADING_DEG
    try:
        with open(track_path) as fh:
            tdata = json.load(fh)
        sf = tdata.get("start_finish") or {}
        sf_lat = float(sf.get("lat", sf_lat))
        sf_lon = float(sf.get("lon", sf_lon))
        sf_hdg = float(sf.get("heading", sf_hdg))
    except Exception:
        pass

    R = 111320.0
    cos_lat = math.cos(math.radians(sf_lat))
    theta = math.radians(sf_hdg)
    sin_t, cos_t = math.sin(theta), math.cos(theta)

    # Radial tolerance: at 200 km/h the car covers ~5.5 m per 10 Hz frame, and
    # the racing line through S/F differs lap-to-lap. 50 m gates out
    # crossings of the *infinite* perpendicular line that happen far from
    # the physical S/F marker (e.g. the back-straight is roughly parallel
    # but ~600 m from S/F, well outside this radius).
    RADIAL_TOL_M = 50.0

    # Only count complete crossing-to-crossing intervals. The pre-first-
    # crossing segment is the out-lap (warmup from pit) and the post-last-
    # crossing segment is the in-lap (cool-down to pit) — both are *not*
    # complete timed laps and must be discarded.
    laps: list = []
    start_idx = None      # None until first crossing
    prev_signed = None
    for i, (t, _d, lat, lon) in enumerate(rows):
        if lat is None or lon is None:
            continue
        x = (lon - sf_lon) * cos_lat * R
        y = (lat - sf_lat) * R
        signed = -x * sin_t + y * cos_t
        radial = math.hypot(x, y)
        if prev_signed is not None and prev_signed < 0 <= signed and radial < RADIAL_TOL_M:
            if start_idx is not None:
                t_start = rows[start_idx][0]
                laps.append({
                    "lap_number":  0,
                    "t_start":     t_start,
                    "t_end":       t,
                    "lap_time_s":  t - t_start,
                    "frame_start": start_idx,
                    "frame_end":   i,
                })
            start_idx = i
        prev_signed = signed
    return laps


def _lap_sectors(sid: str, lap: dict) -> list:
    """Slice one lap into sonoma.SECTORS sub-spans by distance threshold."""
    if not HAS_DUCKDB:
        return []
    with _db_lock:
        conn = get_db()
        if conn is None:
            return []
        rows = conn.execute(
            "SELECT timestamp, distance_m FROM telemetry "
            "WHERE session_id = ? AND timestamp >= ? AND timestamp <= ? "
            "ORDER BY timestamp",
            [sid, lap["t_start"], lap["t_end"]],
        ).fetchall()
        conn.close()
    if not rows:
        return []

    base_d = rows[0][1] or 0.0
    track_len = float(getattr(sonoma, "TRACK_LENGTH_M", 4258))

    def lap_progress(d):
        if d is None:
            return None
        delta = d - base_d
        if delta < -track_len / 2:
            delta += track_len
        return delta

    out: list = []
    for sec in sonoma.SECTORS:
        t_enter = None
        t_exit = None
        for t, d in rows:
            p = lap_progress(d)
            if p is None:
                continue
            if t_enter is None and p >= sec.start_m:
                t_enter = t
            if t_exit is None and p >= sec.end_m:
                t_exit = t
                break
        if t_enter is None:
            continue
        if t_exit is None:
            t_exit = rows[-1][0]
        out.append({
            "name":    sec.name,
            "start_m": sec.start_m,
            "end_m":   sec.end_m,
            "t_enter": t_enter,
            "t_exit":  t_exit,
            "time_s":  t_exit - t_enter,
        })
    return out


def _quantile(sorted_vals: list, p: float) -> float:
    """Tukey linear-interp quantile per docs/api.md spec."""
    if not sorted_vals:
        return 0.0
    n = len(sorted_vals)
    if n == 1:
        return float(sorted_vals[0])
    h = p * (n - 1) + 1.0
    lo = max(int(h) - 1, 0)
    hi = min(lo + 1, n - 1)
    frac = h - int(h)
    return float(sorted_vals[lo]) + frac * (float(sorted_vals[hi]) - float(sorted_vals[lo]))


def _load_track_json(track_id: str) -> dict | None:
    """Load data/tracks/<id>.json or return None."""
    path = os.path.abspath(os.path.join(
        SIM_DIR, "..", "..", "data", "tracks", f"{track_id}.json",
    ))
    if not os.path.exists(path):
        return None
    try:
        with open(path) as fh:
            return json.load(fh)
    except Exception:
        return None


def _corner_bounds_from_track(track: dict) -> list:
    """Return [{name, entry_m, apex_m, exit_m}] for each corner in track JSON."""
    out: list = []
    for c in (track or {}).get("corners", []):
        try:
            out.append({
                "name":    c["name"],
                "entry_m": float(c["entry"]["distance"]),
                "apex_m":  float(c["apex"]["distance"]),
                "exit_m":  float(c["exit"]["distance"]),
            })
        except (KeyError, TypeError, ValueError):
            continue
    return out


# ── Frame helpers — DuckDB rows ↔ TelemetryFrame ─────────────────────────────

def _frames_to_rows(session_id: str, frames) -> list:
    """Map a list of TelemetryFrame objects to DuckDB row tuples."""
    return [
        (
            session_id, i, f.timestamp,
            getattr(f, "distance", 0.0),
            f.speed, f.g_lat, f.g_long, f.combo_g,
            f.brake_pressure, f.throttle, f.steering, f.rpm,
            getattr(f, "lat", 0.0), getattr(f, "lon", 0.0),
        )
        for i, f in enumerate(frames)
    ]


def _rows_to_frames(rows):
    """Reconstruct frame-shaped objects (SimpleNamespace) from DuckDB rows."""
    from types import SimpleNamespace
    out = []
    for r in rows:
        (sid, idx, ts, dist, spd, gl, gL, cg,
         brk, thr, st, rpm, lat, lon) = r
        out.append(SimpleNamespace(
            timestamp=ts, distance=dist, speed=spd,
            g_lat=gl, g_long=gL, combo_g=cg,
            brake_pressure=brk, throttle=thr, steering=st, rpm=rpm,
            lat=lat, lon=lon,
        ))
    return out


# ── Coaching engine ────────────────────────────────────────────────────────────

def _cues_to_coaching(cues: list) -> str:
    """Convert AudioCue list from sonic_model into a single coaching sentence."""
    if not cues:
        return "Smooth sector. Maintain pace."

    # Sort by priority descending — highest priority cue drives the message
    top = sorted(cues, key=lambda c: c.priority, reverse=True)[0]
    reason = top.reason

    # Map patterns + layers to brief coaching language
    pat = top.pattern.value if hasattr(top.pattern, "value") else str(top.pattern)
    layer = top.layer

    if pat == "buzz":
        return f"OVER GRIP LIMIT — ease off. {reason}"
    if layer == "grip" and top.priority >= 2:
        return f"At grip limit. {reason}"
    if layer == "brake_approach":
        return f"Brake marker. {reason}"
    if layer == "trail_brake":
        return f"Trail brake — hold pressure. {reason}"
    if layer == "throttle":
        return f"Throttle now. {reason}"
    if layer == "lap_estimate":
        return reason

    return reason[:120] if reason else "Stay focused."


def _sonic_coaching(burst: dict) -> tuple[str, list]:
    """
    Run the real sonic_model over the burst's representative frame data.
    Returns (coaching_text, serialised_cues).
    """
    if not HAS_SONIC:
        return None, []

    # Reconstruct a representative frame at the peak-stress snapshot.
    # Use MAX values for safety-critical channels (G, brake) so the
    # coaching engine fires on the worst moment within the burst.
    frame = {
        "speed":             burst.get("avg_speed_kmh", 0) / 3.6,   # → m/s
        "g_lat":             burst.get("max_lateral_g", burst.get("max_combo_g", 0) * 0.7),
        "g_long":            burst.get("max_long_g",    burst.get("max_combo_g", 0) * 0.7),
        "combo_g":           burst.get("max_combo_g", 0),           # peak, not avg
        "brake_pressure":    burst.get("max_brake_bar", 0),         # peak
        "throttle":          burst.get("avg_throttle_pct", 0),
        "steering":          burst.get("avg_steering_deg", 0),
        "distance_to_corner": burst.get("dist_to_next_corner_m", 50),  # assume near corner
        "corner_severity":   burst.get("next_corner_severity", 3),
        "past_apex":         burst.get("past_apex", False),
        "in_corner":         burst.get("in_corner", len(burst.get("corners_visited", [])) > 0),
    }

    # Also compute with track context if available
    corners = burst.get("corners_visited", [])
    if _track and corners:
        nearest = find_nearest_corner(_track, burst.get("distance_m", 0))
        if nearest:
            frame["distance_to_corner"] = distance_to_corner(
                _track, burst.get("distance_m", 0), nearest
            )
            frame["corner_severity"] = nearest.severity

    cues = compute_cues(frame, prev_frame=None)
    coaching = _cues_to_coaching(cues)

    # Serialise cues for the response payload
    serialised = [
        {
            "layer":     c.layer,
            "frequency": c.frequency,
            "volume":    c.volume,
            "pattern":   c.pattern.value if hasattr(c.pattern, "value") else str(c.pattern),
            "priority":  c.priority,
            "reason":    c.reason,
        }
        for c in cues
    ]
    return coaching, serialised


def _rule_coaching(burst: dict) -> str:
    """Fallback rule engine — used when sonic_model is unavailable."""
    avg_speed    = burst.get("avg_speed_kmh", 0)
    max_g        = burst.get("max_combo_g", 0)
    coast_frames = burst.get("coast_frames", 0)
    trail_frames = burst.get("trail_brake_frames", 0)
    frame_count  = burst.get("frame_count", 1)
    corners      = burst.get("corners_visited", [])

    coast_pct = coast_frames / max(frame_count, 1) * 100

    if max_g > 2.1:
        return "Back off — at grip limit. Smooth inputs only."
    if coast_pct > 25:
        corner = corners[0] if corners else "next corner"
        return f"Coasting {coast_pct:.0f}% of sector. Get on throttle earlier through {corner}."
    if trail_frames > 0 and max_g > 1.5:
        return "Good trail braking. Carry that commitment to the next corner."
    if avg_speed < 60:
        return "Below 60 km/h avg — check braking points, you may be braking too early."
    if avg_speed > 120:
        return "Strong sector pace. Stay smooth on the wheel."
    return f"Sector avg {avg_speed:.0f} km/h, max {max_g:.2f}G. Focus on exit speed — throttle at apex."


# ── Routes ─────────────────────────────────────────────────────────────────────

@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status":    "ok",
        "version":   "2.0",
        "engine":    "sonic_model" if HAS_SONIC else "rules",
        "track":     _track.name if _track else None,
        "duckdb":    HAS_DUCKDB,
        "timestamp": datetime.utcnow().isoformat(),
    })


@app.route("/analyze", methods=["POST"])
def analyze():
    """
    Receive a telemetry burst from AntigravityPipeline.kt and return coaching.

    Expected JSON (from serialiseBurst in AntigravityPipeline):
    {
      "session_id": "...",  "burst_id": 7,
      "avg_speed_kmh": 104, "max_combo_g": 1.82,
      "max_brake_bar": 45,  "avg_throttle_pct": 38,
      "coast_frames": 12,   "trail_brake_frames": 4,
      "frame_count": 75,    "corners_visited": ["Turn 3"],
      "distance_m": 1450,   "in_corner": false
    }
    """
    burst = request.get_json(force=True, silent=True) or {}
    burst_id = burst.get("burst_id", 0)

    # Accumulate burst for /insights scoring
    with _burst_lock:
        _session_bursts.append(burst)

    # Tier 1: Full sonic_model pipeline (audio cues)
    coaching, cues = _sonic_coaching(burst)

    # Tier 2: Rule fallback
    if not coaching:
        coaching = _rule_coaching(burst)
        source = "bridge_rules"
    else:
        source = "sonic_model"

    # Tier 3: Insightful Coach Engine (Pace Notes)
    pace_note = None
    coach_source = None
    
    if _coach and _track:
        import types
        
        frame = types.SimpleNamespace(
            speed=burst.get("avg_speed_kmh", 0) / 3.6,
            brake_pressure=burst.get("max_brake_bar", 0),
            throttle=burst.get("avg_throttle_pct", 0),
            g_lat=burst.get("max_lateral_g", burst.get("max_combo_g", 0) * 0.7),
            g_long=burst.get("max_long_g", burst.get("max_combo_g", 0) * 0.7)
        )
        
        distance_m = burst.get("distance_m", 0)
        nearest = find_nearest_corner(_track, distance_m)
        dist_to_corner = distance_to_corner(_track, distance_m, nearest) if nearest else 999.0
        in_corner_obj = nearest if burst.get("in_corner", False) else None
        
        ctx = build_context(
            driver_level=burst.get("driver_level", "intermediate"),
            track=_track,
            frame=frame,
            next_corner=nearest,
            meters_to_entry=dist_to_corner,
            in_corner_obj=in_corner_obj,
            past_apex=burst.get("past_apex", False),
        )
        
        msg = _coach.propose(ctx)
        msg = _arbiter.submit(msg, now=time.time(), on_straight=abs(frame.g_lat) < 0.3)
        if msg:
            pace_note = msg.text
            coach_source = _coach.name
            # Override generic sonic_model coaching with the more insightful pace note
            coaching = pace_note
            source = coach_source

    # Publish to /cues/stream subscribers — every analyze() call fans out
    # one cue event per session_id (if there's an active subscription).
    sid = burst.get("session_id")
    if sid:
        _cue_bus.publish(sid, {
            "ts":              time.time(),
            "burst_id":        burst_id,
            "phrase_id":       None,
            "text":            coaching or "",
            "priority":        1,
            "emotion":         "neutral",   # /analyze path doesn't carry emotion yet
            "source":          source,
            "pace_note":       pace_note,
            # ADR-018 audio-ducker hint: PWA holds tactical-tone gain at
            # -18 dB for this many ms while it speaks the cue. ~150 ms/word
            # matches the canonical T-Rod TTS render rate; floor at 800 ms
            # so a one-word safety call ('Brake!') still ducks long enough
            # to land cleanly over the continuous tones.
            "expected_tts_ms": _estimate_tts_ms(coaching or ""),
        })

    return jsonify({
        "coaching":     coaching,
        "cues":         cues,
        "burst_id":     burst_id,
        "source":       source,
        "pace_note":    pace_note,
        "coach_source": coach_source,
    })


# ── /cues/stream SSE — live cue fan-out ────────────────────────────────────

import queue as _queue


class _CueBus:
    """In-memory pub/sub of coaching cues, keyed by session_id.

    Each SSE subscriber (one HTTP connection from the PWA's on-track HUD)
    gets its own bounded queue. `publish(sid, event)` pushes to every
    subscriber for that session. Lost queues (subscriber disconnected,
    queue full) are cleaned up lazily on the next publish.
    """

    def __init__(self):
        self._lock = threading.Lock()
        self._subs: dict[str, list[_queue.Queue]] = {}

    def subscribe(self, sid: str, maxsize: int = 32) -> _queue.Queue:
        q: _queue.Queue = _queue.Queue(maxsize=maxsize)
        with self._lock:
            self._subs.setdefault(sid, []).append(q)
        return q

    def unsubscribe(self, sid: str, q: _queue.Queue):
        with self._lock:
            if sid in self._subs:
                try:
                    self._subs[sid].remove(q)
                except ValueError:
                    pass
                if not self._subs[sid]:
                    del self._subs[sid]

    def publish(self, sid: str, event: dict):
        dead: list[_queue.Queue] = []
        with self._lock:
            queues = list(self._subs.get(sid, []))
        for q in queues:
            try:
                q.put_nowait(event)
            except _queue.Full:
                dead.append(q)
        for q in dead:
            self.unsubscribe(sid, q)


_cue_bus = _CueBus()


def _estimate_tts_ms(text: str) -> int:
    """Rough TTS duration estimate for the audio-ducker hint on cue events.

    150 ms/word matches Gemini-TTS's canonical T-Rod render rate; floored at
    800 ms so a one-word safety call ('Brake!') still ducks long enough for
    a clean handover over the continuous tactical tones.
    """
    if not text:
        return 0
    words = max(1, len(text.split()))
    return max(800, words * 150)


@app.route("/cues/stream", methods=["GET"])
def cues_stream():
    """Server-Sent Events stream of coaching cues for a session.

    Query params:
        session_id   required — only events for this session are streamed

    Each event is JSON:
        {ts, burst_id, phrase_id, text, priority, emotion, source}

    The PWA's on-track HUD subscribes once at session start, reads cues
    until the connection closes (Pause menu's QUIT, page hide, network
    drop). Auto-reconnect logic is the client's responsibility.
    """
    sid = request.args.get("session_id")
    if not sid:
        return jsonify({"error": "session_id query param required"}), 400

    q = _cue_bus.subscribe(sid)

    def gen():
        try:
            # Send a hello event so the client knows the connection is live
            yield "event: hello\n"
            yield f"data: {json.dumps({'session_id': sid})}\n\n"
            while True:
                try:
                    event = q.get(timeout=15.0)
                except _queue.Empty:
                    # Heartbeat — keeps proxies + browsers from idle-closing
                    yield ": keepalive\n\n"
                    continue
                yield "event: cue\n"
                yield f"data: {json.dumps(event)}\n\n"
        finally:
            _cue_bus.unsubscribe(sid, q)

    return Response(
        stream_with_context(gen()),
        mimetype="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",       # disable nginx buffering if proxied
        },
    )


# ── /notifications SSE — async event inbox for the PWA ────────────────────


_notif_bus = _CueBus()   # same pub/sub primitive, different keyspace


def emit_notification(driver: Optional[str], kind: str, **fields):
    """Publish an async event for the PWA's notification center.

    Used by debrief-ready, medal-earned, level-up, hardware-warning,
    evolution-ready callsites elsewhere in the bridge. Fan-out is per-driver
    (or global when `driver` is None).
    """
    event = {
        "ts":     time.time(),
        "kind":   kind,                 # see screens/33-notification-center.md
        "driver": driver,
        **fields,
    }
    # Subscribers can listen on a specific driver name OR on '*' for all.
    _notif_bus.publish(driver or "*", event)
    _notif_bus.publish("*", event)


@app.route("/notifications", methods=["GET"])
def notifications_stream():
    """SSE stream of async events for the notification center.

    Query params:
        driver   filter to events for this driver only ('*' or omitted = all)

    Each event is JSON: {ts, kind, driver, ...kind-specific fields}.
    Kinds match `screens/33-notification-center.md`:
      debrief-ready · medal-earned · level-up · affinity-tier ·
      track-unlock · hardware-warning · evolution-ready · session-saved
    """
    driver = request.args.get("driver") or "*"
    q = _notif_bus.subscribe(driver)

    def gen():
        try:
            yield "event: hello\n"
            yield f"data: {json.dumps({'driver': driver})}\n\n"
            while True:
                try:
                    event = q.get(timeout=30.0)
                except _queue.Empty:
                    yield ": keepalive\n\n"
                    continue
                yield "event: notification\n"
                yield f"data: {json.dumps(event)}\n\n"
        finally:
            _notif_bus.unsubscribe(driver, q)

    return Response(
        stream_with_context(gen()),
        mimetype="text/event-stream",
        headers={
            "Cache-Control":     "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


# ── /spectator/token — read-only mirror access ─────────────────────────────


import secrets as _secrets


_spectator_tokens: dict[str, dict] = {}    # token → { sid, expires_at }
_SPECTATOR_TTL_S = 4 * 3600                # 4 hours


def _purge_expired_tokens():
    now = time.time()
    expired = [t for t, info in _spectator_tokens.items() if info["expires_at"] < now]
    for t in expired:
        _spectator_tokens.pop(t, None)


@app.route("/spectator/token", methods=["POST"])
def spectator_token_create():
    """Generate a one-time, time-limited token granting read-only access
    to a session's live cue stream. Used by the PWA's Live Spectator
    screen to share a viewing link with a passenger or external display.

    Body: { session_id: str } — the session this token grants access to
    Response: { token, session_id, expires_at, url } — `url` is the
    suggested deep-link the PWA renders as a QR code.
    """
    body = request.get_json(force=True, silent=True) or {}
    sid = body.get("session_id")
    if not sid:
        return jsonify({"error": "session_id required"}), 400

    _purge_expired_tokens()
    token = _secrets.token_urlsafe(24)
    expires_at = time.time() + _SPECTATOR_TTL_S
    _spectator_tokens[token] = {"session_id": sid, "expires_at": expires_at}
    return jsonify({
        "token":      token,
        "session_id": sid,
        "expires_at": expires_at,
        "ttl_s":      _SPECTATOR_TTL_S,
        "url":        f"/spectator/{sid}?token={token}",
    })


def _validate_spectator_token(token: str) -> Optional[str]:
    """Returns the session_id the token grants access to, or None if
    invalid / expired. Caller should 401 on None."""
    _purge_expired_tokens()
    info = _spectator_tokens.get(token)
    if info is None:
        return None
    if info["expires_at"] < time.time():
        _spectator_tokens.pop(token, None)
        return None
    return info["session_id"]


@app.route("/spectator/token/<token>", methods=["DELETE"])
def spectator_token_revoke(token: str):
    """Driver explicitly revokes a spectator token (e.g., session ended)."""
    existed = _spectator_tokens.pop(token, None) is not None
    return jsonify({"revoked": existed})


# ── Insights engine ───────────────────────────────────────────────────────────

def _score_insights(bursts: list) -> list:
    """
    Score 4 insight dimensions across accumulated session bursts.
    Returns up to 3 insights sorted by effort ASC, est_gain DESC.
    """
    if not bursts:
        return []

    # --- Dimension accumulators ---
    total_frames   = sum(b.get("frame_count", 1) for b in bursts)
    coast_frames   = sum(b.get("coast_frames", 0) for b in bursts)
    trail_frames   = sum(b.get("trail_brake_frames", 0) for b in bursts)
    corner_bursts  = [b for b in bursts if b.get("corners_visited")]
    all_g          = [b.get("max_combo_g", 0) for b in bursts]
    avg_g          = sum(all_g) / len(all_g) if all_g else 0
    avg_speed      = sum(b.get("avg_speed_kmh", 0) for b in bursts) / len(bursts)

    coast_pct      = (coast_frames / max(total_frames, 1)) * 100
    grip_headroom  = 2.29 - avg_g   # Gs below the tyre limit

    # Collect corner names with issues
    coast_corners  = []
    grip_corners   = []
    for b in corner_bursts:
        if (b.get("coast_frames", 0) / max(b.get("frame_count", 1), 1)) > 0.20:
            coast_corners.extend(b.get("corners_visited", []))
        if b.get("max_combo_g", 0) < 1.5:
            grip_corners.extend(b.get("corners_visited", []))
    coast_corners = list(dict.fromkeys(coast_corners))[:4]  # dedupe, max 4
    grip_corners  = list(dict.fromkeys(grip_corners))[:4]

    insights = []

    # 1. Coast excess — easiest gain, just lift the foot earlier
    if coast_pct > 15:
        est = round(min(coast_pct * 0.03, 1.5), 1)
        insights.append({
            "id":             "coast_excess",
            "title":          "Early Throttle Pickup",
            "detail":         f"You're coasting {coast_pct:.0f}% of corners. "
                              f"Get to full throttle at the apex instead of mid-exit. "
                              f"Every tenth of a second off throttle is lost time.",
            "corners":        coast_corners,
            "metric_label":   "Coast",
            "metric_value":   f"{coast_pct:.0f}%",
            "effort":         1,
            "est_gain_s":     est,
            "evidence_bursts": len([b for b in bursts if b.get("coast_frames", 0) > 0]),
        })

    # 2. Grip headroom — easy, just carry more speed
    if avg_g < 1.6 and len(grip_corners) >= 2:
        est = round(min(grip_headroom * 0.4, 1.0), 1)
        insights.append({
            "id":             "grip_headroom",
            "title":          "Unused Grip Budget",
            "detail":         f"Peak G averaging {avg_g:.2f}G — tyres support 2.29G. "
                              f"You have {grip_headroom:.2f}G of headroom. "
                              f"Carry more entry speed through the corners listed.",
            "corners":        grip_corners,
            "metric_label":   "Peak G",
            "metric_value":   f"{avg_g:.2f}G",
            "effort":         1,
            "est_gain_s":     est,
            "evidence_bursts": len(grip_corners),
        })

    # 3. Trail braking absent — moderate effort, technique change
    in_corner_bursts = [b for b in corner_bursts if b.get("in_corner")]
    if in_corner_bursts and trail_frames == 0:
        trail_corners = list(dict.fromkeys(
            c for b in in_corner_bursts for c in b.get("corners_visited", [])
        ))[:4]
        insights.append({
            "id":             "trail_absent",
            "title":          "Add Trail Braking",
            "detail":         "No trail braking detected. Holding light brake pressure "
                              "through corner entry adds rotation, lets you brake later, "
                              "and improves mid-corner balance.",
            "corners":        trail_corners,
            "metric_label":   "Trail frames",
            "metric_value":   "0",
            "effort":         2,
            "est_gain_s":     0.4,
            "evidence_bursts": len(in_corner_bursts),
        })

    # 4. Braking late / low entry speed
    slow_entry_corners = []
    for b in corner_bursts:
        if b.get("avg_speed_kmh", 999) < 70 and b.get("in_corner"):
            slow_entry_corners.extend(b.get("corners_visited", []))
    slow_entry_corners = list(dict.fromkeys(slow_entry_corners))[:4]
    if slow_entry_corners:
        insights.append({
            "id":             "braking_late",
            "title":          "Brake Point Optimisation",
            "detail":         f"Corner entry averaging {avg_speed:.0f} km/h at the corners "
                              f"listed. Try moving your brake marker 15–20m later — you may "
                              f"be over-braking and scrubbing speed unnecessarily.",
            "corners":        slow_entry_corners,
            "metric_label":   "Avg entry",
            "metric_value":   f"{avg_speed:.0f} km/h",
            "effort":         2,
            "est_gain_s":     0.5,
            "evidence_bursts": len(slow_entry_corners),
        })

    # Sort: effort ASC, then est_gain DESC, pick top 3
    insights.sort(key=lambda x: (x["effort"], -x["est_gain_s"]))
    for i, ins in enumerate(insights[:3], 1):
        ins["rank"] = i
    return insights[:3]


@app.route("/insights", methods=["GET"])
def get_insights():
    """Return top-3 prioritised driver insights from the current session bursts.

    Mid-session (in-drive) endpoint — fires every burst, must be sub-100ms.
    Per the three-tier coach architecture (ADR-016 follow-up), in-drive
    coaching MUST NOT call any LLM. We use the deterministic
    `_score_insights` scorer here and let the LLM handle paddock-time
    pre-brief / debrief.
    """
    lap_param = request.args.get("lap")
    lap = int(lap_param) if lap_param else None

    with _burst_lock:
        bursts_snapshot = list(_session_bursts)

    if lap is not None:
        bursts_snapshot = [b for b in bursts_snapshot if b.get("lap") == lap]

    insights = _score_insights(bursts_snapshot)
    return jsonify({
        "insights":       insights,
        "session_bursts": len(bursts_snapshot),
        "generated_at":   datetime.utcnow().isoformat(),
    })


@app.route("/session/reset", methods=["POST"])
def session_reset():
    """Clear the burst accumulator — call this when a new session starts."""
    with _burst_lock:
        count = len(_session_bursts)
        _session_bursts.clear()
    return jsonify({"cleared_bursts": count, "status": "ok"})


def _new_session_id(track_name: str | None = None) -> str:
    """Stable session id derived from the track + UTC stamp.

    Used by /coach/debrief and /session/import when the caller doesn't
    supply their own session_id. Lives at module scope so unit tests can
    import it directly (test_bridge.test_new_session_id_format).
    """
    slug = (track_name or "session").lower().replace(" ", "-")
    stamp = datetime.utcnow().strftime("%Y%m%d-%H%M%S")
    return f"{slug}-{stamp}"


# ── Per-frame telemetry ingestion ─────────────────────────────────────────────

@app.route("/session/<sid>/frames", methods=["POST"])
def session_frames(sid: str):
    """Append a batch of telemetry frames for a session.

    Body shape:
        {"frames": [{"timestamp": ..., "distance": ..., "speed": ...,
                     "g_lat": ..., "g_long": ..., "combo_g": ...,
                     "brake_pressure": ..., "throttle": ..., "steering": ...,
                     "rpm": ..., "lat": ..., "lon": ...}, ...]}
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    data = request.get_json(force=True, silent=True) or {}
    raw_frames = data.get("frames") or []
    if not raw_frames:
        return jsonify({"saved": False, "error": "no frames"}), 400

    _ensure_session_row(
        sid,
        driver=data.get("driver"),
        driver_level=data.get("driver_level"),
        track=data.get("track"),
        car=data.get("car"),
        note=data.get("note"),
    )

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"saved": False, "error": "duckdb not available"}), 503
        # Determine the next frame_idx for this session
        existing = conn.execute(
            "SELECT COALESCE(MAX(frame_idx), -1) FROM telemetry WHERE session_id = ?",
            [sid],
        ).fetchone()[0]
        next_idx = (existing if existing is not None else -1) + 1

        rows = []
        for j, f in enumerate(raw_frames):
            rows.append((
                sid, next_idx + j,
                float(f.get("timestamp", 0)),
                float(f.get("distance", 0)),
                float(f.get("speed", 0)),
                float(f.get("g_lat", 0)),
                float(f.get("g_long", 0)),
                float(f.get("combo_g", 0)),
                float(f.get("brake_pressure", 0)),
                float(f.get("throttle", 0)),
                float(f.get("steering", 0)),
                float(f.get("rpm", 0)),
                float(f.get("lat", 0)),
                float(f.get("lon", 0)),
            ))
        conn.executemany(
            "INSERT INTO telemetry VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            rows,
        )
        conn.close()
    return jsonify({"saved": True, "session_id": sid, "n_appended": len(raw_frames)})


# ── Video frame metadata ingestion ───────────────────────────────────────────

@app.route("/session/<sid>/video_frames", methods=["POST"])
def session_video_frames(sid: str):
    """Append video-frame metadata for a session. Body:
        {"frames": [{"timestamp": ..., "avitime_ms": ...,
                     "file_path": "...", "file_offset_s": ...,
                     "width": ..., "height": ...}, ...]}

    Video bytes stay on disk; this endpoint only records the index. Callers
    use this in tandem with /session/<id>/frames to enable
    timestamp-aligned replay + ffmpeg-seek into the chunked MP4.
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    data = request.get_json(force=True, silent=True) or {}
    raws = data.get("frames") or []
    if not raws:
        return jsonify({"saved": False, "error": "no frames"}), 400
    rows = [
        (
            sid,
            float(f.get("timestamp", 0)),
            int(f.get("avitime_ms", 0)),
            str(f.get("file_path", "")),
            float(f.get("file_offset_s", 0)),
            int(f.get("width", 0)),
            int(f.get("height", 0)),
        )
        for f in raws
    ]
    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"saved": False, "error": "duckdb not available"}), 503
        conn.executemany(
            "INSERT INTO video_frames VALUES (?,?,?,?,?,?,?)",
            rows,
        )
        conn.close()
    return jsonify({"saved": True, "session_id": sid, "n_appended": len(raws)})


@app.route("/session/<sid>/sync", methods=["GET"])
def session_sync(sid: str):
    """Return time-aligned (telemetry + video) rows for a session window.

    Query params:
        from   (epoch seconds, optional)
        to     (epoch seconds, optional)
        window_s (default 0.05) — match telemetry to video within ± this
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    t_from = float(request.args.get("from", 0))
    t_to = float(request.args.get("to", 0))
    win = float(request.args.get("window_s", 0.05))
    where_t = ""
    params = [sid, win]
    if t_to > 0:
        where_t = "AND t.timestamp BETWEEN ? AND ?"
        params.extend([t_from, t_to])
    sql = (
        "SELECT t.frame_idx, t.timestamp, t.distance_m, t.speed_ms, "
        "       t.brake_bar, t.throttle_pct, t.g_lat, t.g_long, "
        "       v.file_path, v.file_offset_s "
        "FROM telemetry t "
        "LEFT JOIN video_frames v "
        "  ON v.session_id = t.session_id "
        "  AND v.timestamp BETWEEN t.timestamp - ? AND t.timestamp + ? "
        f"WHERE t.session_id = ? {where_t} "
        "ORDER BY t.frame_idx"
    )
    params = [win, win, sid] + ([t_from, t_to] if t_to > 0 else [])
    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        rows = conn.execute(sql, params).fetchall()
        cols = [d[0] for d in conn.description]
        conn.close()
    return jsonify({
        "session_id": sid,
        "rows": [dict(zip(cols, r)) for r in rows],
        "count": len(rows),
    })


@app.route("/session/<sid>/export.parquet", methods=["GET"])
def session_export_parquet(sid: str):
    """Stream a session's data as Parquet for DuckDB-Wasm hydration.

    Query params:
        table   'telemetry' (wide canonicals) | 'telemetry_signals'
                (ADR-015 tall sink) | 'capabilities' (per-signal Hz +
                useful flag). Default: 'telemetry'.

    Powers the PWA's analytics flow: PWA fetches the parquet once per
    session, registers it with DuckDB-Wasm, then runs all subsequent
    SQL client-side. No per-query bridge round-trip needed.

    Status codes:
      200  parquet bytes streamed back; Content-Type: application/octet-stream
      400  bad table name
      404  session not found in the requested table
      503  duckdb unavailable
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    table = (request.args.get("table") or "telemetry").lower()
    if table not in ("telemetry", "telemetry_signals", "capabilities"):
        return jsonify({"error": f"unknown table: {table}"}), 400

    import tempfile
    tmp = tempfile.NamedTemporaryFile(suffix=".parquet", delete=False)
    tmp_path = tmp.name
    tmp.close()

    try:
        with _db_lock:
            conn = get_db()
            if conn is None:
                return jsonify({"error": "duckdb not available"}), 503
            try:
                if table == "capabilities":
                    select_sql = (
                        "SELECT sc.session_id, sr.name AS signal_name, "
                        "       sc.n_samples, sc.mean_hz, sc.t_start, sc.t_end, "
                        "       sr.units, sr.\"group\", sr.min_useful_hz "
                        "FROM session_capabilities sc "
                        "JOIN signal_registry sr USING(signal_id) "
                        "WHERE sc.session_id = ?"
                    )
                else:
                    select_sql = f"SELECT * FROM {table} WHERE session_id = ?"
                # Pre-check there's anything to export — else return 404 cleanly
                n = conn.execute(
                    f"SELECT COUNT(*) FROM ({select_sql})",
                    [sid],
                ).fetchone()[0]
                if not n:
                    return jsonify({
                        "error": "session not found in this table",
                        "session_id": sid, "table": table,
                    }), 404
                # DuckDB's COPY TO writes parquet directly (no pandas needed)
                conn.execute(
                    f"COPY ({select_sql}) TO '{tmp_path}' (FORMAT PARQUET)",
                    [sid],
                )
            finally:
                conn.close()

        with open(tmp_path, "rb") as f:
            data = f.read()
        return Response(
            data,
            mimetype="application/octet-stream",
            headers={
                "Content-Disposition": f'attachment; filename="{sid}-{table}.parquet"',
                "Cache-Control": "no-cache",
                "X-Pitwall-Session": sid,
                "X-Pitwall-Table": table,
                "X-Pitwall-Rows": str(n),
            },
        )
    finally:
        try:
            os.remove(tmp_path)
        except OSError:
            pass


@app.route("/session/import", methods=["POST"])
def session_import():
    """Import an entire VBO file into a new session in DuckDB.

    Body: {"vbo_path": "/abs/path/to/file.vbo",
           "driver": "...",
           "driver_level": "intermediate",
           "session_id": (optional, auto-generated if omitted)}

    Parses the VBO, creates a `sessions` row, persists every frame to the
    `telemetry` table. Returns {session_id, n_frames, duration_s, distance_m}.
    Idempotent on session_id: if the session already has frames, returns 409.
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    data = request.get_json(force=True, silent=True) or {}
    vbo = data.get("vbo_path")
    if not vbo or not os.path.exists(vbo):
        return jsonify({"error": f"vbo_path missing or not found: {vbo!r}"}), 400

    sid = data.get("session_id") or _new_session_id(_track.name if _track else None)
    driver = data.get("driver", "")
    level = data.get("driver_level", "intermediate")
    note = data.get("note", f"Imported from {os.path.basename(vbo)}")

    try:
        from vbo_parser import parse_vbo
        meta, frames = parse_vbo(vbo)
        if not frames:
            return jsonify({"error": f"no frames parsed from {vbo}"}), 400
    except Exception as e:
        return jsonify({"error": f"parse_vbo failed: {e}"}), 500

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        # Reject if this session already has frames
        existing = conn.execute(
            "SELECT count(*) FROM telemetry WHERE session_id = ?",
            [sid],
        ).fetchone()[0]
        if existing > 0:
            conn.close()
            return jsonify({
                "error": f"session {sid} already has {existing} frames",
                "session_id": sid,
            }), 409

        # Create the session row (UPSERT-style)
        conn.execute(
            "INSERT INTO sessions (session_id, driver, driver_level, track, car, note) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            [sid, driver, level,
             _track.name if _track else "Sonoma Raceway",
             meta.device_type or "", note],
        )
        rows = _frames_to_rows(sid, frames)
        conn.executemany(
            "INSERT INTO telemetry VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            rows,
        )
        conn.close()

    duration_s = frames[-1].timestamp - frames[0].timestamp
    distance_m = frames[-1].distance - frames[0].distance

    n_caps = _compute_capabilities(sid)

    return jsonify({
        "session_id":         sid,
        "n_frames":           len(frames),
        "duration_s":         round(duration_s, 2),
        "distance_m":         round(distance_m, 1),
        "vbo_source":         os.path.basename(vbo),
        "capabilities_count": n_caps,
    })


# ── Session lifecycle (start / end / list / detail) ────────────────────────

@app.route("/session/start", methods=["POST"])
def session_start():
    """Open a new session row in DuckDB.

    Body fields are optional. If `session_id` is omitted, the bridge generates
    `<track-slug>-<UTC-YYYYMMDD-HHMMSS>`. Idempotent: re-starting an existing
    session_id is a no-op (200 with `started: true` either way).
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    data = request.get_json(force=True, silent=True) or {}
    track_name = data.get("track") or (_track.name if _track else None)
    sid = data.get("session_id") or _new_session_id(track_name)
    _ensure_session_row(
        sid,
        driver=data.get("driver"),
        driver_level=data.get("driver_level"),
        track=track_name,
        car=data.get("car"),
        note=data.get("note"),
    )
    return jsonify({"started": True, "session_id": sid})


@app.route("/session/<sid>/end", methods=["POST"])
def session_end(sid: str):
    """Stamp `ended_at = now()` on a session. Idempotent."""
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        existing = conn.execute(
            "SELECT 1 FROM sessions WHERE session_id = ?", [sid],
        ).fetchone()
        if existing is None:
            conn.execute(
                "INSERT INTO sessions (session_id, ended_at) VALUES (?, now())",
                [sid],
            )
        else:
            conn.execute(
                "UPDATE sessions SET ended_at = now() "
                "WHERE session_id = ? AND ended_at IS NULL",
                [sid],
            )
        conn.close()
    return jsonify({"ended": True, "session_id": sid})


@app.route("/sessions", methods=["GET"])
def sessions_list():
    """List sessions, newest first. `?active_only=true` hides ended ones."""
    if not HAS_DUCKDB:
        return jsonify({"sessions": [], "error": "duckdb not available"})
    try:
        limit = int(request.args.get("limit", 50))
    except ValueError:
        return jsonify({"error": "limit must be an integer"}), 400
    active_only = (request.args.get("active_only", "false").lower() == "true")

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"sessions": []})
        sql = ("SELECT session_id, driver, driver_level, track, car, "
               "started_at, ended_at, note FROM sessions")
        params: list = []
        if active_only:
            sql += " WHERE ended_at IS NULL"
        sql += " ORDER BY started_at DESC LIMIT ?"
        params.append(limit)
        sess_rows = conn.execute(sql, params).fetchall()

        # Per-session derived: lap_count + best_lap_s from the laps table.
        sessions_out: list = []
        for r in sess_rows:
            sid = r[0]
            lap_row = conn.execute(
                "SELECT COUNT(*), MIN(lap_time_s) FROM laps WHERE session_id = ?",
                [sid],
            ).fetchone()
            lap_count = int(lap_row[0]) if lap_row else 0
            best_lap_s = float(lap_row[1]) if lap_row and lap_row[1] is not None else None
            sessions_out.append({
                "session_id":   r[0],
                "driver":       r[1],
                "driver_level": r[2],
                "track":        r[3],
                "car":          r[4],
                "started_at":   r[5].isoformat() if r[5] else None,
                "ended_at":     r[6].isoformat() if r[6] else None,
                "note":         r[7],
                "lap_count":    lap_count,
                "best_lap_s":   best_lap_s,
            })
        conn.close()
    return jsonify({"sessions": sessions_out, "count": len(sessions_out)})


@app.route("/session/<sid>", methods=["GET"])
def session_detail(sid: str):
    """Full session detail: row + laps + recent coaching_notes."""
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        sess_row = conn.execute(
            "SELECT session_id, driver, driver_level, track, car, "
            "started_at, ended_at, note FROM sessions WHERE session_id = ?",
            [sid],
        ).fetchone()
        if sess_row is None:
            conn.close()
            return jsonify({"error": "session not found", "session_id": sid}), 404

        lap_rows = conn.execute(
            "SELECT lap_number, lap_time_s, best_sector, avg_speed_kmh, "
            "max_combo_g, coast_pct, recorded_at FROM laps "
            "WHERE session_id = ? ORDER BY lap_number ASC",
            [sid],
        ).fetchall()

        note_rows = conn.execute(
            "SELECT burst_id, distance_m, text, source, recorded_at "
            "FROM coaching_notes WHERE session_id = ? "
            "ORDER BY recorded_at DESC LIMIT 50",
            [sid],
        ).fetchall()
        conn.close()

    laps = [
        {"lap_number":    int(r[0]) if r[0] is not None else None,
         "lap_time_s":    float(r[1]) if r[1] is not None else None,
         "best_sector":   float(r[2]) if r[2] is not None else None,
         "avg_speed_kmh": float(r[3]) if r[3] is not None else None,
         "max_combo_g":   float(r[4]) if r[4] is not None else None,
         "coast_pct":     float(r[5]) if r[5] is not None else None,
         "recorded_at":   r[6].isoformat() if r[6] else None}
        for r in lap_rows
    ]
    notes = [
        {"burst_id":    r[0], "distance_m": r[1], "text": r[2],
         "source":      r[3], "recorded_at": r[4].isoformat() if r[4] else None}
        for r in note_rows
    ]
    best_lap_s = min((l["lap_time_s"] for l in laps if l["lap_time_s"] is not None),
                     default=None)
    session_dict = {
        "session_id":   sess_row[0],
        "driver":       sess_row[1],
        "driver_level": sess_row[2],
        "track":        sess_row[3],
        "car":          sess_row[4],
        "started_at":   sess_row[5].isoformat() if sess_row[5] else None,
        "ended_at":     sess_row[6].isoformat() if sess_row[6] else None,
        "note":         sess_row[7],
    }
    return jsonify({
        "session":    session_dict,
        "laps":       laps,
        "notes":      notes,
        "lap_count":  len(laps),
        "best_lap_s": best_lap_s,
    })


def _load_session_frames(sid: str):
    """Read all frames for a session from the telemetry table, ordered."""
    if not HAS_DUCKDB:
        return []
    with _db_lock:
        conn = get_db()
        if conn is None:
            return []
        rows = conn.execute(
            "SELECT * FROM telemetry WHERE session_id = ? ORDER BY frame_idx",
            [sid],
        ).fetchall()
        conn.close()
    return _rows_to_frames(rows)


# ── Session analysis (post-debrief, scorecard, highlights, map) ─────────────

# Bridge keeps a small cache of analysed sessions so /scorecard, /highlights,
# /map, /clips, /trace can all read from the same bundle without re-running
# the analyser.
_session_bundles: dict[str, dict] = {}
_BUNDLES_LOCK = threading.Lock()


def _analyse_session_id(sid: str) -> dict:
    """Run the analyser for a session if we haven't already, cache the bundle."""
    if not HAS_ANALYZER:
        return {"error": "session_analyzer not available", "session_id": sid}

    with _BUNDLES_LOCK:
        cached = _session_bundles.get(sid)
        if cached is not None:
            return cached

    # Reconstruct frames from the recorded VBO (if a path was stored on
    # session_start) OR from a runtime-buffered list (future). For the
    # current bridge we only persist coaching_notes — the frames come
    # from the caller as a `vbo_path` query param when running the analyser.
    return {
        "error": "no frame source for this session — POST /coach/debrief with vbo_path",
        "session_id": sid,
    }


@app.route("/coach/debrief", methods=["POST"])
def coach_debrief():
    """Run the post-session analyser and return the full visualisation bundle.

    Body:
        {"session_id": "...", "vbo_path": "/abs/path/to/lap.vbo"}

    `vbo_path` is mandatory — the bridge doesn't yet persist per-frame
    telemetry (coming in a follow-up; see ADR-014 future endpoints).
    """
    if not HAS_ANALYZER:
        return jsonify({"error": "session_analyzer not available"}), 503

    data = request.get_json(force=True, silent=True) or {}
    sid = data.get("session_id") or _new_session_id(_track.name if _track else None)
    vbo = data.get("vbo_path")
    driver_id = data.get("driver_id", "")
    persist_to_profile = data.get("persist_to_profile", True)

    frames = []
    if vbo and os.path.exists(vbo):
        try:
            from vbo_parser import parse_vbo
            _, frames = parse_vbo(vbo)
            if not frames:
                return jsonify({"error": f"no frames in {vbo}"}), 400
        except Exception as e:
            return jsonify({"error": f"parse_vbo failed: {e}"}), 500
    else:
        # No VBO → load from per-frame DuckDB persistence
        frames = _load_session_frames(sid)
        if not frames:
            return jsonify({
                "error": "no telemetry for session — push frames via "
                         "POST /session/<id>/frames or pass vbo_path",
                "session_id": sid,
            }), 400

    bundle = analyze_session(
        session_id=sid,
        frames=frames,
        coach=_coach if HAS_COACH else None,
        driver_level=getattr(_coach, "driver_level", "intermediate") if _coach else "intermediate",
    )

    with _BUNDLES_LOCK:
        _session_bundles[sid] = bundle

    # Persist scorecard events to the driver profile so /coach/brief
    # can read longitudinal trends on the next session
    if persist_to_profile and driver_id and HAS_DUCKDB:
        try:
            with _db_lock:
                conn = get_db()
                if conn is not None:
                    ensure_driver_schema(conn)
                    append_session_events(
                        conn, driver_id, sid, bundle.get("scorecard") or {},
                    )
                    conn.close()
        except Exception:
            pass

    return jsonify(bundle)


def _section(sid: str, key: str):
    bundle = _session_bundles.get(sid)
    if bundle is None:
        return (jsonify({"error": "session not analysed; POST /coach/debrief first",
                         "session_id": sid}), 404)
    return (jsonify({"session_id": sid, key: bundle.get(key)}), 200)


@app.route("/session/<sid>/scorecard", methods=["GET"])
def session_scorecard(sid: str):
    return _section(sid, "scorecard")


@app.route("/session/<sid>/highlights", methods=["GET"])
def session_highlights(sid: str):
    return _section(sid, "highlights")


@app.route("/session/<sid>/stats", methods=["GET"])
def session_stats(sid: str):
    return _section(sid, "stats")


@app.route("/session/<sid>/friction_circle", methods=["GET"])
def session_friction(sid: str):
    return _section(sid, "friction")


@app.route("/session/<sid>/hustle_map", methods=["GET"])
def session_hustle(sid: str):
    return _section(sid, "hustle_map")


@app.route("/session/<sid>/eob", methods=["GET"])
def session_eob(sid: str):
    return _section(sid, "eob")


@app.route("/session/<sid>/incidents", methods=["GET"])
def session_incidents(sid: str):
    return _section(sid, "incidents")


@app.route("/session/<sid>/map", methods=["GET"])
def session_map(sid: str):
    """Map overlay bundle: lap polylines + per-corner color + marker pins.
    Pulls from `data/tracks/sonoma.json` (markers w/ lat/lon) and the
    per-corner grade colors from the scorecard."""
    bundle = _session_bundles.get(sid)
    if bundle is None:
        return jsonify({"error": "session not analysed first",
                        "session_id": sid}), 404
    if _track is None:
        return jsonify({"error": "track not loaded"}), 503

    sc = bundle.get("scorecard") or {}
    grade_color = {"A+": "#1b5e20", "A": "#43a047", "B": "#7cb342",
                   "C": "#fdd835", "D": "#fb8c00", "F": "#e53935"}
    per_corner_color = {
        c["corner"]: grade_color.get(c.get("grade"), "#9e9e9e")
        for c in sc.get("corners", [])
    }

    pins = []
    try:
        # Read marker info straight from the canonical track JSON
        track_path = os.path.join(SIM_DIR, "..", "..", "data", "tracks", "sonoma.json")
        track_path = os.path.abspath(track_path)
        with open(track_path) as f:
            data = json.load(f)
        for m in data.get("markers", []):
            pins.append({
                "id": m.get("id"),
                "label": m.get("label"),
                "kind": m.get("kind"),
                "corner": m.get("corner"),
                "lat": m.get("lat"),
                "lon": m.get("lon"),
                "distance_m": m.get("distance"),
            })
    except Exception:
        pass

    danger = []
    for d in getattr(sonoma, "DANGER_ZONES", ()):
        danger.append({
            "id": d.id, "start_m": d.start_m, "end_m": d.end_m,
            "description": d.description, "severity": d.severity,
        })

    return jsonify({
        "session_id":       sid,
        "track":            _track.name,
        "per_corner_color": per_corner_color,
        "marker_pins":      pins,
        "danger_zones":     danger,
        # `lap_polylines` would carry GeoJSON of the actual recorded lap;
        # not yet built — needs per-frame persistence (ADR-014 followup).
        "lap_polylines":    {},
    })


@app.route("/session/<sid>/clips", methods=["GET"])
def session_clips(sid: str):
    """ffmpeg-ready cut points — derived from highlights' video_in/out fields."""
    bundle = _session_bundles.get(sid)
    if bundle is None:
        return jsonify({"error": "session not analysed first"}), 404
    clips = [
        {
            "id":       f"h{i}",
            "title":    h.get("title", ""),
            "in_s":     h.get("video_in_s", 0),
            "out_s":    h.get("video_out_s", 0),
            "category": h.get("category", ""),
            "severity": h.get("severity", ""),
            "lap":      h.get("lap", 0),
        }
        for i, h in enumerate(bundle.get("highlights") or [])
    ]
    return jsonify({"session_id": sid, "clips": clips, "count": len(clips)})


# ── Phase-6: lap performance + corner/straight aggregates ───────────────────

def _laps_or_400(sid: str):
    """Resolve laps for a session; returns (laps, error_response)."""
    if not _session_has_telemetry(sid):
        return None, (jsonify({"error": "session not found", "session_id": sid}), 404)
    laps = _detect_laps(sid)
    if not laps:
        return None, (jsonify({
            "error":      "no complete laps detected",
            "session_id": sid,
        }), 400)
    return laps, None


@app.route("/session/<sid>/lap_time_table", methods=["GET"])
def session_lap_time_table(sid: str):
    """Per-lap times + sector splits, with best-lap and best-sector flags."""
    laps, err = _laps_or_400(sid)
    if err:
        return err

    sectors_per_lap = [_lap_sectors(sid, l) for l in laps]
    sector_names = [s.name for s in sonoma.SECTORS]

    best_t = min(l["lap_time_s"] for l in laps)
    best_lap_no = next(l["lap_number"] for l in laps if l["lap_time_s"] == best_t)

    best_sector_t: dict = {}
    for nm in sector_names:
        ts = [s["time_s"] for sl in sectors_per_lap for s in sl if s["name"] == nm]
        if ts:
            best_sector_t[nm] = min(ts)

    laps_out = []
    for lap, secs in zip(laps, sectors_per_lap):
        sec_out = []
        for s in secs:
            best = best_sector_t.get(s["name"])
            sec_out.append({
                "name":    s["name"],
                "time_s":  round(s["time_s"], 3),
                "is_best": best is not None and abs(s["time_s"] - best) < 1e-6,
            })
        laps_out.append({
            "lap_number":      lap["lap_number"],
            "lap_time_s":      round(lap["lap_time_s"], 3),
            "delta_to_best_s": round(lap["lap_time_s"] - best_t, 3),
            "is_best":         lap["lap_number"] == best_lap_no,
            "sectors":         sec_out,
        })
    return jsonify({
        "session_id":      sid,
        "lap_count":       len(laps),
        "best_lap_s":      round(best_t, 3),
        "best_lap_number": best_lap_no,
        "laps":            laps_out,
    })


@app.route("/session/<sid>/lap_time_distribution", methods=["GET"])
def session_lap_time_distribution(sid: str):
    """Tukey box-plot statistics over the session's lap times."""
    laps, err = _laps_or_400(sid)
    if err:
        return err
    times = sorted(l["lap_time_s"] for l in laps)
    n = len(times)
    q1 = _quantile(times, 0.25)
    q2 = _quantile(times, 0.50)
    q3 = _quantile(times, 0.75)
    iqr = q3 - q1
    lo_fence = q1 - 1.5 * iqr
    hi_fence = q3 + 1.5 * iqr
    in_range = [t for t in times if lo_fence <= t <= hi_fence]
    whisker_low = min(in_range) if in_range else times[0]
    whisker_high = max(in_range) if in_range else times[-1]
    outliers = [
        {"lap_number": l["lap_number"], "lap_time_s": round(l["lap_time_s"], 3)}
        for l in laps if l["lap_time_s"] < lo_fence or l["lap_time_s"] > hi_fence
    ]
    mu = sum(times) / n
    var = sum((t - mu) ** 2 for t in times) / n
    sigma = var ** 0.5
    return jsonify({
        "session_id":     sid,
        "lap_count":      n,
        "min_s":          round(times[0], 3),
        "max_s":          round(times[-1], 3),
        "q1_s":           round(q1, 3),
        "median_s":       round(q2, 3),
        "q3_s":           round(q3, 3),
        "iqr_s":          round(iqr, 3),
        "whisker_low_s":  round(whisker_low, 3),
        "whisker_high_s": round(whisker_high, 3),
        "outliers":       outliers,
        "mean_s":         round(mu, 3),
        "stddev_s":       round(sigma, 3),
    })


@app.route("/session/<sid>/ideal_lap", methods=["GET"])
def session_ideal_lap(sid: str):
    """Theoretical fastest lap = sum of best per-sector times."""
    laps, err = _laps_or_400(sid)
    if err:
        return err
    sectors_per_lap = [_lap_sectors(sid, l) for l in laps]
    best_per_sector = []
    for sec in sonoma.SECTORS:
        best_time = None
        from_lap = None
        for lap, secs in zip(laps, sectors_per_lap):
            for s in secs:
                if s["name"] != sec.name:
                    continue
                if best_time is None or s["time_s"] < best_time:
                    best_time = s["time_s"]
                    from_lap = lap["lap_number"]
        if best_time is not None:
            best_per_sector.append({
                "name":     sec.name,
                "time_s":   round(best_time, 3),
                "from_lap": from_lap,
            })
    if not best_per_sector:
        return jsonify({"error": "no sector times computed"}), 400
    ideal = sum(s["time_s"] for s in best_per_sector)
    best_actual = min(l["lap_time_s"] for l in laps)
    return jsonify({
        "session_id":        sid,
        "ideal_lap_s":       round(ideal, 3),
        "best_actual_lap_s": round(best_actual, 3),
        "gain_potential_s":  round(best_actual - ideal, 3),
        "best_sectors":      best_per_sector,
    })


@app.route("/session/<sid>/sector_times", methods=["GET"])
def session_sector_times(sid: str):
    """Thinner per-lap-per-sector view: just S1/S2/S3 numbers."""
    laps, err = _laps_or_400(sid)
    if err:
        return err
    sectors_per_lap = [_lap_sectors(sid, l) for l in laps]
    laps_out = []
    for lap, secs in zip(laps, sectors_per_lap):
        s_by_name = {s["name"]: s["time_s"] for s in secs}
        laps_out.append({
            "lap_number": lap["lap_number"],
            "s1": round(s_by_name.get(sonoma.SECTORS[0].name, 0.0), 3),
            "s2": round(s_by_name.get(sonoma.SECTORS[1].name, 0.0), 3),
            "s3": round(s_by_name.get(sonoma.SECTORS[2].name, 0.0), 3),
        })
    return jsonify({
        "session_id": sid,
        "sector_definitions": [
            {"name": s.name, "start_m": s.start_m, "end_m": s.end_m}
            for s in sonoma.SECTORS
        ],
        "laps": laps_out,
    })


@app.route("/session/<sid>/pedal_behavior", methods=["GET"])
def session_pedal_behavior(sid: str):
    """4-state distribution: throttle_only / brake_only / trail_brake / coast."""
    if not _session_has_telemetry(sid):
        return jsonify({"error": "session not found", "session_id": sid}), 404
    try:
        thr_th = float(request.args.get("throttle_th") or 5.0)
        brk_th = float(request.args.get("brake_th") or 1.0)
    except ValueError:
        return jsonify({"error": "thresholds must be numbers"}), 400

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        rows = conn.execute(
            "SELECT timestamp, throttle_pct, brake_bar FROM telemetry "
            "WHERE session_id = ? ORDER BY timestamp", [sid],
        ).fetchall()
        conn.close()
    if not rows:
        return jsonify({"error": "session not found"}), 404

    states = {"throttle_only": 0, "brake_only": 0, "trail_brake": 0, "coast": 0}
    n = 0
    for _t, thr, brk in rows:
        thr = thr or 0.0
        brk = brk or 0.0
        on_thr = thr > thr_th
        on_brk = brk > brk_th
        if on_thr and on_brk:
            states["trail_brake"] += 1
        elif on_thr:
            states["throttle_only"] += 1
        elif on_brk:
            states["brake_only"] += 1
        else:
            states["coast"] += 1
        n += 1

    deltas = sorted(rows[i + 1][0] - rows[i][0] for i in range(len(rows) - 1)) if len(rows) > 1 else [0.1]
    frame_dt = deltas[len(deltas) // 2] if deltas else 0.1

    out = {}
    for k, v in states.items():
        out[k] = {
            "frames": v,
            "pct":    round(100.0 * v / n, 1) if n else 0.0,
            "time_s": round(v * frame_dt, 1),
        }
    return jsonify({
        "session_id":  sid,
        "frame_count": n,
        "thresholds":  {"throttle_pct": thr_th, "brake_bar": brk_th},
        "frame_dt_s":  round(frame_dt, 4),
        "states":      out,
    })


@app.route("/session/<sid>/throttle_corner_box", methods=["GET"])
def session_throttle_corner_box(sid: str):
    """Per-corner throttle box-plot stats over all passes through that corner."""
    if not _session_has_telemetry(sid):
        return jsonify({"error": "session not found", "session_id": sid}), 404
    track = _load_track_json("sonoma") or {}
    corner_bounds = _corner_bounds_from_track(track)
    if not corner_bounds:
        return jsonify({"error": "no corner geometry available"}), 422

    laps = _detect_laps(sid)
    n_passes_default = max(len(laps), 1)

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        out: list = []
        for c in corner_bounds:
            rows = conn.execute(
                "SELECT throttle_pct FROM telemetry "
                "WHERE session_id = ? AND distance_m >= ? AND distance_m <= ?",
                [sid, c["entry_m"], c["exit_m"]],
            ).fetchall()
            samples = sorted(float(r[0]) for r in rows if r[0] is not None)
            if not samples:
                out.append({
                    "name":       c["name"],
                    "n_passes":   0,
                    "n_samples":  0,
                    "min_pct":    None, "q1_pct": None,
                    "median_pct": None, "q3_pct": None,
                    "max_pct":    None, "mean_pct": None,
                })
                continue
            mean = sum(samples) / len(samples)
            out.append({
                "name":       c["name"],
                "n_passes":   n_passes_default,
                "n_samples":  len(samples),
                "min_pct":    round(samples[0], 2),
                "q1_pct":     round(_quantile(samples, 0.25), 2),
                "median_pct": round(_quantile(samples, 0.50), 2),
                "q3_pct":     round(_quantile(samples, 0.75), 2),
                "max_pct":    round(samples[-1], 2),
                "mean_pct":   round(mean, 2),
            })
        conn.close()
    return jsonify({"session_id": sid, "corners": out})


@app.route("/session/<sid>/corner_classification", methods=["GET"])
def session_corner_classification(sid: str):
    """Group corners into low/med/high speed bands by min apex speed."""
    if not _session_has_telemetry(sid):
        return jsonify({"error": "session not found", "session_id": sid}), 404
    try:
        low_max = float(request.args.get("low_max") or 80.0)
        med_max = float(request.args.get("med_max") or 130.0)
    except ValueError:
        return jsonify({"error": "thresholds must be numbers"}), 400
    track = _load_track_json("sonoma") or {}
    corner_bounds = _corner_bounds_from_track(track)
    if not corner_bounds:
        return jsonify({"error": "no corner geometry available"}), 422

    bands = {"low_speed": [], "med_speed": [], "high_speed": []}
    apex_speeds_by_band: dict = {"low_speed": [], "med_speed": [], "high_speed": []}

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        for c in corner_bounds:
            row = conn.execute(
                "SELECT MIN(speed_ms) FROM telemetry "
                "WHERE session_id = ? AND distance_m >= ? AND distance_m <= ?",
                [sid, c["entry_m"], c["exit_m"]],
            ).fetchone()
            min_ms = row[0] if row else None
            if min_ms is None:
                continue
            apex_kmh = float(min_ms) * 3.6
            if apex_kmh < low_max:
                band = "low_speed"
            elif apex_kmh < med_max:
                band = "med_speed"
            else:
                band = "high_speed"
            bands[band].append(c["name"])
            apex_speeds_by_band[band].append(apex_kmh)
        conn.close()

    out = []
    for band_name in ("low_speed", "med_speed", "high_speed"):
        speeds = apex_speeds_by_band[band_name]
        out.append({
            "band":              band_name,
            "corners":           bands[band_name],
            "mean_apex_kmh":     round(sum(speeds) / len(speeds), 1) if speeds else None,
            "median_apex_kmh":   round(_quantile(sorted(speeds), 0.5), 1) if speeds else None,
        })
    return jsonify({
        "session_id": sid,
        "thresholds": {"low_max_kmh": low_max, "med_max_kmh": med_max},
        "bands":      out,
    })


@app.route("/session/<sid>/straight_line_speed", methods=["GET"])
def session_straight_line_speed(sid: str):
    """Top speed per named straight (sonoma.STRAIGHTS)."""
    if not _session_has_telemetry(sid):
        return jsonify({"error": "session not found", "session_id": sid}), 404
    laps = _detect_laps(sid)
    track_len = float(getattr(sonoma, "TRACK_LENGTH_M", 4258))

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503

        out = []
        for st in sonoma.STRAIGHTS:
            if st.start_m <= st.end_m:
                where = "distance_m >= ? AND distance_m <= ?"
                params = [sid, st.start_m, st.end_m]
            else:
                # wraps S/F: distance_m >= start OR distance_m <= end
                where = "(distance_m >= ? OR distance_m <= ?)"
                params = [sid, st.start_m, st.end_m]
            row = conn.execute(
                f"SELECT timestamp, speed_ms, distance_m FROM telemetry "
                f"WHERE session_id = ? AND {where} "
                f"ORDER BY speed_ms DESC LIMIT 1",
                params,
            ).fetchone()
            if row is None:
                out.append({
                    "name": st.name, "start_m": st.start_m, "end_m": st.end_m,
                    "top_speed_kmh": None, "from_lap": None,
                })
                continue
            t_top, top_ms, _d = row
            from_lap = None
            for l in laps:
                if l["t_start"] <= t_top <= l["t_end"]:
                    from_lap = l["lap_number"]
                    break
            out.append({
                "name":          st.name,
                "start_m":       st.start_m,
                "end_m":         st.end_m,
                "top_speed_kmh": round(float(top_ms) * 3.6, 1),
                "from_lap":      from_lap,
            })
        conn.close()
    return jsonify({"session_id": sid, "track_length_m": track_len, "straights": out})


@app.route("/session/<sid>/brake_acceleration", methods=["GET"])
def session_brake_acceleration(sid: str):
    """Heavy-brake decel + corner-exit longitudinal accel scatter."""
    if not _session_has_telemetry(sid):
        return jsonify({"error": "session not found", "session_id": sid}), 404
    track = _load_track_json("sonoma") or {}
    corner_bounds = _corner_bounds_from_track(track)
    if not corner_bounds:
        return jsonify({"error": "no corner geometry available"}), 422

    BRAKE_TH = 25.0
    laps = _detect_laps(sid)
    n_passes = max(len(laps), 1)

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        rows = conn.execute(
            "SELECT timestamp, distance_m, brake_bar, throttle_pct, g_long, speed_ms "
            "FROM telemetry WHERE session_id = ? ORDER BY timestamp", [sid],
        ).fetchall()
        conn.close()

    def nearest_corner(d):
        if d is None:
            return None
        return min(corner_bounds, key=lambda c: abs(d - c["entry_m"]))["name"]

    # Brake zones: contiguous frames with brake > 25 bar.
    brake_zones: list = []
    current: list = []
    for r in rows:
        if (r[2] or 0.0) > BRAKE_TH:
            current.append(r)
        elif current:
            brake_zones.append(current)
            current = []
    if current:
        brake_zones.append(current)

    decel_by_corner: dict = {}
    duration_by_corner: dict = {}
    for zone in brake_zones:
        ts = [z[0] for z in zone]
        glongs = [z[4] for z in zone if z[4] is not None]
        d_mid = zone[len(zone) // 2][1]
        if not glongs:
            continue
        cname = nearest_corner(d_mid)
        if cname is None:
            continue
        peak = min(glongs)
        decel_by_corner.setdefault(cname, []).append(peak)
        duration_by_corner.setdefault(cname, []).append(ts[-1] - ts[0])

    brake_zones_out = []
    for cname in sorted(decel_by_corner.keys()):
        peaks = decel_by_corner[cname]
        durs = duration_by_corner.get(cname, [])
        brake_zones_out.append({
            "corner":      cname,
            "max_decel_g": round(sum(peaks) / len(peaks), 3),
            "duration_s":  round(sum(durs) / len(durs), 2) if durs else 0.0,
            "n_passes":    len(peaks),
        })

    # Corner exits: throttle > 50% within (apex_m, exit_m).
    accel_by_corner: dict = {}
    exit_speed_by_corner: dict = {}
    for c in corner_bounds:
        glongs = [r[4] for r in rows
                  if r[1] is not None and c["apex_m"] <= r[1] <= c["exit_m"]
                  and (r[3] or 0) > 50 and r[4] is not None]
        exit_speeds = [r[5] for r in rows
                       if r[1] is not None and abs(r[1] - c["exit_m"]) < 5
                       and r[5] is not None]
        if glongs:
            accel_by_corner[c["name"]] = max(glongs)
        if exit_speeds:
            exit_speed_by_corner[c["name"]] = sum(exit_speeds) / len(exit_speeds)

    corner_exits_out = []
    for cname in sorted(accel_by_corner.keys()):
        corner_exits_out.append({
            "corner":           cname,
            "max_long_accel_g": round(accel_by_corner[cname], 3),
            "exit_speed_kmh":   round(exit_speed_by_corner.get(cname, 0.0) * 3.6, 1),
            "n_passes":         n_passes,
        })

    return jsonify({
        "session_id":   sid,
        "brake_zones":  brake_zones_out,
        "corner_exits": corner_exits_out,
    })


@app.route("/track/<track_id>/elevation", methods=["GET"])
def track_elevation(track_id: str):
    """Elevation profile sampled along the centerline of a track JSON."""
    track = _load_track_json(track_id)
    if track is None:
        return jsonify({"error": "track not found", "track_id": track_id}), 404
    profile = track.get("elevation_profile") or []
    if not profile:
        ref = track.get("reference_line") or []
        if not ref:
            return jsonify({
                "error":    "no reference_line — cannot derive elevation",
                "track_id": track_id,
            }), 422
        return jsonify({
            "track_id":         track_id,
            "name":             track.get("name", track_id),
            "track_length_m":   track.get("track_length_m"),
            "elevation_source": "missing",
            "samples":          [],
        })
    try:
        step_m = float(request.args.get("step_m") or 10.0)
    except ValueError:
        return jsonify({"error": "step_m must be a number"}), 400
    if step_m <= 0:
        return jsonify({"error": "step_m must be > 0"}), 400

    pts = sorted(profile, key=lambda p: float(p.get("distance", 0.0)))
    max_d = float(pts[-1].get("distance", 0.0))
    samples: list = []
    j = 0
    d = 0.0
    while d <= max_d + 1e-9:
        while j + 1 < len(pts) and float(pts[j + 1]["distance"]) < d:
            j += 1
        if j + 1 >= len(pts):
            elev = float(pts[-1].get("altitude") or 0.0)
        else:
            d0 = float(pts[j].get("distance", 0.0))
            d1 = float(pts[j + 1].get("distance", 0.0))
            a0 = float(pts[j].get("altitude") or 0.0)
            a1 = float(pts[j + 1].get("altitude") or 0.0)
            elev = a0 if d1 == d0 else a0 + (a1 - a0) * (d - d0) / (d1 - d0)
        samples.append({"distance_m": round(d, 2), "elevation_m": round(elev, 2)})
        d += step_m

    elevs = [s["elevation_m"] for s in samples if s["elevation_m"] is not None]
    return jsonify({
        "track_id":         track_id,
        "name":             track.get("name", track_id),
        "track_length_m":   track.get("track_length_m"),
        "step_m":           step_m,
        "elevation_source": "json_profile",
        "min_elevation_m":  min(elevs) if elevs else None,
        "max_elevation_m":  max(elevs) if elevs else None,
        "samples":          samples,
    })


@app.route("/driver/<driver_id>/evolution", methods=["GET"])
def driver_evolution(driver_id: str):
    """Multi-session driver evolution time-series.

    204 if the driver has < 5 sessions for the requested track (frontend
    hides the panel). 404 if zero sessions for that filter.
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    track_filter = request.args.get("track")

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        sql = ("SELECT session_id, started_at, track FROM sessions "
               "WHERE driver = ?")
        params: list = [driver_id]
        if track_filter:
            sql += " AND (track = ? OR track IS NULL)"
            params.append(track_filter)
        sql += " ORDER BY started_at ASC"
        sess_rows = conn.execute(sql, params).fetchall()
        conn.close()

    if not sess_rows:
        return jsonify({
            "error":     "no sessions for this driver/track",
            "driver_id": driver_id,
        }), 404

    if len(sess_rows) < 5:
        return jsonify({
            "driver_id":     driver_id,
            "track":         track_filter,
            "session_count": len(sess_rows),
            "evolution":     [],
            "summary":       None,
            "note":          "evolution requires >= 5 sessions",
        }), 204

    evolution = []
    for idx, (sess_id, started_at, sess_track) in enumerate(sess_rows, start=1):
        laps = _detect_laps(sess_id)
        if not laps:
            continue
        times = sorted(l["lap_time_s"] for l in laps)
        sectors_per_lap = [_lap_sectors(sess_id, l) for l in laps]
        sec_pbs: dict = {}
        for sec_idx, sec in enumerate(sonoma.SECTORS):
            ts = [s["time_s"] for sl in sectors_per_lap for s in sl if s["name"] == sec.name]
            if ts:
                sec_pbs[f"s{sec_idx + 1}"] = round(min(ts), 3)
        evolution.append({
            "session_id":    sess_id,
            "started_at":    started_at.isoformat() if started_at else None,
            "session_index": idx,
            "best_lap_s":    round(times[0], 3),
            "median_lap_s":  round(_quantile(times, 0.5), 3),
            "lap_count":     len(laps),
            "sector_pbs":    sec_pbs,
        })

    if not evolution:
        return jsonify({
            "driver_id":     driver_id,
            "track":         track_filter,
            "session_count": len(sess_rows),
            "evolution":     [],
            "summary":       None,
            "note":          "no laps detected in any session",
        })

    first_best = evolution[0]["best_lap_s"]
    latest_best = evolution[-1]["best_lap_s"]
    return jsonify({
        "driver_id":     driver_id,
        "track":         track_filter,
        "session_count": len(evolution),
        "evolution":     evolution,
        "summary": {
            "first_best_s":   first_best,
            "latest_best_s":  latest_best,
            "improvement_s":  round(first_best - latest_best, 3),
        },
    })


# ── Roadmap endpoints (api.md §"Roadmap, proposed") ────────────────────────

@app.route("/session/<sid>/frame", methods=["POST"])
def session_frame(sid: str):
    """Append a single telemetry frame. Foundation for per-corner replay.

    Body shape: same fields as one element of `/frames` (timestamp, distance,
    speed, g_lat, g_long, combo_g, brake_pressure, throttle, steering, rpm,
    lat, lon). Returns the assigned `frame_idx` so the caller can build a
    per-corner replay reference.
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    f = request.get_json(force=True, silent=True) or {}
    if not isinstance(f, dict) or not f:
        return jsonify({"error": "no frame body"}), 400

    _ensure_session_row(
        sid,
        driver=f.get("driver"),
        track=f.get("track"),
    )

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        existing = conn.execute(
            "SELECT COALESCE(MAX(frame_idx), -1) FROM telemetry WHERE session_id = ?",
            [sid],
        ).fetchone()[0]
        next_idx = (existing if existing is not None else -1) + 1
        try:
            conn.execute(
                "INSERT INTO telemetry VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (sid, next_idx,
                 float(f.get("timestamp", 0)),
                 float(f.get("distance", 0)),
                 float(f.get("speed", 0)),
                 float(f.get("g_lat", 0)),
                 float(f.get("g_long", 0)),
                 float(f.get("combo_g", 0)),
                 float(f.get("brake_pressure", 0)),
                 float(f.get("throttle", 0)),
                 float(f.get("steering", 0)),
                 float(f.get("rpm", 0)),
                 float(f.get("lat", 0)),
                 float(f.get("lon", 0))),
            )
        except (TypeError, ValueError) as e:
            conn.close()
            return jsonify({"error": f"invalid frame: {e}"}), 400
        conn.close()
    return jsonify({"saved": True, "session_id": sid, "frame_idx": next_idx})


@app.route("/session/<sid>/corners", methods=["GET"])
def session_corners(sid: str):
    """Per-corner aggregates: best pass + averages, optional gold-standard delta.

    For each corner in the track JSON, walks every detected lap and computes
    entry/apex/exit speeds, peak brake, max gLat, and corner time. Best pass
    is the lap with the highest apex speed (least time spent slow).
    """
    laps, err = _laps_or_400(sid)
    if err:
        return err
    track = _load_track_json("sonoma") or {}
    corner_bounds = _corner_bounds_from_track(track)
    if not corner_bounds:
        return jsonify({"error": "no corner geometry available"}), 422

    gold_by_corner = {}
    try:
        from gold_standard import load_gold_standard
        gold_path = os.path.abspath(os.path.join(
            SIM_DIR, "..", "..", "data", "reference", "sonoma_gold.json",
        ))
        if os.path.exists(gold_path):
            gold = load_gold_standard(gold_path)
            for cp in (gold.corner_passes if hasattr(gold, "corner_passes") else []):
                gold_by_corner[cp.corner] = cp
    except Exception:
        pass

    out = []
    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503

        for c in corner_bounds:
            passes = []
            for lap in laps:
                rows = conn.execute(
                    "SELECT timestamp, distance_m, speed_ms, brake_bar, g_lat "
                    "FROM telemetry WHERE session_id = ? "
                    "AND timestamp >= ? AND timestamp <= ? "
                    "AND distance_m >= ? AND distance_m <= ? "
                    "ORDER BY timestamp",
                    [sid, lap["t_start"], lap["t_end"],
                     c["entry_m"] - 50, c["exit_m"] + 10],
                ).fetchall()
                if not rows:
                    continue
                in_corner = [r for r in rows
                             if r[1] is not None and c["entry_m"] <= r[1] <= c["exit_m"]]
                if not in_corner:
                    continue
                speeds = [r[2] for r in in_corner if r[2] is not None]
                if not speeds:
                    continue
                # Entry / exit speeds at the corner boundaries (closest sample).
                entry_row = min(in_corner, key=lambda r: abs((r[1] or 0) - c["entry_m"]))
                exit_row  = min(in_corner, key=lambda r: abs((r[1] or 0) - c["exit_m"]))
                apex_idx = min(range(len(in_corner)), key=lambda i: in_corner[i][2] or 1e9)
                apex_row = in_corner[apex_idx]
                peak_brake = max(((r[3] or 0) for r in rows), default=0.0)
                max_glat   = max((abs(r[4] or 0) for r in in_corner), default=0.0)
                t_in       = in_corner[0][0]
                t_out      = in_corner[-1][0]
                passes.append({
                    "lap_number":      lap["lap_number"],
                    "entry_speed_kmh": round(float(entry_row[2] or 0) * 3.6, 1),
                    "apex_speed_kmh":  round(float(apex_row[2]  or 0) * 3.6, 1),
                    "exit_speed_kmh":  round(float(exit_row[2]  or 0) * 3.6, 1),
                    "peak_brake_bar":  round(float(peak_brake), 1),
                    "max_g_lat":       round(float(max_glat), 2),
                    "corner_time_s":   round(float(t_out - t_in), 3),
                    "apex_distance_m": round(float(apex_row[1] or 0), 1),
                })
            if not passes:
                out.append({"name": c["name"], "n_passes": 0,
                            "best_pass": None, "averages": None,
                            "grade": "ungraded", "gold_delta_kmh": None})
                continue

            best_pass = max(passes, key=lambda p: p["apex_speed_kmh"])
            apexes = [p["apex_speed_kmh"] for p in passes]
            ctimes = [p["corner_time_s"] for p in passes]
            averages = {
                "apex_speed_kmh": round(sum(apexes) / len(apexes), 1),
                "corner_time_s":  round(sum(ctimes) / len(ctimes), 3),
            }

            grade = "ungraded"
            gold_delta_kmh = None
            gold = gold_by_corner.get(c["name"])
            if gold is not None:
                gold_apex = float(getattr(gold, "apex_speed_kmh", 0))
                if gold_apex > 0:
                    delta = best_pass["apex_speed_kmh"] - gold_apex
                    gold_delta_kmh = round(delta, 1)
                    # Grade: ≥-1 km/h = A, -3 = B, -5 = C, -8 = D, worse = F.
                    if delta >= -1.0:
                        grade = "A"
                    elif delta >= -3.0:
                        grade = "B"
                    elif delta >= -5.0:
                        grade = "C"
                    elif delta >= -8.0:
                        grade = "D"
                    else:
                        grade = "F"

            out.append({
                "name":           c["name"],
                "n_passes":       len(passes),
                "best_pass":      best_pass,
                "averages":       averages,
                "grade":          grade,
                "gold_delta_kmh": gold_delta_kmh,
                "passes":         passes,
            })
        conn.close()

    return jsonify({
        "session_id":      sid,
        "track":           track.get("name", "Sonoma Raceway"),
        "lap_count":       len(laps),
        "corners":         out,
        "gold_available":  bool(gold_by_corner),
    })


@app.route("/score", methods=["POST"])
def llm_score():
    """Local-Gemma-graded session score: 0–100 + one-sentence why.

    Paddock-time endpoint. Calls the on-device Gemma 4 E2B via litert-lm
    (no cloud dependency). Body: {session_id, focus?, driver_level?}.

    Status codes:
      400  missing session_id
      404  session has no telemetry
      503  no LLM coach loaded (LitertCoach unavailable; user can re-run
           after `litert-lm import …` populates the model file)
      502  LLM call raised
      200  {session_id, score, why, model, focus}
    """
    body = request.get_json(force=True, silent=True) or {}
    sid = body.get("session_id")
    focus = body.get("focus", "")
    driver_level = body.get("driver_level", "intermediate")
    if not sid:
        return jsonify({"error": "session_id required"}), 400
    if not _session_has_telemetry(sid):
        return jsonify({"error": "session not found", "session_id": sid}), 404

    coach = _coach
    if coach is None or getattr(coach, "name", "") != "litert" or getattr(coach, "_engine", None) is None:
        return jsonify({
            "error":  "local Gemma coach not loaded — install litert-lm and "
                      "import the .litertlm model (see README §Quick start)",
            "engine": getattr(coach, "name", None),
        }), 503

    laps = _detect_laps(sid)
    best_lap_s = min((l["lap_time_s"] for l in laps), default=None)

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        agg = conn.execute(
            "SELECT AVG(speed_ms), MAX(combo_g), MAX(brake_bar), AVG(throttle_pct) "
            "FROM telemetry WHERE session_id = ?", [sid],
        ).fetchone()
        conn.close()
    avg_speed_ms, max_combo_g, max_brake_bar, avg_throttle = (agg or (0, 0, 0, 0))

    system_prompt = (
        f"You are an expert race coach grading a {driver_level} driver after "
        f"a Sonoma Raceway session. Score 0–100 (100 = textbook fast lap, "
        f"50 = average track-day, 0 = unsafe). Ground judgement in Ross "
        f"Bentley's pedagogy: trail-brake quality, exit-speed beats "
        f"corner-speed, look-ahead. Respond with ONE JSON object only, "
        f'no preface: {{"score": <int 0-100>, "why": "<one sentence>"}}.'
    )

    user_prompt_lines = ["Session stats:"]
    if best_lap_s is not None:
        user_prompt_lines.append(f"- best lap: {best_lap_s:.2f}s")
    else:
        user_prompt_lines.append("- no complete lap")
    user_prompt_lines += [
        f"- lap count: {len(laps)}",
        f"- average speed: {(avg_speed_ms or 0) * 3.6:.0f} km/h",
        f"- peak combined G: {(max_combo_g or 0):.2f}",
        f"- peak brake: {(max_brake_bar or 0):.0f} bar",
        f"- average throttle: {(avg_throttle or 0):.0f}%",
    ]
    if focus:
        user_prompt_lines.append(f"- focus corner: {focus}")
    user_prompt = "\n".join(user_prompt_lines)

    try:
        text = coach._generate(system_prompt, user_prompt) or ""
        # Strip any code-fence Gemma may add around the JSON
        if "```json" in text:
            text = text.split("```json", 1)[1].split("```", 1)[0]
        elif "```" in text:
            text = text.split("```", 1)[1].split("```", 1)[0]
        # Keep only the first JSON object
        start = text.find("{")
        end = text.rfind("}")
        if start == -1 or end == -1 or end <= start:
            raise ValueError(f"no JSON object in model output: {text[:200]!r}")
        data = json.loads(text[start:end + 1])
        score = int(data.get("score", 0))
        why = str(data.get("why", "")).strip()
    except Exception as e:
        return jsonify({"error": f"llm call failed: {e}"}), 502

    return jsonify({
        "session_id": sid,
        "score":      max(0, min(100, score)),
        "why":        why,
        "model":      "gemma-4-e2b (litert-lm)",
        "focus":      focus or None,
    })


@app.route("/markers", methods=["GET"])
def markers_filtered():
    """Filterable view over the Sonoma marker schema (ADR-011).

    Query params (all optional, intersect):
        corner    e.g. "Turn 11"
        kind      "brake" | "apex" | "track_out" | "reference"
    """
    track_path = os.path.abspath(os.path.join(
        SIM_DIR, "..", "..", "data", "tracks", "sonoma.json",
    ))
    try:
        with open(track_path) as fh:
            data = json.load(fh)
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    markers = data.get("markers", [])
    corner = request.args.get("corner")
    kind = request.args.get("kind")
    if corner:
        markers = [m for m in markers if m.get("corner") == corner]
    if kind:
        markers = [m for m in markers if m.get("kind") == kind]
    return jsonify({
        "track":    data.get("name", "Sonoma Raceway"),
        "filters":  {"corner": corner, "kind": kind},
        "markers":  markers,
        "count":    len(markers),
    })


# Static map: concept_id → (description, when-fired hint). Mirrors the
# match_bentley_concept() if-tree so the frontend can render a tray of
# coachable behaviours without parsing Python source.
_BENTLEY_CONCEPTS = (
    {"id": "trail_brake",   "description": "Smoothly release brake as steering increases at corner entry.",
     "fires_when": "in_corner AND brake>10% AND |g_lat|>0.4 g"},
    {"id": "entry_release", "description": "Keep some brake on entry to load the front tires (anti-pattern: bleeding off too soon).",
     "fires_when": "in_corner AND brake<1% AND |g_lat|>0.6 g"},
    {"id": "exit_speed",    "description": "Throttle on early — exit speed beats corner speed.",
     "fires_when": "past_apex AND throttle<20% AND |g_lat|>0.3 g"},
    {"id": "hustle",        "description": "Commit to 100% throttle on the straights, even briefly.",
     "fires_when": "not in_corner AND throttle<5% AND brake<2 bar AND speed>50 km/h"},
    {"id": "eob",           "description": "Look at the end of braking, not the start.",
     "fires_when": "30m < meters_to_entry < brake_zone_m+30 AND brake<2%"},
    {"id": "downhill_brake","description": "Downhill: brake earlier — gravity adds to your speed.",
     "fires_when": "next_elevation_change < -5m AND meters_to_entry<200"},
    {"id": "uphill_brake",  "description": "Uphill: brake zone is shorter — the climb decelerates you.",
     "fires_when": "next_elevation_change > 5m AND meters_to_entry<200"},
    {"id": "late_apex",     "description": "Late apex for a faster exit.",
     "fires_when": "0 < meters_to_entry < 250 (default approach reminder)"},
    {"id": "look_ahead",    "description": "Eyes far ahead — vision drives the line.",
     "fires_when": "on a clean straight far from anything"},
)


@app.route("/coach/concepts", methods=["GET"])
def coach_concepts():
    """List the 9 Bentley pedagogical concepts the coach can fire (ADR-012)."""
    return jsonify({
        "source":   "Ross Bentley — Performance Driving Illustrated",
        "concepts": list(_BENTLEY_CONCEPTS),
        "count":    len(_BENTLEY_CONCEPTS),
    })


# ── Pre-brief + driver profile ───────────────────────────────────────────────

@app.route("/coach/brief", methods=["GET"])
def coach_brief():
    """Pre-session focus plan. Reads driver profile + today's weather + selected markers.

    Query params:
        driver       (str)  required
        date         (ISO8601, default today)
        focus        (comma-list of corner names)
        goal         (str, default "personal best lap")
        hour_local   (int, default current local hour)
    """
    if not HAS_COACH:
        return jsonify({"error": "coach_engine not available"}), 503
    driver_id = request.args.get("driver", "")
    today = request.args.get("date") or datetime.utcnow().date().isoformat()
    focus_csv = request.args.get("focus", "")
    goal = request.args.get("goal", "personal best lap")
    try:
        hour_local = int(request.args.get("hour_local", datetime.now().hour))
    except ValueError:
        hour_local = 12

    markers_selected = [s.strip() for s in focus_csv.split(",") if s.strip()]
    weather_phase = sonoma.weather_phase_for_hour(hour_local)

    profile = {}
    if HAS_ANALYZER and HAS_DUCKDB and driver_id:
        try:
            with _db_lock:
                conn = get_db()
                if conn is not None:
                    profile = compute_profile(conn, driver_id)
                    conn.close()
        except Exception:
            profile = {}

    danger_today = [
        f"{d.id}: {d.description}"
        for d in sonoma.DANGER_ZONES
        if (weather_phase.id == "morning_fog" and d.severity in ("high", "medium"))
        or d.severity == "high"
    ]

    emotion = "neutral"
    sid_param = (request.args.get("session_id") or "").strip() or None
    if hasattr(_coach, "brief"):
        try:
            result = _coach.brief(
                driver_id=driver_id,
                today_iso=today,
                weather_phase=weather_phase.id,
                surface_state=weather_phase.surface_state,
                markers_selected=markers_selected,
                weakest_recent_corner=profile.get("weakest_recent_corner"),
                biggest_recent_improvement=profile.get("biggest_improvement"),
                danger_zones_today=danger_today,
                goal=goal,
                session_id=sid_param,
            )
        except TypeError:
            # Older coach signature without session_id (test stubs etc.)
            result = _coach.brief(
                driver_id=driver_id,
                today_iso=today,
                weather_phase=weather_phase.id,
                surface_state=weather_phase.surface_state,
                markers_selected=markers_selected,
                weakest_recent_corner=profile.get("weakest_recent_corner"),
                biggest_recent_improvement=profile.get("biggest_improvement"),
                danger_zones_today=danger_today,
                goal=goal,
            )
        # New shape (3-tuple) for emotion-aware coaches; old shape (2-tuple) tolerated.
        if len(result) == 3:
            narrative, focus, emotion = result
        else:
            narrative, focus = result
    else:
        narrative, focus = "", markers_selected[:3]

    return jsonify({
        "driver_id":               driver_id,
        "date":                    today,
        "weather_phase":           weather_phase.id,
        "surface_state":           weather_phase.surface_state,
        "weather_note":            weather_phase.coaching_note,
        "weakest_recent_corner":   profile.get("weakest_recent_corner"),
        "biggest_recent_improvement": profile.get("biggest_improvement"),
        "danger_zones_today":      danger_today,
        "narrative_md":            narrative,
        "focus":                   focus,
        "emotion":                 emotion,
    })


@app.route("/driver/<driver_id>/profile", methods=["GET"])
def driver_profile_route(driver_id: str):
    if not HAS_ANALYZER or not HAS_DUCKDB:
        return jsonify({"error": "driver profile unavailable"}), 503
    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        try:
            profile = compute_profile(conn, driver_id)
        finally:
            conn.close()
    return jsonify(profile)


# ── Markers, danger zones, weather (pure-data endpoints for the frontend) ───

@app.route("/session/<sid>/capabilities", methods=["GET"])
def session_capabilities_get(sid: str):
    """ADR-015 Phase 3: per-session capability envelope.

    Returns the signals this session has, at what mean rate, with a `useful`
    flag against `signal_registry.min_useful_hz`. Frontend hits this once at
    session-load to drive its widget tray; the coach engine intersects the
    list with each rule's `requires` (Phase 4).
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        rows = conn.execute(
            """SELECT sr.name, sc.n_samples, sc.mean_hz, sr.min_useful_hz,
                      sc.t_start, sc.t_end
               FROM session_capabilities sc
               JOIN signal_registry sr USING(signal_id)
               WHERE sc.session_id = ?
               ORDER BY sr.name""",
            [sid],
        ).fetchall()
        conn.close()
    if not rows:
        return jsonify({"error": "session not found", "session_id": sid}), 404
    signals = []
    caps_by_name: dict = {}
    t_starts: list = []
    t_ends: list = []
    for name, n_samples, mean_hz, min_useful_hz, t_start, t_end in rows:
        useful = (min_useful_hz is None) or (float(mean_hz) >= float(min_useful_hz))
        signals.append({
            "name":      name,
            "n_samples": int(n_samples),
            "mean_hz":   float(mean_hz),
            "useful":    bool(useful),
        })
        caps_by_name[name] = {"mean_hz": float(mean_hz), "useful": bool(useful)}
        t_starts.append(float(t_start))
        t_ends.append(float(t_end))
    duration_s = (max(t_ends) - min(t_starts)) if t_starts else 0.0

    available: list = []
    disabled: list = []
    try:
        from coach_engine import evaluate_coach_gating
        available, disabled = evaluate_coach_gating(caps_by_name)
    except ImportError:
        pass

    return jsonify({
        "session_id":        sid,
        "duration_s":        duration_s,
        "signals":           signals,
        "coaches_available": available,
        "coaches_disabled":  disabled,
    })


@app.route("/session/<sid>/signals", methods=["GET"])
def session_signals_get(sid: str):
    """ADR-015 Phase 3: query-time synchroniser.

    Query params:
        names    comma-separated signal names (required)
        axis     'gps' (default) | 'lap_distance' | <signal_name>
        rate_hz  resample to this rate; 0 (default) = axis-native
        interp   'hold' (default, ASOF) | 'lerp' (linear bracketing)
        t_from   epoch seconds — clip lower bound
        t_to     epoch seconds — clip upper bound

    Signals not present in the session come back as null columns alongside a
    `missing` list; the response still 200s. 400 for unknown names/axes,
    404 for sessions with no telemetry at all.
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503

    names_param = request.args.get("names", "") or ""
    names = [n.strip() for n in names_param.split(",") if n.strip()]
    if not names:
        return jsonify({"error": "names is required (comma-separated)"}), 400

    axis = (request.args.get("axis") or "gps").strip()
    interp_kind = (request.args.get("interp") or "hold").strip().lower()
    if interp_kind not in ("hold", "lerp"):
        return jsonify({"error": "interp must be 'hold' or 'lerp'"}), 400

    try:
        rate_hz = float(request.args.get("rate_hz") or 0)
    except ValueError:
        return jsonify({"error": "rate_hz must be a number"}), 400
    if rate_hz < 0:
        return jsonify({"error": "rate_hz must be >= 0"}), 400

    try:
        t_from = request.args.get("t_from")
        t_to   = request.args.get("t_to")
        t_from = float(t_from) if t_from not in (None, "") else None
        t_to   = float(t_to)   if t_to   not in (None, "") else None
    except ValueError:
        return jsonify({"error": "t_from/t_to must be numbers"}), 400

    if request.args.get("lap") is not None:
        return jsonify({"error": "lap clipping not yet implemented (Phase 4)"}), 400

    AXIS_KEYWORDS = {"gps", "time", "t", "lap_distance"}

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503

        n_wide = conn.execute(
            "SELECT COUNT(*) FROM telemetry WHERE session_id = ?", [sid],
        ).fetchone()[0]
        n_tall = conn.execute(
            "SELECT COUNT(*) FROM telemetry_signals WHERE session_id = ?", [sid],
        ).fetchone()[0]
        if not n_wide and not n_tall:
            conn.close()
            return jsonify({"error": "session not found", "session_id": sid}), 404

        for nm in names:
            if nm in _WIDE_SIGNAL_NAMES:
                continue
            r = conn.execute(
                "SELECT 1 FROM signal_registry WHERE name = ?", [nm],
            ).fetchone()
            if r is None:
                conn.close()
                return jsonify({"error": f"unknown signal: {nm}"}), 400

        if axis not in AXIS_KEYWORDS and axis not in _WIDE_SIGNAL_NAMES:
            r = conn.execute(
                "SELECT 1 FROM signal_registry WHERE name = ?", [axis],
            ).fetchone()
            if r is None:
                conn.close()
                return jsonify({"error": f"unknown axis signal: {axis}"}), 400

        # Default the window to the union of the session's signal coverage.
        if t_from is None or t_to is None:
            bounds: list = []
            r = conn.execute(
                "SELECT MIN(timestamp), MAX(timestamp) FROM telemetry "
                "WHERE session_id = ?", [sid],
            ).fetchone()
            if r and r[0] is not None:
                bounds.append((float(r[0]), float(r[1])))
            r = conn.execute(
                "SELECT MIN(t), MAX(t) FROM telemetry_signals "
                "WHERE session_id = ?", [sid],
            ).fetchone()
            if r and r[0] is not None:
                bounds.append((float(r[0]), float(r[1])))
            if bounds:
                if t_from is None:
                    t_from = min(b[0] for b in bounds)
                if t_to is None:
                    t_to = max(b[1] for b in bounds)

        signal_data = {nm: _read_signal(conn, sid, nm, t_from, t_to) for nm in names}

        if rate_hz > 0:
            if t_from is None or t_to is None or t_to <= t_from:
                conn.close()
                return jsonify({"error": "rate_hz>0 requires a non-empty time window"}), 400
            step = 1.0 / rate_hz
            axis_ts: list = []
            t = t_from
            while t <= t_to + 1e-9:
                axis_ts.append(t)
                t += step
        elif axis in AXIS_KEYWORDS:
            sql = ("SELECT DISTINCT timestamp FROM telemetry "
                   "WHERE session_id = ?")
            params: list = [sid]
            if t_from is not None:
                sql += " AND timestamp >= ?"
                params.append(t_from)
            if t_to is not None:
                sql += " AND timestamp <= ?"
                params.append(t_to)
            sql += " ORDER BY timestamp"
            axis_ts = [float(r[0]) for r in conn.execute(sql, params).fetchall()]
        else:
            ax_data = _read_signal(conn, sid, axis, t_from, t_to)
            axis_ts = [d[0] for d in ax_data]

        distance_at = None
        if axis == "lap_distance" and axis_ts:
            dist_data = _read_signal(conn, sid, "distance_m", t_from, t_to)
            distance_at = _interp_hold(axis_ts, dist_data)

        conn.close()

    values_by_name: dict = {}
    missing: list = []
    for nm in names:
        data = signal_data[nm]
        if not data:
            values_by_name[nm] = [None] * len(axis_ts)
            missing.append(nm)
        else:
            values_by_name[nm] = _interp(axis_ts, data, interp_kind)

    rows_out = []
    for k, at in enumerate(axis_ts):
        row = {"t": at}
        if axis == "lap_distance" and distance_at is not None:
            row["distance_m"] = distance_at[k]
        for nm in names:
            row[nm] = values_by_name[nm][k]
        rows_out.append(row)

    return jsonify({
        "session_id": sid,
        "axis":       axis,
        "rate_hz":    rate_hz,
        "interp":     interp_kind,
        "t_from":     t_from,
        "t_to":       t_to,
        "names":      names,
        "rows":       rows_out,
        "missing":    missing,
        "count":      len(rows_out),
    })


@app.route("/session/<sid>/signals", methods=["POST"])
def session_signals_post(sid: str):
    """ADR-015: append (name, t, value) tuples to telemetry_signals.

    Body shape:
        {"signals": [
            {"name": "oil_temp_c", "t": 1714316103.0, "value": 92.1},
            {"name": "clutch_pos_pct", "t": 1714316103.05, "value": 23.4}
        ]}

    Either `name` (string) or `signal_id` (integer) is required. Novel names
    auto-register with units=NULL, discovery='discovered'. The session's
    capabilities are recomputed at the end of every batch.
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    body = request.get_json(force=True, silent=True) or {}
    items = body.get("signals") or []
    if not items:
        return jsonify({"error": "no signals"}), 400

    names_seen: set[str] = set()
    discovered: list[str] = []
    rows_written = 0

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503

        # Resolve all unique names to signal_ids in one pre-pass; novel ones
        # are auto-registered as 'discovered'.
        name_to_id: dict[str, int] = {}
        for it in items:
            nm = it.get("name")
            if nm is None or nm in name_to_id:
                continue
            existed = conn.execute(
                "SELECT 1 FROM signal_registry WHERE name = ?", [nm],
            ).fetchone()
            sid_id = _resolve_signal_id(conn, nm)
            name_to_id[nm] = sid_id
            if not existed:
                discovered.append(nm)

        rows = []
        for it in items:
            nm = it.get("name")
            sig_id = it.get("signal_id") if nm is None else name_to_id.get(nm)
            t = it.get("t")
            v = it.get("value")
            if sig_id is None or t is None or v is None:
                continue
            try:
                rows.append((sid, int(sig_id), float(t), float(v)))
            except (TypeError, ValueError):
                continue
            if nm:
                names_seen.add(nm)

        if not rows:
            conn.close()
            return jsonify({"error": "no valid samples (need name|signal_id, t, value)"}), 400

        conn.executemany(
            """INSERT INTO telemetry_signals VALUES (?, ?, ?, ?)
               ON CONFLICT (session_id, signal_id, t) DO UPDATE SET
                   value = excluded.value""",
            rows,
        )
        rows_written = len(rows)
        conn.close()

    n_caps = _compute_capabilities(sid)

    return jsonify({
        "saved":              True,
        "session_id":         sid,
        "n_appended":         rows_written,
        "names":              sorted(names_seen),
        "newly_discovered":   discovered,
        "capabilities_count": n_caps,
    })


@app.route("/session/<sid>/capabilities/recompute", methods=["POST"])
def session_capabilities_recompute(sid: str):
    """Trigger _compute_capabilities for a session — useful after bulk import."""
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    n = _compute_capabilities(sid)
    return jsonify({"session_id": sid, "capabilities_count": n})


@app.route("/signals/registry", methods=["GET"])
def signals_registry():
    """ADR-015: full signal catalog. Frontend caches once at app launch.

    Query params:
        include_can_state    if 'true', include a `can_state` block with
                             the live CAN reader's interface, channel,
                             frames/s (rolling 5 s), unknown CAN IDs,
                             and loaded status. Pit Stall Setup uses this.
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        rows = conn.execute(
            """SELECT signal_id, name, units, semantics, "group",
                      expected_hz, min_useful_hz, discovery, obd2_pid
               FROM signal_registry
               ORDER BY "group", name"""
        ).fetchall()
        conn.close()
    signals = [
        {"signal_id":     r[0], "name":          r[1], "units":     r[2],
         "semantics":     r[3], "group":         r[4], "expected_hz": r[5],
         "min_useful_hz": r[6], "discovery":     r[7], "obd2_pid":  r[8]}
        for r in rows
    ]
    body = {"count": len(signals), "signals": signals}
    if (request.args.get("include_can_state") or "").lower() == "true":
        body["can_state"] = _can_state_snapshot()
    return jsonify(body)


@app.route("/diagnostics/llm_friction", methods=["GET"])
def diagnostics_llm_friction():
    """ADR-018: surface LitertCoach edge friction.

    Every brief/debrief lands a row in `llm_friction`; this endpoint serves
    raw rows + aggregates so the Pit Stall Setup screen can show whether
    Gemma is degrading (latency creep, truncations, fallbacks) before it
    bites mid-session.

    Query params:
        session_id     (optional)  filter to one session
        role           (optional)  brief | cue | debrief
        since_minutes  (optional)  only rows from the last N minutes
        limit          (default 100, max 1000)
    """
    if not HAS_DUCKDB:
        return jsonify({"error": "duckdb not available"}), 503
    sid = (request.args.get("session_id") or "").strip()
    role = (request.args.get("role") or "").strip()
    try:
        limit = max(1, min(int(request.args.get("limit", 100)), 1000))
    except ValueError:
        limit = 100
    try:
        since_min = float(request.args.get("since_minutes", 0) or 0)
    except ValueError:
        since_min = 0.0

    where = []
    params: list = []
    if sid:
        where.append("session_id = ?")
        params.append(sid)
    if role:
        where.append("role = ?")
        params.append(role)
    if since_min > 0:
        where.append("ts >= now() - INTERVAL (?) MINUTE")
        params.append(since_min)
    where_sql = (" WHERE " + " AND ".join(where)) if where else ""

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"error": "duckdb not available"}), 503
        try:
            rows = conn.execute(
                f"""SELECT id, session_id, role, mode, backend,
                          prompt_chars, completion_chars, latency_ms,
                          truncated, fell_back, error, emotion, ts
                   FROM llm_friction
                   {where_sql}
                   ORDER BY ts DESC
                   LIMIT ?""",
                [*params, limit],
            ).fetchall()
            agg_overall = conn.execute(
                f"""SELECT
                       COUNT(*),
                       quantile_cont(latency_ms, 0.5),
                       quantile_cont(latency_ms, 0.95),
                       AVG(CASE WHEN error IS NOT NULL AND error <> '' THEN 1.0 ELSE 0.0 END),
                       AVG(CASE WHEN fell_back THEN 1.0 ELSE 0.0 END),
                       AVG(CASE WHEN truncated THEN 1.0 ELSE 0.0 END)
                   FROM llm_friction
                   {where_sql}""",
                params,
            ).fetchone()
            agg_by_role = conn.execute(
                f"""SELECT role, COUNT(*),
                          quantile_cont(latency_ms, 0.5),
                          AVG(CASE WHEN fell_back THEN 1.0 ELSE 0.0 END)
                   FROM llm_friction
                   {where_sql}
                   GROUP BY role
                   ORDER BY role""",
                params,
            ).fetchall()
        finally:
            conn.close()

    out_rows = [
        {"id": r[0], "session_id": r[1], "role": r[2], "mode": r[3],
         "backend": r[4], "prompt_chars": r[5], "completion_chars": r[6],
         "latency_ms": float(r[7]) if r[7] is not None else 0.0,
         "truncated": bool(r[8]), "fell_back": bool(r[9]),
         "error": r[10] or "", "emotion": r[11] or "",
         "ts": str(r[12]) if r[12] is not None else ""}
        for r in rows
    ]
    n, p50, p95, err_rate, fb_rate, trunc_rate = agg_overall or (0, None, None, 0, 0, 0)
    return jsonify({
        "count": int(n or 0),
        "p50_latency_ms": float(p50) if p50 is not None else None,
        "p95_latency_ms": float(p95) if p95 is not None else None,
        "error_rate":      float(err_rate or 0.0),
        "fallback_rate":   float(fb_rate or 0.0),
        "truncation_rate": float(trunc_rate or 0.0),
        "by_role": [
            {"role": r[0], "count": int(r[1]),
             "p50_latency_ms": float(r[2]) if r[2] is not None else None,
             "fallback_rate":  float(r[3] or 0.0)}
            for r in agg_by_role
        ],
        "rows": out_rows,
    })


def _can_state_snapshot() -> dict:
    """Snapshot for the Pit Stall Setup screen.

    Includes:
      - reader runtime state (loaded / connected / fps / last-frame age)
      - USB-CAN device enumeration (which physical adapters are plugged
        into the host right now, even if no reader is bound to them)

    Empty placeholder fields when no reader is active so the PWA can
    render explicit ✗ rows.
    """
    if _can_reader is None:
        reader_state = {
            "loaded":            False,
            "connected":         False,
            "interface":         None,
            "channel":           None,
            "bitrate":           None,
            "session_id":        None,
            "frames_total":      0,
            "frames_unknown":    0,
            "frames_per_second": 0.0,
            "last_frame_age_s":  None,
            "unknown_ids":       [],
        }
    else:
        reader_state = _can_reader.state()

    # Always probe USB devices — the PWA wants to know about adapters
    # whether or not the bridge has bound to one yet (e.g. user just
    # plugged in a CANable but hasn't started the reader).
    reader_state["usb_devices"] = _detect_usb_can_devices()
    return reader_state


# Vendor:Product → adapter model. Used to label detected USB devices.
# VID/PID list compiled from python-can / community references; expand as
# needed. Lower-cased hex with no 0x prefix.
_USB_CAN_DEVICE_DB: dict[tuple[str, str], dict] = {
    # CANable / canable.io, CANtact-clone family running slcan firmware
    ("1d50", "606f"): {"model": "CANable / OpenLink", "kind": "slcan"},
    ("1d50", "604b"): {"model": "Korlan USB2CAN",     "kind": "slcan"},
    # Macchina M2 — runs custom firmware exposing serial
    ("2341", "8051"): {"model": "Macchina M2",        "kind": "slcan"},
    # PEAK PCAN-USB
    ("0c72", "000c"): {"model": "PEAK PCAN-USB",      "kind": "pcan"},
    # Kvaser USBcan
    ("0bfd", "0117"): {"model": "Kvaser USBcan",      "kind": "kvaser"},
    # Generic FTDI-based ELM327 dongles (treated as serial; not full CAN)
    ("0403", "6001"): {"model": "FTDI USB-serial (ELM327?)", "kind": "obd2"},
    # CH340-based clones
    ("1a86", "7523"): {"model": "CH340 USB-serial (clone)",   "kind": "slcan"},
}


def _detect_usb_can_devices() -> list[dict]:
    """Enumerate currently-connected USB serial devices that look like
    CAN-bus adapters. Returns one dict per device — empty list if none
    detected or pyserial isn't installed.

    The PWA's Pit Stall Setup uses this to:
      - Tell the driver *which* adapter is plugged in (model name)
      - Confirm the cable is physically present even when the bridge
        wasn't started with --can-channel
      - Suggest the right `--can-channel` value if reader isn't running
    """
    try:
        from serial.tools import list_ports
    except ImportError:
        return []

    out: list[dict] = []
    for p in list_ports.comports():
        vid = f"{p.vid:04x}" if p.vid else None
        pid = f"{p.pid:04x}" if p.pid else None
        match = _USB_CAN_DEVICE_DB.get((vid, pid)) if vid and pid else None
        # Skip devices that aren't likely USB-CAN — but keep entries for
        # any FTDI / CDC ACM device since they could be undocumented adapters.
        likely_can = bool(match) or (
            p.device.startswith(("/dev/ttyACM", "/dev/ttyUSB"))
            or "ACM" in (p.device or "")
        )
        if not likely_can:
            continue
        out.append({
            "device":       p.device,
            "vid":          f"0x{vid}" if vid else None,
            "pid":          f"0x{pid}" if pid else None,
            "description":  p.description or "",
            "manufacturer": p.manufacturer or "",
            "model":        match["model"] if match else "Unknown serial device",
            "kind":         match["kind"]  if match else "unknown",
            "is_known":     bool(match),
        })
    return out


@app.route("/track/markers", methods=["GET"])
def track_markers():
    """All Sonoma markers (id, kind, label, distance, lat, lon, corner)."""
    track_path = os.path.abspath(os.path.join(
        SIM_DIR, "..", "..", "data", "tracks", "sonoma.json",
    ))
    try:
        with open(track_path) as f:
            data = json.load(f)
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    return jsonify({"track": data.get("name", "Sonoma Raceway"),
                    "markers": data.get("markers", [])})


@app.route("/track/danger_zones", methods=["GET"])
def track_danger_zones():
    return jsonify({
        "track": "Sonoma Raceway",
        "danger_zones": [
            {"id": d.id, "start_m": d.start_m, "end_m": d.end_m,
             "description": d.description, "severity": d.severity}
            for d in sonoma.DANGER_ZONES
        ],
    })


@app.route("/track/weather", methods=["GET"])
def track_weather():
    try:
        hour_local = int(request.args.get("hour_local", datetime.now().hour))
    except ValueError:
        hour_local = 12
    ph = sonoma.weather_phase_for_hour(hour_local)
    return jsonify({
        "hour_local":     hour_local,
        "phase":          ph.id,
        "surface_state":  ph.surface_state,
        "coaching_note":  ph.coaching_note,
    })


@app.route("/laps", methods=["GET"])
def get_laps():
    session_id = request.args.get("session_id")
    limit = int(request.args.get("limit", 20))

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"laps": [], "error": "duckdb not available"})
        query = "SELECT * FROM laps"
        params = []
        if session_id:
            query += " WHERE session_id = ?"
            params.append(session_id)
        query += f" ORDER BY recorded_at DESC LIMIT {limit}"
        rows = conn.execute(query, params).fetchall()
        cols = [d[0] for d in conn.description]
        conn.close()

    return jsonify({"laps": [dict(zip(cols, r)) for r in rows], "count": len(rows)})


@app.route("/lap", methods=["POST"])
def save_lap():
    data = request.get_json(force=True, silent=True) or {}
    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"saved": False, "error": "duckdb not available"})
        conn.execute("""
            INSERT INTO laps
                (session_id, lap_number, lap_time_s, best_sector,
                 avg_speed_kmh, max_combo_g, coast_pct)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, [
            data.get("session_id", ""),  data.get("lap_number", 0),
            data.get("lap_time_s", 0),   data.get("best_sector", 0),
            data.get("avg_speed_kmh", 0),data.get("max_combo_g", 0),
            data.get("coast_pct", 0),
        ])
        conn.close()
    return jsonify({"saved": True})


# ── CAN reader hookup (ADR-015 + ADR-016) ──────────────────────────────────

# Module-level reference so a shutdown hook can stop the reader cleanly.
_can_reader = None


def _start_can_reader(*, session_id: str, interface: str, channel: str,
                      bitrate: int, dbc_paths: list, flush_ms: int):
    """Start a CanReader thread that pumps CAN frames into pitwall's stores.

    Called from the bridge entrypoint when --can-channel is set. Returns the
    reader (or None if can_reader couldn't be imported / bus failed to open).
    """
    global _can_reader
    try:
        from can_reader import CanReader  # noqa: F401
    except ImportError as e:
        print(f"⚠  CAN reader unavailable ({e}); install python-can + cantools")
        return None

    try:
        from can_reader import CanReader
        _can_reader = CanReader(
            session_id=session_id,
            interface=interface,
            channel=channel,
            bitrate=bitrate,
            dbc_paths=dbc_paths,
            flush_ms=flush_ms,
            bridge=sys.modules[__name__],
        )
        _can_reader.start()
        print(f"✓  CAN reader started (interface={interface} "
              f"channel={channel} session={session_id})")
        return _can_reader
    except Exception as e:
        print(f"⚠  CAN reader failed to start: {e}")
        return None


# ── Entrypoint ─────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Pitwall Bridge Server")
    parser.add_argument("--track", default=os.path.join(SIM_DIR, "sonoma.json"),
                        help="Path to track JSON (default: src/simulator/sonoma.json)")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--can-channel", default=None,
                        help="Start CAN reader on this channel (e.g. 'pitwall_dev', "
                             "'/dev/ttyACM0', 'vcan0'). When unset, no reader runs.")
    parser.add_argument("--can-interface", default="virtual",
                        help="python-can interface (virtual | socketcan | slcan | "
                             "pcan | kvaser …). Default: virtual.")
    parser.add_argument("--can-bitrate", type=int, default=500_000,
                        help="Bitrate for slcan/socketcan/pcan/kvaser interfaces.")
    parser.add_argument("--can-dbc", action="append", default=None,
                        help="DBC file(s) to load for decoding. Repeat to layer "
                             "multiple. Default: data/dbc/pitwall.dbc")
    parser.add_argument("--can-session-id", default=None,
                        help="session_id to tag CAN-ingested rows with. "
                             "Default: auto-generated from track + UTC stamp.")
    parser.add_argument("--can-flush-ms", type=int, default=100,
                        help="Wide-table flush cadence in ms (default 100 = 10 Hz).")
    args = parser.parse_args()

    if HAS_SONIC and os.path.exists(args.track):
        try:
            _track = load_track(args.track)
            print(f"✓  Track: {_track.name} ({len(_track.corners)} corners)")
        except Exception as e:
            print(f"⚠  Track load failed: {e}")

    if HAS_DUCKDB:
        try:
            n = seed_signal_registry()
            print(f"✓  signal_registry seeded ({n} entries from {os.path.relpath(REGISTRY_SEED_PATH)})")
        except Exception as e:
            print(f"⚠  signal_registry seed skipped: {e}")

    # Optional CAN reader — when --can-channel is provided, spawn a thread
    # that consumes the bus and ingests into the same DuckDB this bridge owns.
    # Default session_id is `_live` (the synthetic placeholder for "no real
    # session in progress yet, but the bridge needs to surface live values").
    # PWA's Pit Stall Setup screen polls /session/_live/signals at 5 Hz.
    if args.can_channel:
        sid = args.can_session_id or "_live"
        if sid == "_live":
            # Reset the _live session each bridge boot so stale values from
            # a prior run don't pollute Pit Stall Setup's view.
            _reset_live_session()
        _ensure_session_row(
            sid,
            track=(_track.name if _track else None),
            note="auto-created by bridge --can-channel"
                 + (" (live placeholder)" if sid == "_live" else ""),
        )
        _start_can_reader(
            session_id=sid,
            interface=args.can_interface,
            channel=args.can_channel,
            bitrate=args.can_bitrate,
            dbc_paths=args.can_dbc,
            flush_ms=args.can_flush_ms,
        )
        print(f"    CAN session: {sid}")

    port = args.port
    print(f"\n🏁  Pitwall Bridge v2 on http://127.0.0.1:{port}")
    print(f"    Engine: {'sonic_model (real cues)' if HAS_SONIC else 'rule fallback'}")
    print(f"    DuckDB: {'enabled → ' + DB_PATH if HAS_DUCKDB else 'disabled'}")
    if args.can_channel:
        print(f"    CAN:    {args.can_interface}/{args.can_channel}")
    print(f"\n    Emulator tunnel:")
    print(f"    ~/Library/Android/sdk/platform-tools/adb reverse tcp:{port} tcp:{port}\n")
    try:
        app.run(host="127.0.0.1", port=port, debug=False, threaded=True)
    finally:
        if _can_reader is not None:
            _can_reader.stop()
