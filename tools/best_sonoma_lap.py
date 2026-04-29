"""
Rank Sonoma VBO sessions by best lap time.

Walks a directory of .vbo files, filters to those whose GPS centroid is
within range of Sonoma's start/finish, detects S/F crossings to compute
per-lap times, and prints a ranked table by fastest lap.

Usage:
    python3 tools/best_sonoma_lap.py /path/to/vbo/dir
    python3 tools/best_sonoma_lap.py /path/to/vbo/dir --top 15
"""
from __future__ import annotations

import argparse
import json
import math
import os
import statistics
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src" / "simulator"))

from vbo_parser import parse_vbo  # noqa: E402

EARTH_R = 6_371_000.0


def haversine_m(a_lat: float, a_lon: float, b_lat: float, b_lon: float) -> float:
    p1, p2 = math.radians(a_lat), math.radians(b_lat)
    dlat = math.radians(b_lat - a_lat)
    dlon = math.radians(b_lon - a_lon)
    h = math.sin(dlat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlon / 2) ** 2
    return 2 * EARTH_R * math.asin(math.sqrt(h))


def best_and_all_laps(
    frames,
    sf_lat: float,
    sf_lon: float,
    radius_m: float = 25.0,
    cooldown_s: float = 30.0,
    min_lap_s: float = 60.0,
    max_lap_s: float = 300.0,
) -> list[float]:
    """Return all lap times (seconds) detected by S/F crossings.

    A crossing fires when frame distance-to-SF transitions from outside
    `radius_m` to inside it. `cooldown_s` blocks double-triggers; laps
    outside [min_lap_s, max_lap_s] are dropped as outliers.
    """
    laps: list[float] = []
    inside = False
    last_cross_t: float | None = None
    last_t = None

    for f in frames:
        if not f.lat and not f.lon:
            continue
        d = haversine_m(f.lat, f.lon, sf_lat, sf_lon)
        was_inside = inside
        inside = d < radius_m

        if inside and not was_inside:
            t = f.timestamp
            if last_cross_t is not None:
                gap = t - last_cross_t
                if gap >= cooldown_s:
                    if min_lap_s <= gap <= max_lap_s:
                        laps.append(gap)
                    last_cross_t = t
            else:
                last_cross_t = t
        last_t = f.timestamp

    return laps


def session_centroid(frames) -> tuple[float, float]:
    lats = [f.lat for f in frames if f.lat]
    lons = [f.lon for f in frames if f.lon]
    if not lats:
        return 0.0, 0.0
    return statistics.median(lats), statistics.median(lons)


def fmt_lap(s: float) -> str:
    if not s or s != s:
        return "       —"
    m = int(s // 60)
    rest = s - 60 * m
    return f"{m:d}:{rest:05.2f}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("vbo_dir", help="Directory containing .vbo files")
    ap.add_argument("--track", default=str(ROOT / "data" / "tracks" / "sonoma.json"))
    ap.add_argument("--centroid-radius-km", type=float, default=10.0,
                    help="Session centroid must be within this many km of S/F to count as Sonoma")
    ap.add_argument("--top", type=int, default=20)
    ap.add_argument("--quiet", action="store_true",
                    help="Suppress per-file progress output")
    args = ap.parse_args()

    track = json.loads(Path(args.track).read_text())
    sf = track["start_finish"]
    sf_lat, sf_lon = float(sf["lat"]), float(sf["lon"])
    track_name = track.get("name", "track")
    print(f"Track: {track_name}  S/F=({sf_lat:.5f}, {sf_lon:.5f})  expected length={track.get('track_length_m','?')}m")

    vbo_dir = Path(args.vbo_dir).expanduser()
    vbos = sorted(vbo_dir.glob("*.vbo"))
    print(f"Scanning {len(vbos)} VBO files in {vbo_dir}\n")

    rows = []
    for path in vbos:
        try:
            _, frames = parse_vbo(path)
        except Exception as e:
            if not args.quiet:
                print(f"  SKIP  {path.name}  parse error: {e}")
            continue
        if len(frames) < 50:
            continue
        c_lat, c_lon = session_centroid(frames)
        cdist_km = haversine_m(c_lat, c_lon, sf_lat, sf_lon) / 1000.0
        if cdist_km > args.centroid_radius_km:
            continue
        laps = best_and_all_laps(frames, sf_lat, sf_lon)
        if not laps:
            continue
        rows.append({
            "file": path.name,
            "frames": len(frames),
            "duration_min": (frames[-1].timestamp - frames[0].timestamp) / 60.0,
            "centroid_km_from_sf": cdist_km,
            "n_laps": len(laps),
            "best_s": min(laps),
            "median_s": statistics.median(laps),
            "p25_s": sorted(laps)[max(0, len(laps) // 4)],
            "spread_s": (max(laps) - min(laps)) if len(laps) > 1 else 0.0,
            "max_speed_kmh": max(f.speed for f in frames) * 3.6,
            "max_brake_bar": max(f.brake_pressure for f in frames),
            "max_glat_g": max(abs(f.g_lat) for f in frames),
        })

    if not rows:
        print("No Sonoma sessions detected. Check --track and --centroid-radius-km.")
        return

    rows.sort(key=lambda r: r["best_s"])
    print(f"Found {len(rows)} Sonoma session(s) with detectable laps. Ranked by BEST LAP:\n")
    print(f"{'rank':>4}  {'file':<40} {'laps':>4}  {'best':>8}  {'p25':>8}  {'median':>8}  "
          f"{'spread':>6}  {'minutes':>7}  {'topkmh':>6}  {'maxbar':>6}  {'maxgL':>5}")
    print("  " + "─" * 132)
    for i, r in enumerate(rows[:args.top], 1):
        print(f"{i:>4}  {r['file']:<40} {r['n_laps']:>4}  "
              f"{fmt_lap(r['best_s']):>8}  {fmt_lap(r['p25_s']):>8}  {fmt_lap(r['median_s']):>8}  "
              f"{r['spread_s']:>5.1f}s  {r['duration_min']:>6.1f}m  "
              f"{r['max_speed_kmh']:>6.1f}  {r['max_brake_bar']:>6.1f}  {r['max_glat_g']:>5.2f}")

    print()
    winner = rows[0]
    print(f"BEST: {winner['file']}  best lap {fmt_lap(winner['best_s'])}  "
          f"({winner['n_laps']} laps, median {fmt_lap(winner['median_s'])}, "
          f"spread {winner['spread_s']:.1f}s)")
    print()
    print("Tip: a low BEST + low MEDIAN + low SPREAD is a clean fast session.")
    print("     A low BEST with high MEDIAN/SPREAD is one hot lap inside an inconsistent run.")


if __name__ == "__main__":
    main()
