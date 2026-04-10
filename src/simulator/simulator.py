"""
Pitwall Simulator — replays VBO telemetry with real track data from track.json.

Usage:
    python simulator.py session.vbo --track sonoma.json
    python simulator.py session.vbo --track sonoma.json --speed 5
    python simulator.py session.vbo --track sonoma.json --speed 0 --export cues.csv
"""

import argparse
import csv
import math
import sys
import time
from pathlib import Path

from vbo_parser import parse_vbo, TelemetryFrame
from track_loader import (
    load_track, TrackMap, CornerDef,
    find_nearest_corner, distance_to_corner,
    is_in_corner, is_past_apex, get_sector, get_elevation,
    cross_track_error,
)
from sonic_model import compute_cues, compute_lap_estimate_cue, AudioCue, Pattern

# Try to load LSTM-driven sonic model v2
_sonic_v2 = None
def _try_load_sonic_v2(track):
    global _sonic_v2
    model_path = os.path.join(os.path.dirname(__file__), "models", "lstm_v3.pt")
    if os.path.exists(model_path):
        try:
            from sonic_model_v2 import SonicModelV2
            _sonic_v2 = SonicModelV2(model_path, track)
            print(f"  Sonic Model v2 loaded (LSTM-driven)")
            return True
        except Exception as e:
            print(f"  Sonic Model v2 failed to load: {e}")
    return False


# ─── ANSI ────────────────────────────────────────────────────────────────────

RED = "\033[91m"; GREEN = "\033[92m"; YELLOW = "\033[93m"; BLUE = "\033[94m"
MAGENTA = "\033[95m"; CYAN = "\033[96m"; WHITE = "\033[97m"
DIM = "\033[2m"; BOLD = "\033[1m"; RESET = "\033[0m"


def bar(value, max_val, width=20, color=GREEN):
    pct = max(0, min(1, value / max_val)) if max_val > 0 else 0
    filled = int(pct * width)
    return f"{color}{'█' * filled}{DIM}{'░' * (width - filled)}{RESET}"


def pattern_sym(p: Pattern) -> str:
    return {"silent": "  ", "continuous": "~~", "pulse": "♪ ",
            "fast_pulse": "♪♪", "sharp": "⚡", "buzz": "⚠ ",
            "chime_up": "↑↑", "chime_down": "↓↓", "chime_neutral": "──"
            }.get(p.value, "??")


def freq_note(freq):
    if freq <= 0: return "---"
    notes = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"]
    semi = 12 * math.log2(freq / 440.0) + 69
    return f"{notes[int(round(semi)) % 12]}{int(round(semi)) // 12 - 1}"


# ─── Lap Detection ──────────────────────────────────────────────────────────

class LapTracker:
    def __init__(self, track: TrackMap):
        self.track = track
        self.lap = 0
        self.lap_start_time = 0.0
        self.best_lap_time = None
        self.last_lap_time = None
        self.was_near_sf = True
        self.cooldown = 0
        self.sector_times = {}
        self.current_sector = None

    def update(self, frame: TelemetryFrame) -> bool:
        """Returns True if a new lap started."""
        new_lap = False

        # Start/finish detection
        from vbo_parser import parse_vbo  # for haversine
        d = _haversine(frame.lat, frame.lon, self.track.sf_lat, self.track.sf_lon)
        near = d < 30

        if self.cooldown > 0:
            self.cooldown -= 1

        if near and not self.was_near_sf and self.lap > 0 and self.cooldown == 0:
            lap_time = frame.timestamp - self.lap_start_time
            if 30 < lap_time < 300:  # reasonable lap time
                self.last_lap_time = lap_time
                if self.best_lap_time is None or lap_time < self.best_lap_time:
                    self.best_lap_time = lap_time
                self.lap += 1
                self.lap_start_time = frame.timestamp
                self.cooldown = 50
                new_lap = True
        elif near and not self.was_near_sf and self.lap == 0:
            self.lap = 1
            self.lap_start_time = frame.timestamp
            self.cooldown = 50

        self.was_near_sf = near

        # Sector tracking
        sector = get_sector(self.track, frame.distance)
        if sector and sector.name != self.current_sector:
            self.current_sector = sector.name

        return new_lap


def _haversine(lat1, lon1, lat2, lon2):
    R = 6371000
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1))*math.cos(math.radians(lat2))*math.sin(dlon/2)**2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))


# ─── Render ──────────────────────────────────────────────────────────────────

def render(frame, cues, idx, total, lap_tracker, track, elapsed):
    speed_mph = frame.speed * 2.237
    speed_kmh = frame.speed * 3.6

    corner = is_in_corner(track, frame.distance)
    next_c = find_nearest_corner(track, frame.distance)
    past = is_past_apex(track, frame.distance)
    sector = get_sector(track, frame.distance)
    elev = get_elevation(track, frame.distance)

    dist_next = distance_to_corner(track, frame.distance, next_c) if next_c else 999
    corner_name = corner.name if corner else ""
    next_name = next_c.name if next_c else ""

    # Brake zone indicator
    brake_zone = ""
    if next_c and next_c.brake_distance > 0 and dist_next < next_c.brake_distance + 50:
        brake_remaining = dist_next - (next_c.brake_distance if dist_next > next_c.brake_distance else 0)
        if dist_next <= next_c.brake_distance:
            brake_zone = f"{RED}BRAKE ZONE{RESET}"
        else:
            brake_zone = f"{YELLOW}brake in {dist_next - next_c.brake_distance:.0f}m{RESET}"

    grip_pct = min(frame.combo_g / 2.29 * 100, 150)
    grip_color = RED if grip_pct > 95 else YELLOW if grip_pct > 75 else GREEN

    # Gear estimate
    if frame.speed > 2 and frame.rpm > 500:
        ratio = frame.rpm / (frame.speed * 60 / (2 * 3.14159 * 0.315))
        gear_ratios = {1: 13.17, 2: 8.09, 3: 5.77, 4: 4.52, 5: 3.68, 6: 3.09}
        gear = min(gear_ratios.items(), key=lambda x: abs(x[1] - ratio))[0]
    else:
        gear = 0

    # Lap info
    lap_str = f"Lap {lap_tracker.lap}"
    if lap_tracker.last_lap_time:
        lap_str += f"  last {lap_tracker.last_lap_time:.1f}s"
    if lap_tracker.best_lap_time:
        lap_str += f"  best {lap_tracker.best_lap_time:.1f}s"

    lines = [f"{'═' * 90}"]

    # Row 1: Speed + RPM + Gear + Track
    lines.append(
        f"  {BOLD}{WHITE}{track.name}{RESET}  "
        f"{BOLD}Speed{RESET} {speed_mph:5.1f}mph ({speed_kmh:5.1f}km/h)  "
        f"{BOLD}RPM{RESET} {frame.rpm:5.0f}  "
        f"{BOLD}Gear{RESET} {gear}  "
        f"{DIM}{idx}/{total}{RESET}")

    # Row 2: Pedals
    lines.append(
        f"  {RED}Brake{RESET} {frame.brake_pressure:5.1f}bar {bar(frame.brake_pressure, 100, 20, RED)}  "
        f"{GREEN}Throt{RESET} {frame.throttle:5.1f}%  {bar(frame.throttle, 100, 20, GREEN)}")

    # Row 3: G-forces
    lines.append(
        f"  {CYAN}G-Lat{RESET} {frame.g_lat:+5.2f}G {bar(abs(frame.g_lat), 2, 12, CYAN)}  "
        f"{MAGENTA}G-Lon{RESET} {frame.g_long:+5.2f}G {bar(abs(frame.g_long), 2, 12, MAGENTA)}  "
        f"{grip_color}Grip {grip_pct:4.0f}%{RESET}  "
        f"Combo {frame.combo_g:.2f}G")

    # Row 4: Track position + corner
    in_str = f"{YELLOW}{corner_name}{RESET}" if corner_name else f"{DIM}straight{RESET}"
    next_str = f"{next_name} in {dist_next:.0f}m" if next_c and dist_next < 500 else ""
    sec_str = sector.name if sector else ""
    lines.append(
        f"  {BOLD}Steer{RESET} {frame.steering:+6.0f}°  "
        f"{BOLD}In{RESET} {in_str}  "
        f"{BOLD}Next{RESET} {next_str}  "
        f"{brake_zone}  "
        f"{DIM}{sec_str}  dist:{frame.distance:.0f}m  elev:{elev:.0f}m{RESET}")

    # Row 5: Audio cues
    lines.append(f"  {'─' * 86}")
    if cues:
        for cue in cues[:4]:  # max 4 cues shown
            sym = pattern_sym(cue.pattern)
            note = freq_note(cue.frequency)
            vol_bar = "▮" * int(cue.volume * 10)
            pc = RED if cue.priority >= 3 else YELLOW if cue.priority >= 2 else GREEN if cue.priority >= 1 else DIM
            lines.append(
                f"  {pc}{sym}{RESET} "
                f"{cue.layer:<18} {note:>4}({cue.frequency:5.0f}Hz) "
                f"vol {vol_bar:<10} "
                f"{DIM}{cue.reason[:60]}{RESET}")
    else:
        lines.append(f"  {DIM}(silence){RESET}")

    # Row 6: Status
    lines.append(
        f"  {DIM}Coolant {frame.coolant_temp:.0f}°C  Oil {frame.oil_temp:.0f}°C  "
        f"Fuel {frame.fuel_level:.0f}%  {lap_str}  Time {elapsed:.1f}s{RESET}")

    sys.stdout.write("\033[2J\033[H")
    sys.stdout.write("\n".join(lines) + "\n")
    sys.stdout.flush()


# ─── Main ────────────────────────────────────────────────────────────────────

def run(vbo_path, track_path, speed_mult=1.0, export_path=None):
    print(f"Loading track: {track_path}")
    track = load_track(track_path)
    print(f"  {track.name}: {track.track_length:.0f}m, {len(track.corners)} corners, {len(track.sectors)} sectors")

    # Try loading LSTM sonic model
    use_v2 = _try_load_sonic_v2(track)

    print(f"Loading telemetry: {vbo_path}")
    meta, frames = parse_vbo(vbo_path)
    print(f"  {len(frames)} frames ({len(frames)/10:.0f}s)")

    if not frames:
        print("No data!"); return

    lap_tracker = LapTracker(track)
    start_ts = frames[0].timestamp

    # CSV export
    csv_w = None
    csv_f = None
    if export_path:
        csv_f = open(export_path, "w", newline="")
        csv_w = csv.writer(csv_f)
        csv_w.writerow([
            "frame", "timestamp", "speed_ms", "g_lat", "g_long", "combo_g",
            "brake_bar", "throttle", "steering", "rpm", "distance",
            "corner", "dist_to_corner", "corner_severity", "corner_brake_dist",
            "past_apex", "sector", "lap", "elapsed_in_lap",
            "cue_count", "cue_layers", "cue_freqs", "cue_vols", "cue_patterns", "cue_reasons"])

    print(f"\nPlayback at {speed_mult}x. Ctrl+C to stop.\n")
    time.sleep(0.5)

    prev_fd = None
    try:
        for i, f in enumerate(frames):
            corner = is_in_corner(track, f.distance)
            next_c = find_nearest_corner(track, f.distance)
            past = is_past_apex(track, f.distance)
            sector = get_sector(track, f.distance)
            elapsed = f.timestamp - start_ts

            new_lap = lap_tracker.update(f)

            fd = {
                "speed": f.speed, "g_lat": f.g_lat, "g_long": f.g_long,
                "combo_g": f.combo_g, "brake_pressure": f.brake_pressure,
                "brake_position": f.brake_position, "throttle": f.throttle,
                "steering": f.steering, "rpm": f.rpm, "distance": f.distance,
                "distance_to_corner": distance_to_corner(track, f.distance, next_c) if next_c else 999,
                "corner_severity": next_c.severity if next_c else 0,
                "in_corner": corner is not None,
                "past_apex": past is not None,
            }

            # Use LSTM-driven sonic model if available, else fall back to hand-tuned
            if use_v2 and _sonic_v2 is not None:
                cues = _sonic_v2.update(f)
            else:
                cues = compute_cues(fd, prev_fd)

            # Lap estimate at sector boundaries
            elapsed_in_lap = f.timestamp - lap_tracker.lap_start_time
            if sector and lap_tracker.lap > 0:
                track_pos = f.distance % track.track_length
                for pct in [0.33, 0.66]:
                    if abs(track_pos - track.track_length * pct) < 15:
                        lc = compute_lap_estimate_cue(
                            f.distance, elapsed_in_lap, track.track_length, lap_tracker.best_lap_time)
                        if lc:
                            cues.append(lc)

            render(f, cues, i + 1, len(frames), lap_tracker, track, elapsed)

            if csv_w:
                csv_w.writerow([
                    i, f.timestamp, f.speed, f.g_lat, f.g_long, f.combo_g,
                    f.brake_pressure, f.throttle, f.steering, f.rpm, f.distance,
                    corner.name if corner else "",
                    fd["distance_to_corner"], fd["corner_severity"],
                    next_c.brake_distance if next_c else 0,
                    1 if past else 0,
                    sector.name if sector else "",
                    lap_tracker.lap, elapsed_in_lap,
                    len(cues),
                    "|".join(c.layer for c in cues),
                    "|".join(f"{c.frequency:.0f}" for c in cues),
                    "|".join(f"{c.volume:.2f}" for c in cues),
                    "|".join(c.pattern.value for c in cues),
                    "|".join(c.reason for c in cues),
                ])

            prev_fd = fd

            if speed_mult > 0 and i < len(frames) - 1:
                dt = (frames[i+1].timestamp - f.timestamp) / speed_mult
                if dt > 0:
                    time.sleep(max(0.001, dt))

    except KeyboardInterrupt:
        print(f"\n\nStopped at frame {i+1}")

    finally:
        if csv_f:
            csv_f.close()
            print(f"\nExported to {export_path}")

    print(f"\n{'═' * 60}")
    print(f"Session: {len(frames)} frames, {elapsed:.0f}s")
    print(f"Laps: {lap_tracker.lap}")
    if lap_tracker.best_lap_time:
        print(f"Best lap: {lap_tracker.best_lap_time:.1f}s")
    print(f"Max speed: {max(f.speed*3.6 for f in frames):.1f} km/h")
    print(f"Max gLat: {max(abs(f.g_lat) for f in frames):.2f}G")
    print(f"Max brake: {max(f.brake_pressure for f in frames):.1f} bar")


if __name__ == "__main__":
    p = argparse.ArgumentParser(description="Pitwall Simulator (track-aware)")
    p.add_argument("vbo_file", help=".vbo file to replay")
    p.add_argument("--track", required=True, help="track.json file")
    p.add_argument("--speed", type=float, default=1.0, help="Playback speed (0=instant)")
    p.add_argument("--export", default=None, help="Export cues CSV")
    a = p.parse_args()
    run(a.vbo_file, a.track, a.speed, a.export)
