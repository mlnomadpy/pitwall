"""bridge.helpers — lap detection, frame transforms, and coaching helpers.

Pure domain logic with no Flask dependency. Can be unit-tested independently.
"""

import json
import math
import os
from datetime import datetime, timezone
from types import SimpleNamespace

from pitwall.state import state, SIM_DIR
from pitwall.db import get_db, WIDE_SIGNAL_NAMES


# ── Lap detection constants ────────────────────────────────────────────────────

LAP_MIN_S = 60.0
LAP_MAX_S = 300.0


# ── Lap detection strategies ──────────────────────────────────────────────────

def detect_laps(sid: str) -> list:
    """Detect complete laps from the wide telemetry table.

    Two strategies, tried in order:
      1. Distance wraparound — `distance_m` resets toward 0 after passing
         the track length. Works for synthetic per-lap data.
      2. GPS perpendicular S/F crossing — uses the loaded track's
         start_finish lat/lon/heading. Works for cumulative-distance data
         (real Racelogic VBO output).

    Lap times outside [60, 300] s are rejected as parser noise.
    """
    sonoma = state.sonoma
    if not state.has_duckdb:
        return []
    with state.db_lock:
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
        if LAP_MIN_S <= l["lap_time_s"] <= LAP_MAX_S:
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
    sonoma = state.sonoma
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
    sonoma = state.sonoma

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

    RADIAL_TOL_M = 50.0

    laps: list = []
    start_idx = None
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


# ── Sector splitting ──────────────────────────────────────────────────────────

def lap_sectors(sid: str, lap: dict) -> list:
    """Slice one lap into sonoma.SECTORS sub-spans by distance threshold."""
    sonoma = state.sonoma
    if not state.has_duckdb:
        return []
    with state.db_lock:
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

    def _lap_progress(d):
        """Convert raw cumulative distance to 0-1 lap progress fraction."""
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
            p = _lap_progress(d)
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


# ── Statistics ─────────────────────────────────────────────────────────────────

def quantile(sorted_vals: list, p: float) -> float:
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


# ── Track JSON helpers ─────────────────────────────────────────────────────────

def load_track_json(track_id: str) -> dict | None:
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


def corner_bounds_from_track(track: dict) -> list:
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


# ── Frame transforms ──────────────────────────────────────────────────────────

def frames_to_rows(session_id: str, frames) -> list:
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


def rows_to_frames(rows):
    """Reconstruct frame-shaped objects (SimpleNamespace) from DuckDB rows."""
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


def load_session_frames(sid: str):
    """Read all frames for a session from the telemetry table, ordered."""
    if not state.has_duckdb:
        return []
    with state.db_lock:
        conn = get_db()
        if conn is None:
            return []
        rows = conn.execute(
            "SELECT * FROM telemetry WHERE session_id = ? ORDER BY frame_idx",
            [sid],
        ).fetchall()
        conn.close()
    return rows_to_frames(rows)


# ── Coaching engine helpers ────────────────────────────────────────────────────

def cues_to_coaching(cues: list) -> str:
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


def sonic_coaching(burst: dict) -> tuple[str, list]:
    """Run the real sonic_model over the burst's representative frame data.
    Returns (coaching_text, serialised_cues).
    """
    if not state.has_sonic:
        return None, []

    frame = {
        "speed":             burst.get("avg_speed_kmh", 0) / 3.6,
        "g_lat":             burst.get("max_lateral_g", burst.get("max_combo_g", 0) * 0.7),
        "g_long":            burst.get("max_long_g",    burst.get("max_combo_g", 0) * 0.7),
        "combo_g":           burst.get("max_combo_g", 0),
        "brake_pressure":    burst.get("max_brake_bar", 0),
        "throttle":          burst.get("avg_throttle_pct", 0),
        "steering":          burst.get("avg_steering_deg", 0),
        "distance_to_corner": burst.get("dist_to_next_corner_m", 50),
        "corner_severity":   burst.get("next_corner_severity", 3),
        "past_apex":         burst.get("past_apex", False),
        "in_corner":         burst.get("in_corner", len(burst.get("corners_visited", [])) > 0),
    }

    corners = burst.get("corners_visited", [])
    if state.track and corners:
        nearest = state.find_nearest_corner(state.track, burst.get("distance_m", 0))
        if nearest:
            frame["distance_to_corner"] = state.distance_to_corner(
                state.track, burst.get("distance_m", 0), nearest
            )
            frame["corner_severity"] = nearest.severity

    cues = state.compute_cues(frame, prev_frame=None)
    coaching = cues_to_coaching(cues)

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


def rule_coaching(burst: dict) -> str:
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


def estimate_tts_ms(text: str) -> int:
    """Rough TTS duration estimate for the audio-ducker hint on cue events.

    150 ms/word matches Gemini-TTS's canonical T-Rod render rate; floored at
    800 ms so a one-word safety call ('Brake!') still ducks long enough for
    a clean handover over the continuous tactical tones.
    """
    if not text:
        return 0
    words = max(1, len(text.split()))
    return max(800, words * 150)


def new_session_id(track_name: str | None = None) -> str:
    """Stable session id derived from the track + UTC stamp.

    Used by /coach/debrief and /session/import when the caller doesn't
    supply their own session_id.
    """
    slug = (track_name or "session").lower().replace(" ", "-")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    return f"{slug}-{stamp}"


# ── Laps-or-400 shortcut ──────────────────────────────────────────────────────

def laps_or_400(sid: str):
    """Resolve laps for a session; returns (laps, error_response).

    Used by analysis endpoints. Returns Flask-compatible (jsonify, status_code)
    tuple as the error — callers just `return err`.
    """
    from flask import jsonify
    from pitwall.db import session_has_telemetry

    if not session_has_telemetry(sid):
        return None, (jsonify({"error": "session not found", "session_id": sid}), 404)
    laps = detect_laps(sid)
    if not laps:
        return None, (jsonify({
            "error":      "no complete laps detected",
            "session_id": sid,
        }), 400)
    return laps, None
