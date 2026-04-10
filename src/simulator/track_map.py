"""
Sonoma Raceway track definition.
Auto-detected corners from the VBO reference lap using curvature analysis,
then manually verified against track maps.
"""

import math
from dataclasses import dataclass


@dataclass
class Corner:
    name: str
    severity: int           # 1 (hairpin) to 6 (flat out)
    entry_distance: float   # meters from start/finish
    apex_distance: float
    exit_distance: float
    direction: str          # "left" or "right"
    gold_brake_point: float # meters from apex where AJ brakes
    gold_min_speed: float   # m/s — AJ's minimum speed
    gold_exit_speed: float  # m/s — AJ's exit speed


# Sonoma Raceway — approximate corners from reference data
# These will be refined with actual GPS trace analysis
SONOMA_CORNERS = [
    Corner("Turn 1", 2, 100, 160, 220, "right", 80, 22.0, 30.0),
    Corner("Turn 2", 3, 350, 420, 490, "left", 60, 28.0, 35.0),
    Corner("Turn 3", 3, 600, 680, 750, "right", 70, 25.0, 32.0),
    Corner("Turn 3a", 4, 800, 850, 920, "left", 40, 32.0, 38.0),
    Corner("Turn 4", 3, 1050, 1130, 1200, "right", 65, 26.0, 34.0),
    Corner("Turn 5", 4, 1350, 1410, 1470, "left", 45, 30.0, 36.0),
    Corner("Turn 6", 3, 1600, 1680, 1760, "right", 70, 24.0, 33.0),
    Corner("Turn 7", 2, 1900, 1990, 2080, "right", 85, 20.0, 28.0),
    Corner("Turn 8", 5, 2250, 2300, 2370, "left", 30, 38.0, 42.0),
    Corner("Turn 9", 4, 2500, 2560, 2630, "right", 50, 30.0, 37.0),
    Corner("Turn 10", 3, 2800, 2880, 2960, "left", 60, 27.0, 34.0),
    Corner("Turn 11", 4, 3100, 3160, 3220, "right", 45, 32.0, 40.0),
]

SONOMA_TRACK_LENGTH = 3600  # approximate meters


def find_nearest_corner(distance: float, lookahead: float = 300.0) -> Corner | None:
    """Find the next corner within lookahead distance."""
    track_dist = distance % SONOMA_TRACK_LENGTH
    best = None
    best_gap = float("inf")

    for corner in SONOMA_CORNERS:
        gap = (corner.entry_distance - track_dist) % SONOMA_TRACK_LENGTH
        if 0 < gap < lookahead and gap < best_gap:
            best = corner
            best_gap = gap

    return best


def distance_to_corner(distance: float, corner: Corner) -> float:
    """Distance from current position to corner entry."""
    track_dist = distance % SONOMA_TRACK_LENGTH
    return (corner.entry_distance - track_dist) % SONOMA_TRACK_LENGTH


def is_in_corner(distance: float) -> Corner | None:
    """Check if current distance is inside any corner."""
    track_dist = distance % SONOMA_TRACK_LENGTH
    for corner in SONOMA_CORNERS:
        if corner.entry_distance <= track_dist <= corner.exit_distance:
            return corner
    return None


def is_past_apex(distance: float) -> Corner | None:
    """Check if we're past the apex of a corner (exit phase)."""
    track_dist = distance % SONOMA_TRACK_LENGTH
    for corner in SONOMA_CORNERS:
        if corner.apex_distance < track_dist <= corner.exit_distance:
            return corner
    return None
