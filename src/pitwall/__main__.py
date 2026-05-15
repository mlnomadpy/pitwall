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
import os
import sys

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


def _start_can_reader(*, session_id, interface, channel, bitrate, dbc_paths, flush_ms):
    """Start a CanReader thread that pumps CAN frames into pitwall's stores."""
    try:
        from pitwall.features.telemetry.can_reader import CanReader
    except ImportError as e:
        print(f"⚠  CAN reader unavailable ({e}); install python-can + cantools")
        return None
    try:
        reader = CanReader(
            session_id=session_id, interface=interface, channel=channel,
            bitrate=bitrate, dbc_paths=dbc_paths, flush_ms=flush_ms,
            bridge=sys.modules["pitwall.state"],
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
    parser.add_argument("--can-session-id", default=None)
    parser.add_argument("--can-flush-ms", type=int, default=100)
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

    # 5. CAN reader
    if args.can_channel:
        sid = args.can_session_id or "_live"
        if sid == "_live":
            reset_live_session()
        ensure_session_row(sid, track=(state.track.name if state.track else None),
                           note="auto-created by bridge --can-channel" + (" (live placeholder)" if sid == "_live" else ""))
        _start_can_reader(session_id=sid, interface=args.can_interface, channel=args.can_channel,
                          bitrate=args.can_bitrate, dbc_paths=args.can_dbc, flush_ms=args.can_flush_ms)
        print(f"    CAN session: {sid}")

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
    try:
        app.run(host="127.0.0.1", port=port, debug=False, threaded=True)
    finally:
        if state.can_reader is not None:
            state.can_reader.stop()


if __name__ == "__main__":
    main()
