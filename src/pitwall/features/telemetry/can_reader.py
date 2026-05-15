#!/usr/bin/env python3
"""
can_reader.py — CAN bus consumer that ingests into pitwall's DuckDB.

Reads CAN frames from a python-can Bus, decodes them with cantools using one
or more DBC files, then writes signals to the appropriate stores:

  - The 11 wide-table canonicals (speed_ms, brake_bar, …) update an in-memory
    "current frame" buffer; a row is committed to `telemetry` whenever the
    buffer's `distance_m` increments or every N milliseconds (whichever is
    sooner). This preserves the wide-table contract used by lap detection
    and every Phase-6 endpoint.

  - Everything else lands in `telemetry_signals` (ADR-015 tall sink) at
    native rate via `_resolve_signal_id` / direct INSERT.

The reader can run two ways:

    # Standalone (separate process; talks to a running bridge over HTTP)
    python3 tools/can_reader.py --session-id sonoma-001 \\
        --interface virtual --channel pitwall_dev

    # Embedded (called from pitwall_bridge.py with --can-channel; in-process,
    # uses the bridge's DuckDB lock directly)
    from tools.can_reader import CanReader
    reader = CanReader(session_id="...", interface="virtual", channel="...")
    reader.start()

For dev/test use `interface=virtual` (pure Python, no kernel modules).
For production over USB use `interface=slcan, channel=/dev/ttyACM0` (CANable,
Macchina M2, similar). For Linux native CAN use `interface=socketcan,
channel=can0`.

Production target — 2003 BMW M3 (E46) via AiM MXP CAN2 output:

    interface = slcan          # ASCII "t<ID><DLC><DATA>\\r" frames
    channel   = /dev/ttyACM0   # CANable 2.0 / Jhoinrch RH-02 PRO CDC port
    bitrate   = 1_000_000      # MXP CAN2 runs at 1 Mbit/s, NOT 500 kbps
    dbc       = data/dbc/pitwall.dbc  # AIM MXP SmartyCam V3.0 layouts

See .context/CANable2_Pixel10_Developer_Integration_Spec_v1.0.pdf and
data/cars/bmw_e46_m3.yaml for the full wiring + protocol description.
The MXP re-broadcasts a clean 8-frame protocol (0x420-0x424, 0x450-0x452)
at 11-bit standard / Little Endian; this reader only needs the bitrate
to match. Native BMW PT-CAN is never touched.
"""
from __future__ import annotations

import argparse
import logging
import os
import sys
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import can
import cantools

ROOT = Path(__file__).resolve().parents[4]

from pitwall.dead_reckoning import DeadReckoner, DeadReckonerConfig

# Lazy import — bridge module pulls in flask + sonic_model. Reader works
# without the bridge when run standalone (it'll POST signals over HTTP).
_HAS_BRIDGE = False
try:
    import pitwall as br
    _HAS_BRIDGE = True
except ImportError:
    pass


DEFAULT_DBC = ROOT / "data" / "dbc" / "pitwall.dbc"


@dataclass
class _WideRow:
    """Buffer for the next wide-table row. Filled from CAN frames as they
    decode; flushed when distance_m advances or the flush timer fires."""
    timestamp: float = 0.0
    distance_m: float = 0.0
    speed_ms: float = 0.0
    g_lat: float = 0.0
    g_long: float = 0.0
    combo_g: float = 0.0
    brake_bar: float = 0.0
    throttle_pct: float = 0.0
    steering_deg: float = 0.0
    rpm: float = 0.0
    lat: float = 0.0
    lon: float = 0.0
    seen_any: bool = False


_WIDE_FIELDS = (
    "distance_m", "speed_ms", "g_lat", "g_long", "combo_g",
    "brake_bar", "throttle_pct", "steering_deg", "rpm", "lat", "lon",
)

# AIM MXP V3.0 Canonical Mappings
_CANONICAL_MAPPING = {
    "speed_mph": "speed_ms",          # mph -> m/s
    "lateral_accel_g": "g_lat",       # g -> g
    "inline_accel_g": "g_long",       # g -> g
    "brake_press_psi": "brake_bar",   # psi -> bar
    "throttle_pos_pct": "throttle_pct", # % -> %
    "steer_angle_deg": "steering_deg", # deg -> deg
    "gps_lat": "lat",                 # deg -> deg
    "gps_lon": "lon"                  # deg -> deg
}

# Values from these channels need specific scaling/conversion before hitting the wide table
_CONVERSIONS = {
    "speed_mph": lambda v: v * 0.44704,           # mph to m/s
    "brake_press_psi": lambda v: v * 0.0689476,   # psi to bar
}

# Bidirectional channels mapped to unsigned slots that require signed recovery
_SIGNED_RECOVERY = {
    "roll_rate_degs", "pitch_rate_degs", "yaw_rate_degs", 
    "lateral_accel_g", "inline_accel_g", "vertical_accel_g", 
    "steer_angle_deg"
}

class CanReader:


    """Consumes a CAN bus, decodes via DBC, sinks into pitwall stores.

    Args:
        session_id: ingested rows are tagged with this id. The reserved
                    string `_live` is the conventional placeholder for
                    "no session in progress yet, but the bridge needs to
                    serve live values" (used by the PWA's Pit Stall Setup
                    screen). Live data accumulated against `_live` is
                    safe to discard between sessions.
        interface:  python-can interface name (`virtual`, `socketcan`,
                    `slcan`, `pcan`, …).
        channel:    interface-specific channel (e.g. `/dev/ttyACM0`,
                    `vcan0`, `pitwall_dev`).
        bitrate:    only used by interfaces that need it (slcan, socketcan).
        dbc_paths:  list of DBC files to load. First one is the primary;
                    subsequent ones extend the namespace (real-vehicle DBC
                    can be loaded alongside pitwall.dbc).
        flush_ms:   time-based wide-row flush interval (default 100 ms = 10 Hz).
        bridge:     optional reference to the loaded pitwall_bridge module.
                    If None, the reader will try to import it.
    """

    def __init__(
        self,
        session_id: str,
        *,
        interface: str = "virtual",
        channel: str = "pitwall_dev",
        bitrate: int = 1_000_000,
        dbc_paths: Optional[list[str]] = None,
        flush_ms: int = 100,
        bridge=None,
        log: Optional[logging.Logger] = None,
        dead_reckon: bool = True,
    ):
        self.session_id = session_id
        self.interface = interface
        self.channel = channel
        self.bitrate = bitrate
        self.flush_interval_s = flush_ms / 1000.0
        self.log = log or logging.getLogger("pitwall.can_reader")
        # ADR-018: smooth distance between 10 Hz GPS ticks using CAN speed
        # + IMU g_long. Wide-table writes use the filtered value; the raw
        # GPS distance still lands in the tall store as `gps_distance_m`
        # for diagnostics. Disable via dead_reckon=False on replay paths
        # that already trust their distance source (legacy VBO).
        self._dead_reckon = dead_reckon
        self._dr = DeadReckoner(DeadReckonerConfig()) if dead_reckon else None

        if bridge is not None:
            self._bridge = bridge
        elif _HAS_BRIDGE:
            self._bridge = br
        else:
            raise RuntimeError(
                "pitwall_bridge unavailable; can_reader needs it for DuckDB "
                "access. Run from the pitwall repo root.",
            )

        # Load DBC(s)
        paths = dbc_paths or [str(DEFAULT_DBC)]
        self._db = cantools.database.load_file(paths[0])
        for extra in paths[1:]:
            self._db.add_dbc_file(extra)

        self._bus: Optional[can.BusABC] = None
        self._stop = threading.Event()
        self._reader_thread: Optional[threading.Thread] = None
        self._flush_thread: Optional[threading.Thread] = None

        # Wide-row buffer + lock (only the reader thread mutates it; the
        # flush thread snapshots it under the lock)
        self._wide = _WideRow()
        self._wide_lock = threading.Lock()
        self._frame_idx = 0
        self._last_distance_m = -1.0

        # Pre-resolve signal_ids for tall-store names. Populated lazily on
        # first sighting to avoid a round-trip on every frame.
        self._tall_id_cache: dict[str, int] = {}

        # Stats for the Pit Stall Setup screen: rolling frames/sec,
        # unknown CAN IDs (frames whose arbitration_id isn't in any loaded
        # DBC). The PWA polls these via /signals/registry?include_can_state=true.
        self._stats_lock = threading.Lock()
        self._frames_total = 0
        self._frames_unknown = 0
        self._frames_window = []         # list[float] timestamps of recent frames
        self._unknown_ids: dict[int, int] = {}   # arbitration_id → count

    # ── public state accessor (read-only snapshot for pit-stall UI) ───────

    def state(self) -> dict:
        """Snapshot of CAN reader's runtime state.

        Surfaces what the bridge's `/signals/registry?include_can_state=true`
        endpoint exposes to the Pit Stall Setup screen: interface, channel,
        bitrate, frames/s (rolling 5-s window), known + unknown frame
        counts, the top unknown-ID list, the time since last frame, and
        a `connected` boolean (loaded AND fresh).
        """
        now = time.time()
        with self._stats_lock:
            recent = [t for t in self._frames_window if now - t <= 5.0]
            self._frames_window = recent
            fps = len(recent) / 5.0 if recent else 0.0
            last_frame = max(recent) if recent else 0.0
            unknown_top = sorted(
                self._unknown_ids.items(), key=lambda kv: kv[1], reverse=True,
            )[:10]
        last_frame_age_s: Optional[float] = (now - last_frame) if last_frame else None
        # "Connected" means: thread alive AND a frame arrived in the last 5 s.
        # Different from "loaded" (just thread alive — bus could be plugged in
        # but car ignition off, no frames flowing).
        loaded    = self._engine_alive()
        connected = loaded and (last_frame_age_s is not None and last_frame_age_s <= 5.0)
        return {
            "interface": self.interface,
            "channel":   self.channel,
            "bitrate":   self.bitrate,
            "session_id": self.session_id,
            "frames_total":   self._frames_total,
            "frames_unknown": self._frames_unknown,
            "frames_per_second": round(fps, 1),
            "last_frame_age_s":  None if last_frame_age_s is None else round(last_frame_age_s, 2),
            "unknown_ids": [
                {"can_id": f"0x{cid:x}", "count": cnt}
                for cid, cnt in unknown_top
            ],
            "loaded":    loaded,
            "connected": connected,
        }

    def _engine_alive(self) -> bool:
        return self._reader_thread is not None and self._reader_thread.is_alive()

    # ── lifecycle ─────────────────────────────────────────────────────────

    def start(self):
        """Open the bus + spawn reader and flush threads. Non-blocking."""
        self._open_bus()
        self._stop.clear()
        self._reader_thread = threading.Thread(
            target=self._reader_loop, name="pitwall-can-reader", daemon=True,
        )
        self._flush_thread = threading.Thread(
            target=self._flush_loop, name="pitwall-can-flush", daemon=True,
        )
        self._reader_thread.start()
        self._flush_thread.start()
        self.log.info(
            "can_reader started (interface=%s channel=%s session=%s)",
            self.interface, self.channel, self.session_id,
        )

    def stop(self, *, timeout: float = 2.0):
        """Signal threads to stop and close the bus."""
        self._stop.set()
        if self._bus is not None:
            try:
                self._bus.shutdown()
            except Exception:
                pass
        for t in (self._reader_thread, self._flush_thread):
            if t is not None:
                t.join(timeout=timeout)
        # Flush any pending wide row on shutdown
        self._flush_wide(force=True)

    def run_forever(self):
        """Blocking entrypoint for standalone CLI use."""
        self.start()
        try:
            while not self._stop.is_set():
                time.sleep(0.5)
        except KeyboardInterrupt:
            self.log.info("interrupt — shutting down")
        finally:
            self.stop()

    # ── bus + decode ─────────────────────────────────────────────────────

    def _open_bus(self):
        kwargs = {"interface": self.interface, "channel": self.channel}
        if self.interface in ("socketcan", "slcan", "pcan", "kvaser"):
            kwargs["bitrate"] = self.bitrate
        try:
            self._bus = can.Bus(**kwargs)
        except Exception as e:
            raise RuntimeError(
                f"failed to open CAN bus ({self.interface}/{self.channel}): {e}",
            ) from e

    def _reader_loop(self):
        assert self._bus is not None
        # Use explicit recv() with a small timeout so stop() can shut down
        # the bus cleanly without raising in the middle of an iter.
        while not self._stop.is_set():
            try:
                msg = self._bus.recv(timeout=0.5)
            except can.exceptions.CanOperationError:
                # bus closed by stop() — exit cleanly
                return
            if msg is None:
                continue
            # Record stats first so even unknown frames count toward fps.
            with self._stats_lock:
                self._frames_total += 1
                self._frames_window.append(time.time())
            try:
                decoded = self._db.decode_message(msg.arbitration_id, msg.data)
            except (KeyError, ValueError):
                with self._stats_lock:
                    self._frames_unknown += 1
                    self._unknown_ids[msg.arbitration_id] = (
                        self._unknown_ids.get(msg.arbitration_id, 0) + 1
                    )
                continue
            self._consume(msg.timestamp, decoded)

    def _consume(self, t: float, decoded: dict):
        """Route a decoded frame's signals to wide buffer and/or tall sink."""
        wide_updates = {}
        tall_signals = []
        for name, value in decoded.items():
            try:
                fvalue = float(value)
            except (TypeError, ValueError):
                continue
                
            # Apply AIM MXP signed recovery for unsigned slots
            if name in _SIGNED_RECOVERY and fvalue > 32767:
                # The raw value was scaled (e.g. * 10 or 100) before transmission.
                # Since the DBC slot is unsigned, cantools gives us (raw * scale).
                # Example: -1.0g raw=0xFFFF(65535). cantools gives 65535 * 0.01 = 655.35.
                # We need to subtract the max offset created by the scale.
                if "accel" in name:
                    fvalue -= 655.36  # 65536 * 0.01
                else:
                    fvalue -= 6553.6  # 65536 * 0.1

            # Apply AIM MXP mapping and conversions
            target_name = _CANONICAL_MAPPING.get(name, name)
            if target_name in _CONVERSIONS:
                fvalue = _CONVERSIONS[target_name](fvalue)
            elif name in _CONVERSIONS:
                fvalue = _CONVERSIONS[name](fvalue)

            if target_name in _WIDE_FIELDS:
                wide_updates[target_name] = fvalue
            
            # We always push to tall store with the original name for diagnostic fidelity
            tall_signals.append((name, t, fvalue))
            # And push mapped canonicals to tall store if they differ
            if target_name != name:
                 tall_signals.append((target_name, t, fvalue))

        # ADR-018: Kalman fusion for smooth distance.
        if self._dr is not None:
            advanced = False

            if "g_long" in wide_updates:
                self._dr.update_imu(t, wide_updates["g_long"])
                advanced = True
            if "speed_ms" in wide_updates:
                self._dr.update_speed(t, wide_updates["speed_ms"])
                advanced = True
            if "distance_m" in wide_updates:
                raw_d = wide_updates["distance_m"]
                self._dr.update_distance(t, raw_d)
                tall_signals.append(("gps_distance_m", t, raw_d))
                wide_updates["distance_m"] = self._dr.distance_m
                advanced = True
            elif advanced and self._dr.t is not None:
                self._dr.predict_to(t)
                wide_updates["distance_m"] = self._dr.distance_m

        if wide_updates:
            with self._wide_lock:
                self._wide.timestamp = t
                self._wide.seen_any = True
                for k, v in wide_updates.items():
                    setattr(self._wide, k, v)

        if tall_signals:
            self._sink_tall(tall_signals)


    # ── tall-store sink (ADR-015) ────────────────────────────────────────

    def _sink_tall(self, signals: list[tuple[str, float, float]]):
        if not signals:
            return
        with self._bridge.state.db_lock:
            conn = self._bridge.db.get_db()
            if conn is None:
                return
            rows = []
            for name, t, v in signals:
                sid_id = self._tall_id_cache.get(name)
                if sid_id is None:
                    sid_id = self._bridge.db.resolve_signal_id(conn, name)
                    self._tall_id_cache[name] = sid_id
                rows.append((self.session_id, sid_id, float(t), float(v)))
            try:
                conn.executemany(
                    """INSERT INTO telemetry_signals VALUES (?, ?, ?, ?)
                       ON CONFLICT (session_id, signal_id, t) DO UPDATE SET
                           value = excluded.value""",
                    rows,
                )
            finally:
                conn.close()

    # ── wide-table flush ─────────────────────────────────────────────────

    def _flush_loop(self):
        """Periodic 10 Hz flush of the wide buffer to the telemetry table."""
        while not self._stop.is_set():
            time.sleep(self.flush_interval_s)
            self._flush_wide()

    def _flush_wide(self, *, force: bool = False):
        with self._wide_lock:
            if not self._wide.seen_any:
                return
            # Skip duplicates: only flush if distance advanced or forced
            if not force and abs(self._wide.distance_m - self._last_distance_m) < 0.001:
                return
            row = (
                self.session_id, self._frame_idx, self._wide.timestamp,
                self._wide.distance_m, self._wide.speed_ms,
                self._wide.g_lat, self._wide.g_long, self._wide.combo_g,
                self._wide.brake_bar, self._wide.throttle_pct,
                self._wide.steering_deg, self._wide.rpm,
                self._wide.lat, self._wide.lon,
            )
            self._frame_idx += 1
            self._last_distance_m = self._wide.distance_m

        with self._bridge.state.db_lock:
            conn = self._bridge.db.get_db()
            if conn is None:
                return
            try:
                conn.execute(
                    "INSERT INTO telemetry VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    row,
                )
            finally:
                conn.close()


# ── CLI ─────────────────────────────────────────────────────────────────

def main():
    """CLI entry point — start the CAN reader as a standalone process."""
    p = argparse.ArgumentParser(description="pitwall CAN reader (USB/virtual)")
    p.add_argument("--session-id", required=True, help="session_id to tag rows with")
    p.add_argument("--interface", default="virtual",
                   help="python-can interface (virtual, socketcan, slcan, pcan, …)")
    p.add_argument("--channel", default="pitwall_dev",
                   help="interface channel (e.g. /dev/ttyACM0, vcan0, pitwall_dev)")
    p.add_argument("--bitrate", type=int, default=1_000_000,
                   help="CAN bus bitrate; AiM MXP CAN2 output runs at 1 Mbit/s")
    p.add_argument("--dbc", action="append", default=None,
                   help="DBC file(s) to load (default: data/dbc/pitwall.dbc). May repeat.")
    p.add_argument("--flush-ms", type=int, default=100,
                   help="wide-table flush cadence in ms (default 100 = 10 Hz)")
    p.add_argument("--verbose", action="store_true")
    args = p.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(name)s %(levelname)s %(message)s",
    )

    reader = CanReader(
        session_id=args.session_id,
        interface=args.interface,
        channel=args.channel,
        bitrate=args.bitrate,
        dbc_paths=args.dbc,
        flush_ms=args.flush_ms,
    )
    reader.run_forever()


if __name__ == "__main__":
    main()
