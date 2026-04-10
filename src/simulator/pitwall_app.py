"""
Pitwall Terminal App — touchable TUI for laptop and Termux.

Runs the full coaching pipeline:
  VBO replay (or live Bluetooth) → Fusion → LSTM → Sonic Model → Audio

Usage:
    # Laptop development (VBO replay)
    python pitwall_app.py --replay session.vbo --track sonoma.json

    # Live mode (Bluetooth sensors)
    python pitwall_app.py --live --track sonoma.json

    # Fast replay (no audio, just visuals)
    python pitwall_app.py --replay session.vbo --track sonoma.json --speed 5
"""

import argparse
import math
import os
import sys
import threading
import time

# Check for textual — fall back to simple mode if not available
try:
    from textual.app import App, ComposeResult
    from textual.widgets import Button, Header, Footer, Static, ProgressBar, Label
    from textual.containers import Horizontal, Vertical, Container
    from textual.reactive import reactive
    from textual import work
    HAS_TEXTUAL = True
except ImportError:
    HAS_TEXTUAL = False

from vbo_parser import parse_vbo
from track_loader import (
    load_track, find_nearest_corner, distance_to_corner,
    is_in_corner, is_past_apex, get_sector,
)
from audio_engine import create_audio_engine

# Try loading LSTM sonic model, fall back to v1
try:
    from sonic_model_v2 import SonicModelV2
    HAS_LSTM = True
except ImportError:
    HAS_LSTM = False

from sonic_model import compute_cues as compute_cues_v1


# ─── Session Manager ─────────────────────────────────────────────────────────

class SessionManager:
    """Manages the telemetry session — replay or live."""

    def __init__(self, track_path, model_path=None):
        self.track = load_track(track_path)
        self.audio = create_audio_engine()
        self.recording = False
        self.running = False
        self.current_frame = None
        self.current_cues = []
        self.frame_count = 0
        self.session_start = 0
        self.lap = 0
        self.best_lap = None
        self.last_lap = None

        # Sonic model
        self.sonic_v2 = None
        if HAS_LSTM and model_path and os.path.exists(model_path):
            try:
                self.sonic_v2 = SonicModelV2(model_path, self.track)
                print(f"  LSTM Sonic Model v2 loaded")
            except Exception as e:
                print(f"  LSTM load failed: {e}, using v1")

    def start_replay(self, vbo_path, speed=1.0, on_frame=None):
        """Replay a VBO file."""
        meta, frames = parse_vbo(vbo_path)
        if not frames:
            print("No frames!")
            return

        self.running = True
        self.session_start = frames[0].timestamp
        self.audio.start()
        self.frame_count = len(frames)

        prev_fd = None
        for i, f in enumerate(frames):
            if not self.running:
                break

            self.current_frame = f
            corner = is_in_corner(self.track, f.distance)
            next_c = find_nearest_corner(self.track, f.distance)

            # Compute cues
            if self.sonic_v2:
                cues = self.sonic_v2.update(f)
            else:
                fd = {
                    "speed": f.speed, "g_lat": f.g_lat, "g_long": f.g_long,
                    "combo_g": f.combo_g, "brake_pressure": f.brake_pressure,
                    "throttle": f.throttle, "steering": f.steering, "rpm": f.rpm,
                    "distance": f.distance,
                    "distance_to_corner": distance_to_corner(self.track, f.distance, next_c) if next_c else 999,
                    "corner_severity": next_c.severity if next_c else 0,
                    "in_corner": corner is not None,
                    "past_apex": is_past_apex(self.track, f.distance) is not None,
                }
                cues = compute_cues_v1(fd, prev_fd)
                prev_fd = fd

            self.current_cues = cues
            self.audio.set_cues(cues)

            if on_frame:
                on_frame(i, f, cues, corner, next_c)

            # Timing
            if speed > 0 and i < len(frames) - 1:
                dt = (frames[i + 1].timestamp - f.timestamp) / speed
                if dt > 0:
                    time.sleep(max(0.001, dt))

        self.running = False
        self.audio.stop()

    def stop(self):
        self.running = False
        self.audio.silence()
        self.audio.stop()


# ─── Simple Terminal Mode (No Textual) ────────────────────────────────────────

def run_simple(args):
    """Simple terminal mode — works everywhere, no textual dependency."""
    print("Pitwall — Simple Terminal Mode")
    print("=" * 60)

    model_path = os.path.join(os.path.dirname(__file__), "models", "lstm_v3.pt")
    session = SessionManager(args.track, model_path)
    print(f"Track: {session.track.name} ({session.track.track_length:.0f}m, {len(session.track.corners)} corners)")

    if args.replay:
        print(f"Replay: {args.replay}")
        print(f"Speed: {args.speed}x")
        print()

        def on_frame(i, f, cues, corner, next_c):
            # Clear and redraw
            speed_kmh = f.speed * 3.6
            speed_mph = f.speed * 2.237
            grip = f.combo_g / 2.29 * 100

            corner_name = corner.name if corner else ""
            next_name = next_c.name if next_c else ""
            dist = distance_to_corner(session.track, f.distance, next_c) if next_c else 999

            # Colored bars (ANSI)
            brake_pct = min(f.brake_pressure / 100, 1)
            thr_pct = f.throttle / 100
            grip_color = "\033[91m" if grip > 95 else "\033[93m" if grip > 70 else "\033[92m"

            brake_bar = "\033[91m" + "█" * int(brake_pct * 20) + "\033[2m" + "░" * (20 - int(brake_pct * 20)) + "\033[0m"
            thr_bar = "\033[92m" + "█" * int(thr_pct * 20) + "\033[2m" + "░" * (20 - int(thr_pct * 20)) + "\033[0m"

            # Build display
            lines = []
            lines.append(f"\033[1m{'═' * 60}\033[0m")
            lines.append(f"  \033[1mPITWALL\033[0m  {session.track.name}  Frame {i+1}/{session.frame_count}")
            lines.append(f"  Speed \033[1m{speed_mph:5.1f}\033[0m mph ({speed_kmh:5.1f} km/h)  RPM {f.rpm:5.0f}  {grip_color}Grip {grip:3.0f}%\033[0m")
            lines.append(f"  Brake {f.brake_pressure:5.1f} bar {brake_bar}  Throttle {f.throttle:4.0f}% {thr_bar}")
            lines.append(f"  G-Lat \033[96m{f.g_lat:+5.2f}\033[0m  G-Long \033[95m{f.g_long:+5.2f}\033[0m  Combo {f.combo_g:.2f}G")

            loc = f"\033[93m{corner_name}\033[0m" if corner_name else "\033[2mstraight\033[0m"
            nxt = f"{next_name} in {dist:.0f}m" if next_c and dist < 500 else ""
            lines.append(f"  Corner: {loc}  Next: {nxt}  Steer: {f.steering:+.0f}°")

            lines.append(f"  \033[2m{'─' * 56}\033[0m")
            if cues:
                for c in cues[:3]:
                    pcolor = "\033[91m" if c.priority >= 3 else "\033[93m" if c.priority >= 2 else "\033[92m" if c.priority >= 1 else "\033[2m"
                    pattern = c.pattern.value if hasattr(c.pattern, 'value') else str(c.pattern)
                    sym = {"continuous": "~~", "pulse": "♪ ", "fast_pulse": "♪♪", "buzz": "⚠ ", "silent": "  "}.get(pattern, "??")
                    lines.append(f"  {pcolor}{sym}\033[0m {c.layer:<16} {c.frequency:5.0f}Hz vol {c.volume:.2f}  \033[2m{c.reason[:50]}\033[0m")
            else:
                lines.append(f"  \033[2m(silence)\033[0m")

            sys.stdout.write("\033[2J\033[H" + "\n".join(lines) + "\n")
            sys.stdout.flush()

        try:
            session.start_replay(args.replay, speed=args.speed, on_frame=on_frame)
        except KeyboardInterrupt:
            print("\n\nStopped.")
        finally:
            session.stop()

    else:
        print("No --replay file specified. Use --replay session.vbo")


# ─── Textual TUI Mode ────────────────────────────────────────────────────────

if HAS_TEXTUAL:
    class PitwallTUI(App):
        """Full touchable terminal UI."""

        CSS = """
        Screen {
            background: #1a1a1a;
        }
        #header_bar {
            background: #d32f2f;
            color: white;
            height: 3;
            padding: 1;
            text-align: center;
        }
        #speed_display {
            color: white;
            text-style: bold;
            height: 3;
            padding: 0 2;
        }
        #telemetry {
            height: auto;
            padding: 0 2;
            color: #cccccc;
        }
        #cue_display {
            height: auto;
            min-height: 4;
            padding: 0 2;
            color: #ffd600;
            border-top: solid #333333;
        }
        #status_bar {
            height: 2;
            padding: 0 2;
            color: #888888;
        }
        .button-row {
            height: 5;
            align: center middle;
        }
        Button {
            margin: 0 1;
            min-width: 14;
        }
        Button.start { background: #2e7d32; }
        Button.stop { background: #c62828; }
        Button.record { background: #1565c0; }
        Button.review { background: #6a1b9a; }
        """

        BINDINGS = [
            ("q", "quit", "Quit"),
            ("s", "start_session", "Start"),
            ("x", "stop_session", "Stop"),
            ("r", "toggle_record", "Record"),
        ]

        def __init__(self, track_path, replay_path=None, speed=1.0):
            super().__init__()
            self.track_path = track_path
            self.replay_path = replay_path
            self.speed = speed
            self.session = None

        def compose(self) -> ComposeResult:
            yield Static("🏁 PITWALL", id="header_bar")
            yield Static("Speed: --- mph", id="speed_display")
            yield Static("Brake: --- | Throttle: --- | G: ---", id="telemetry")
            yield Static("(ready)", id="cue_display")
            with Horizontal(classes="button-row"):
                yield Button("▶ START", id="start", classes="start")
                yield Button("■ STOP", id="stop", classes="stop")
            with Horizontal(classes="button-row"):
                yield Button("⏺ RECORD", id="record", classes="record")
                yield Button("📊 REVIEW", id="review", classes="review")
            yield Static("Ready | Track: loading...", id="status_bar")

        def on_mount(self):
            model_path = os.path.join(os.path.dirname(__file__), "models", "lstm_v3.pt")
            self.session = SessionManager(self.track_path, model_path)
            self.query_one("#status_bar").update(
                f"Ready | {self.session.track.name} | {len(self.session.track.corners)} corners"
            )

        def on_button_pressed(self, event: Button.Pressed):
            if event.button.id == "start":
                self.action_start_session()
            elif event.button.id == "stop":
                self.action_stop_session()
            elif event.button.id == "record":
                self.action_toggle_record()
            elif event.button.id == "review":
                self.notify("Review mode — coming soon")

        @work(thread=True)
        def action_start_session(self):
            if not self.replay_path:
                self.call_from_thread(self.notify, "No replay file. Use --replay")
                return

            def on_frame(i, f, cues, corner, next_c):
                speed_mph = f.speed * 2.237
                speed_kmh = f.speed * 3.6
                grip = min(f.combo_g / 2.29 * 100, 150)

                corner_str = corner.name if corner else "straight"
                dist = distance_to_corner(self.session.track, f.distance, next_c) if next_c else 999
                next_str = f"{next_c.name} {dist:.0f}m" if next_c and dist < 500 else ""

                self.call_from_thread(
                    self.query_one("#speed_display").update,
                    f"Speed: {speed_mph:.0f} mph ({speed_kmh:.0f} km/h)  RPM: {f.rpm:.0f}  Grip: {grip:.0f}%"
                )

                brake_bar = "█" * int(min(f.brake_pressure / 100, 1) * 15)
                thr_bar = "█" * int(f.throttle / 100 * 15)
                self.call_from_thread(
                    self.query_one("#telemetry").update,
                    f"Brake: {f.brake_pressure:5.1f} bar {brake_bar:<15}  Throttle: {f.throttle:4.0f}% {thr_bar:<15}\n"
                    f"G-Lat: {f.g_lat:+.2f}  G-Long: {f.g_long:+.2f}  Steer: {f.steering:+.0f}°\n"
                    f"Corner: {corner_str}  Next: {next_str}"
                )

                if cues:
                    cue_text = "\n".join(
                        f"{'⚠' if c.priority >= 3 else '♪' if c.priority >= 1 else '~'} "
                        f"{c.layer}: {c.frequency:.0f}Hz  {c.reason[:55]}"
                        for c in cues[:3]
                    )
                else:
                    cue_text = "(silence)"

                self.call_from_thread(self.query_one("#cue_display").update, cue_text)
                self.call_from_thread(
                    self.query_one("#status_bar").update,
                    f"LIVE | Frame {i+1}/{self.session.frame_count} | {self.session.track.name}"
                )

            self.session.start_replay(self.replay_path, speed=self.speed, on_frame=on_frame)

        def action_stop_session(self):
            if self.session:
                self.session.stop()
            self.query_one("#status_bar").update("Stopped")
            self.notify("Session stopped")

        def action_toggle_record(self):
            if self.session:
                self.session.recording = not self.session.recording
                state = "ON" if self.session.recording else "OFF"
                self.notify(f"Recording: {state}")


# ─── Main ────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Pitwall Terminal App")
    parser.add_argument("--replay", help="VBO file to replay")
    parser.add_argument("--track", required=True, help="Track JSON file")
    parser.add_argument("--speed", type=float, default=1.0, help="Playback speed")
    parser.add_argument("--simple", action="store_true", help="Force simple mode (no textual)")
    parser.add_argument("--live", action="store_true", help="Live Bluetooth mode (not yet implemented)")
    args = parser.parse_args()

    if args.live:
        print("Live Bluetooth mode not yet implemented. Use --replay for now.")
        sys.exit(1)

    if args.simple or not HAS_TEXTUAL:
        if not HAS_TEXTUAL:
            print("(textual not installed — using simple mode)")
        run_simple(args)
    else:
        app = PitwallTUI(args.track, args.replay, args.speed)
        app.run()


if __name__ == "__main__":
    main()
