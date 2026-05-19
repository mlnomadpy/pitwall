"""pitwall.features.session.laps — lap detection, sector splitting, quantile.

Pure domain logic with no Flask dependency. Can be unit-tested independently.

Three strategies for `detect_laps`, picked by data shape:
  1. Cumulative distance — real Racelogic VBOs (monotonic distance_m).
  2. Distance wraparound — synthetic per-lap data (distance resets at S/F).
  3. GPS perpendicular S/F crossing — fallback using track JSON start_finish.
"""

import json
import math
import os
from datetime import datetime, timezone

from pitwall.state import state, SIM_DIR
from pitwall.db import db_conn, DuckDbUnavailable


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
    try:
        with db_conn() as conn:
            rows = conn.execute(
                "SELECT timestamp, distance_m, lat, lon "
                "FROM telemetry WHERE session_id = ? "
                "ORDER BY frame_idx",
                [sid],
            ).fetchall()
    except DuckDbUnavailable:
        return []
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
    try:
        with db_conn() as conn:
            rows = conn.execute(
                "SELECT timestamp, distance_m FROM telemetry "
                "WHERE session_id = ? AND timestamp >= ? AND timestamp <= ? "
                "ORDER BY timestamp",
                [sid, lap["t_start"], lap["t_end"]],
            ).fetchall()
    except DuckDbUnavailable:
        return []
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


# ── Session id ─────────────────────────────────────────────────────────────────

def new_session_id(track_name: str | None = None) -> str:
    """Stable session id derived from the track + UTC stamp.

    Used by /coach/debrief and /session/import when the caller doesn't
    supply their own session_id.
    """
    slug = (track_name or "session").lower().replace(" ", "-")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    return f"{slug}-{stamp}"
