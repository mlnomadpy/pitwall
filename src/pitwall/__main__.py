#!/usr/bin/env python3
"""Pitwall Bridge v2 — modular entry point.

This file replaces the 4,596-line monolith. All domain logic, endpoints,
and shared state now live in the `src/pitwall/` package. This file handles only:
  1. CLI argument parsing
  2. Flask app creation
  3. Bridge state initialization
  4. Blueprint registration
  5. CAN reader startup
  6. app.run()

Usage unchanged:
    python pitwall_bridge_v2.py --track src/simulator/sonoma.json --port 8765
"""

import argparse
import atexit
import os
import signal
import subprocess
import sys
import threading

from flask import Flask
from flask_cors import CORS

# ── Bootstrap path so pitwall/ can find src/simulator + src/pitwall ─────────────
SRC_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "src"))
SIM_DIR = os.path.join(SRC_DIR, "simulator")
for p in (SRC_DIR, SIM_DIR):
    if p not in sys.path:
        sys.path.insert(0, p)

# ── Pitwall backend package ───────────────────────────────────────────────────
from pitwall.state import state
from pitwall.db import get_db, seed_signal_registry, log_llm_friction, reset_live_session, ensure_session_row
from pitwall import create_app, register_blueprints


app = create_app()


# ── Graceful shutdown ─────────────────────────────────────────────────────────
# DuckDB invalidates the whole file when a write is killed mid-flight (we
# already had to write rotation-recovery for this in db.py::reset_live_session).
# The right answer is to never let it happen: handle SIGTERM/SIGINT, stop the
# CAN reader + simulator, CHECKPOINT, release the Termux wake-lock, then exit.
# Belt and suspenders via atexit in case we're terminated by a path that
# doesn't go through main()'s normal flow.

_shutdown_done = threading.Event()


def _shutdown(reason: str = "exit"):
    """Idempotent graceful shutdown. Safe to call from signal handler or atexit."""
    if _shutdown_done.is_set():
        return
    _shutdown_done.set()
    print(f"\n⏻  shutdown ({reason})")

    sim = getattr(state, "simulator", None)
    if sim is not None:
        try:
            sim.stop(timeout=2.0)
            print("   simulator stopped")
        except Exception as e:
            print(f"   simulator stop failed: {e}")

    reader = getattr(state, "can_reader", None)
    if reader is not None:
        try:
            reader.stop(timeout=2.0)
            print("   CAN reader stopped")
        except Exception as e:
            print(f"   CAN reader stop failed: {e}")

    if getattr(state, "has_duckdb", False):
        try:
            with state.db_lock:
                conn = get_db()
                if conn is not None:
                    try:
                        conn.execute("CHECKPOINT")
                        print("   DuckDB CHECKPOINT ok")
                    finally:
                        conn.close()
        except Exception as e:
            print(f"   DuckDB CHECKPOINT failed: {e}")

    # Release Termux wake-lock if one was acquired. termux-wake-unlock is a
    # no-op if no lock is held; safe to call unconditionally.
    try:
        subprocess.run(
            ["termux-wake-unlock"],
            timeout=2.0, check=False,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        pass


def _signal_shutdown(signum, _frame):
    _shutdown(reason=f"signal {signum}")
    # Re-raise default behavior for SIGINT so Python exits with the right code
    sys.exit(0 if signum == signal.SIGTERM else 130)


signal.signal(signal.SIGTERM, _signal_shutdown)
signal.signal(signal.SIGINT, _signal_shutdown)
atexit.register(_shutdown, "atexit")


def _start_can_reader(*, session_id, interface, channel, bitrate, dbc_paths,
                      flush_ms, car_config_path=None):
    """Start a CanReader thread that pumps CAN frames into pitwall's stores."""
    try:
        from pitwall.features.telemetry.can_reader import CanReader
    except ImportError as e:
        print(f"⚠  CAN reader unavailable ({e}); install python-can + cantools")
        return None
    try:
        # CanReader needs the top-level `pitwall` package as `bridge` so it
        # can reach both `bridge.state.db_lock` and `bridge.db.get_db()`.
        # Passing `pitwall.state` was a pre-existing bug — the wide/tall
        # sink calls crash with AttributeError once frames actually flow.
        reader = CanReader(
            session_id=session_id, interface=interface, channel=channel,
            bitrate=bitrate, dbc_paths=dbc_paths, flush_ms=flush_ms,
            bridge=sys.modules["pitwall"],
            car_config_path=car_config_path,
        )
        reader.start()
        state.can_reader = reader
        print(f"✓  CAN reader started (interface={interface} channel={channel} session={session_id})")
        return reader
    except Exception as e:
        print(f"⚠  CAN reader failed to start: {e}")
        return None


def main():
    """CLI entry point for the Pitwall Bridge server."""
    parser = argparse.ArgumentParser(description="Pitwall Bridge Server")
    parser.add_argument("--track", default=os.path.join(SIM_DIR, "sonoma.json"))
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--can-channel", default=None)
    parser.add_argument("--can-interface", default="virtual")
    parser.add_argument("--can-bitrate", type=int, default=1_000_000,
                        help="CAN bus bitrate; AiM MXP CAN2 output is 1 Mbit/s")
    parser.add_argument("--can-dbc", action="append", default=None)
    parser.add_argument("--can-car-config", default=None,
                        help="per-car YAML for the post-decode pipeline "
                             "(default: data/cars/bmw_e46_m3.yaml; "
                             "pass --can-no-car-config to skip)")
    parser.add_argument("--can-no-car-config", action="store_true",
                        help="skip car config; emit raw DBC signal names")
    parser.add_argument("--can-session-id", default=None)
    parser.add_argument("--can-flush-ms", type=int, default=100)
    parser.add_argument("--dev", action="store_true",
                        help="serve via Flask's dev server instead of waitress "
                             "(local debugging only; never use in prod)")
    parser.add_argument("--simulate", action="store_true",
                        help="spawn the AiM MXP synthetic simulator and a "
                             "virtual-bus CanReader so the bridge has live "
                             "telemetry without any real car connected")
    parser.add_argument("--simulate-speed", type=float, default=1.0,
                        help="simulator speed multiplier (1.0 = real time)")
    parser.add_argument("--simulate-lap-seconds", type=float, default=60.0,
                        help="duration of one synthetic lap in seconds")
    args = parser.parse_args()

    # 1. Initialize imports (feature flags, coach engine, etc.)
    state.init_imports()

    # 2. Load track
    if state.has_sonic and os.path.exists(args.track):
        try:
            state.track = state.load_track(args.track)
            print(f"✓  Track: {state.track.name} ({len(state.track.corners)} corners)")
        except Exception as e:
            print(f"⚠  Track load failed: {e}")

    # 3. Wire LLM friction logger
    if state.has_coach and state.set_friction_logger:
        state.set_friction_logger(log_llm_friction)

    # 4. Seed signal registry
    if state.has_duckdb:
        try:
            from pitwall.db import REGISTRY_SEED_PATH
            n = seed_signal_registry()
            print(f"✓  signal_registry seeded ({n} entries from {os.path.relpath(REGISTRY_SEED_PATH)})")
        except Exception as e:
            print(f"⚠  signal_registry seed skipped: {e}")

    # 5. CAN reader (+ optional synthetic simulator)
    if args.simulate and not args.can_channel:
        # Auto-pick a virtual channel so the simulator and reader meet
        # in-process. Override --can-interface/--can-channel only if the
        # user didn't set them.
        args.can_interface = "virtual"
        args.can_channel = "pitwall_simulate"

    if args.can_channel:
        sid = args.can_session_id or "_live"
        if sid == "_live":
            reset_live_session()
        ensure_session_row(sid, track=(state.track.name if state.track else None),
                           note="auto-created by bridge --can-channel" + (" (live placeholder)" if sid == "_live" else ""))
        _start_can_reader(session_id=sid, interface=args.can_interface, channel=args.can_channel,
                          bitrate=args.can_bitrate, dbc_paths=args.can_dbc, flush_ms=args.can_flush_ms,
                          car_config_path=(False if args.can_no_car_config else args.can_car_config))
        print(f"    CAN session: {sid}")

        if args.simulate:
            try:
                # src/simulator is on sys.path (see bootstrap above), so
                # import the module name directly.
                from aim_mxp_simulator import AimMxpSimulator, LapProfile
                sim = AimMxpSimulator(
                    interface=args.can_interface, channel=args.can_channel,
                    bitrate=args.can_bitrate, speed_x=args.simulate_speed,
                    profile=LapProfile(lap_seconds=args.simulate_lap_seconds),
                )
                sim.start()
                state.simulator = sim
                print(f"✓  Synthetic simulator running on {args.can_interface}/"
                      f"{args.can_channel} ({args.simulate_speed:.2f}× speed, "
                      f"{args.simulate_lap_seconds:.0f}s laps)")
            except Exception as e:
                print(f"⚠  Simulator failed to start: {e}")

    # 6. Launch
    port = args.port
    db_path = state.db_path
    print(f"\n🏁  Pitwall Bridge v2 on http://127.0.0.1:{port}")
    print(f"    Engine: {'sonic_model (real cues)' if state.has_sonic else 'rule fallback'}")
    print(f"    DuckDB: {'enabled → ' + db_path if state.has_duckdb else 'disabled'}")
    if args.can_channel:
        print(f"    CAN:    {args.can_interface}/{args.can_channel}")
    print(f"\n    Emulator tunnel:")
    print(f"    ~/Library/Android/sdk/platform-tools/adb reverse tcp:{port} tcp:{port}\n")

    if args.dev:
        # Dev path: Flask's built-in server. Re-prints the production
        # warning at boot — that's the intended signal.
        try:
            app.run(host="127.0.0.1", port=port, debug=False, threaded=True)
        finally:
            _shutdown(reason="dev server stopped")
    else:
        # Production path: waitress is a pure-Python WSGI server with
        # bounded thread pool + per-request channel timeout. No DNS
        # surprise, no kernel module, runs on Termux. Same `127.0.0.1`
        # bind as before.
        try:
            from waitress import serve
        except ImportError:
            print("⚠  waitress not installed — falling back to Flask dev server.")
            print("   `pip install waitress` to silence this and serve production-ready.")
            try:
                app.run(host="127.0.0.1", port=port, debug=False, threaded=True)
            finally:
                _shutdown(reason="dev fallback stopped")
        else:
            try:
                serve(
                    app, host="127.0.0.1", port=port,
                    threads=8,                   # bounded worker pool
                    channel_timeout=30,          # kill stuck requests
                    cleanup_interval=10,         # housekeep dead channels
                    ident="pitwall-bridge",      # appears in Server: header
                )
            finally:
                _shutdown(reason="waitress stopped")


if __name__ == "__main__":
    main()
