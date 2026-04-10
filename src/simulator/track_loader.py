"""
Track Loader — loads track.json files and provides runtime corner/sector lookup.
Replaces the hardcoded track_map.py with data-driven track definitions.
"""

import json
import math
from dataclasses import dataclass
from pathlib import Path


@dataclass
class CornerDef:
    name: str
    number: int
    direction: str
    severity: int
    entry_distance: float
    apex_distance: float
    exit_distance: float
    curvature_peak: float
    entry_speed: float      # m/s
    apex_speed: float       # m/s
    exit_speed: float       # m/s
    brake_distance: float   # meters before entry where braking starts
    brake_pressure: float   # typical peak bar
    elevation_change: float # meters


@dataclass
class SectorDef:
    name: str
    start_distance: float
    end_distance: float


@dataclass
class TrackMap:
    name: str
    track_length: float
    sf_lat: float
    sf_lon: float
    sf_heading: float
    corners: list[CornerDef]
    sectors: list[SectorDef]
    elevation_profile: list[tuple[float, float]]  # (distance, altitude)
    reference_line: list[tuple[float, float, float]]  # (distance, lat, lon)


def load_track(path: object) -> TrackMap:
    """Load a track.json into a TrackMap for runtime use."""
    with open(path) as f:
        data = json.load(f)

    corners = []
    for c in data.get("corners", []):
        spd = c.get("typical_speed_ms", {})
        brk = c.get("typical_brake_point", {})
        corners.append(CornerDef(
            name=c["name"],
            number=c["number"],
            direction=c["direction"],
            severity=c["severity"],
            entry_distance=c["entry"]["distance"],
            apex_distance=c["apex"]["distance"],
            exit_distance=c["exit"]["distance"],
            curvature_peak=c.get("curvature_peak", 0),
            entry_speed=spd.get("entry", 0),
            apex_speed=spd.get("apex", 0),
            exit_speed=spd.get("exit", 0),
            brake_distance=brk.get("distance_before_entry", 0),
            brake_pressure=brk.get("pressure_bar", 0),
            elevation_change=c.get("elevation_change_m", 0),
        ))

    sectors = []
    for s in data.get("sectors", []):
        sectors.append(SectorDef(
            name=s["name"],
            start_distance=s["start_distance"],
            end_distance=s["end_distance"],
        ))

    sf = data.get("start_finish", {})
    elev = [(e["distance"], e["altitude"]) for e in data.get("elevation_profile", [])]
    ref = [(r["distance"], r["lat"], r["lon"]) for r in data.get("reference_line", [])]

    return TrackMap(
        name=data.get("name", "Unknown"),
        track_length=data.get("track_length_m", 0),
        sf_lat=sf.get("lat", 0),
        sf_lon=sf.get("lon", 0),
        sf_heading=sf.get("heading", 0),
        corners=corners,
        sectors=sectors,
        elevation_profile=elev,
        reference_line=ref,
    )


def find_nearest_corner(track: TrackMap, distance: float, lookahead: float = 500.0) -> object:
    """Find the next corner within lookahead distance."""
    track_dist = distance % track.track_length
    best = None
    best_gap = float("inf")

    for corner in track.corners:
        gap = (corner.entry_distance - track_dist) % track.track_length
        if 0 < gap < lookahead and gap < best_gap:
            best = corner
            best_gap = gap

    return best


def distance_to_corner(track: TrackMap, distance: float, corner: CornerDef) -> float:
    """Distance from current position to corner entry."""
    track_dist = distance % track.track_length
    return (corner.entry_distance - track_dist) % track.track_length


def is_in_corner(track: TrackMap, distance: float) -> object:
    """Check if current distance is inside any corner."""
    track_dist = distance % track.track_length
    for corner in track.corners:
        if corner.entry_distance <= track_dist <= corner.exit_distance:
            return corner
    return None


def is_past_apex(track: TrackMap, distance: float) -> object:
    """Check if we're past the apex of a corner (exit phase)."""
    track_dist = distance % track.track_length
    for corner in track.corners:
        if corner.apex_distance < track_dist <= corner.exit_distance:
            return corner
    return None


def get_sector(track: TrackMap, distance: float) -> object:
    """Get the sector for the current distance."""
    track_dist = distance % track.track_length
    for sector in track.sectors:
        if sector.start_distance <= track_dist < sector.end_distance:
            return sector
    return None


def get_elevation(track: TrackMap, distance: float) -> float:
    """Interpolate altitude at a given distance."""
    track_dist = distance % track.track_length
    if not track.elevation_profile:
        return 0.0

    # Find surrounding points
    for i in range(len(track.elevation_profile) - 1):
        d1, a1 = track.elevation_profile[i]
        d2, a2 = track.elevation_profile[i + 1]
        if d1 <= track_dist <= d2:
            t = (track_dist - d1) / (d2 - d1) if d2 > d1 else 0
            return a1 + t * (a2 - a1)

    return track.elevation_profile[-1][1]


def get_reference_position(track: TrackMap, distance: float) -> object:
    """Get the reference line lat/lon at a given distance (for cross-track error)."""
    track_dist = distance % track.track_length
    if not track.reference_line:
        return None

    for i in range(len(track.reference_line) - 1):
        d1, lat1, lon1 = track.reference_line[i]
        d2, lat2, lon2 = track.reference_line[i + 1]
        if d1 <= track_dist <= d2:
            t = (track_dist - d1) / (d2 - d1) if d2 > d1 else 0
            return (lat1 + t * (lat2 - lat1), lon1 + t * (lon2 - lon1))

    d, lat, lon = track.reference_line[-1]
    return (lat, lon)


def cross_track_error(track: TrackMap, distance: float, lat: float, lon: float) -> float:
    """Compute lateral offset from the reference line in meters."""
    ref = get_reference_position(track, distance)
    if ref is None:
        return 0.0

    ref_lat, ref_lon = ref
    R = 6371000
    dlat = math.radians(lat - ref_lat)
    dlon = math.radians(lon - ref_lon)
    a = math.sin(dlat / 2) ** 2 + math.cos(math.radians(ref_lat)) * math.cos(math.radians(lat)) * math.sin(dlon / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
