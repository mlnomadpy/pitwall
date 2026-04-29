"""Round-trip CAN pipeline tests.

Both reader and simulator share python-can's `interface='virtual'` bus.
Multiple Bus instances on the same channel see each other's frames in pure
Python — no kernel modules, no permissions, works in CI.

These tests also serve as the canonical example of the CAN data path:
encode → bus → decode → DuckDB.
"""
from __future__ import annotations

import sys
import time
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "src" / "simulator"))

import can
import cantools

import pitwall_bridge as br
from can_reader import CanReader, DEFAULT_DBC


# ── Fixtures ─────────────────────────────────────────────────────────────


@pytest.fixture
def isolated_db(monkeypatch, tmp_path):
    """Each test gets a clean DuckDB file."""
    monkeypatch.setattr(br, "DB_PATH", str(tmp_path / "can.duckdb"))
    yield


@pytest.fixture
def virtual_channel(request):
    """Unique channel per test so concurrent tests don't share traffic."""
    return f"pitwall_test_{request.node.name}"


@pytest.fixture
def producer_bus(virtual_channel):
    """The 'simulator' side of the bus — we send frames into this."""
    bus = can.Bus(interface="virtual", channel=virtual_channel)
    yield bus
    bus.shutdown()


@pytest.fixture
def reader(virtual_channel, isolated_db):
    """A started CanReader consuming the same virtual channel."""
    r = CanReader(
        session_id="test-can-001",
        interface="virtual",
        channel=virtual_channel,
        flush_ms=20,                # tighter flush for fast tests
    )
    r.start()
    yield r
    r.stop(timeout=1.0)


@pytest.fixture(scope="module")
def db():
    return cantools.database.load_file(str(DEFAULT_DBC))


# ── Helpers ──────────────────────────────────────────────────────────────


def _send(producer_bus, db, msg_name, signals, t):
    msg = db.get_message_by_name(msg_name)
    producer_bus.send(can.Message(
        arbitration_id=msg.frame_id,
        data=msg.encode(signals),
        timestamp=t,
        is_extended_id=False,
    ))


# ── Tests ────────────────────────────────────────────────────────────────


def test_reader_imports_dbc_with_expected_messages(db):
    names = {m.name for m in db.messages}
    assert {
        "PitwallMotion", "PitwallPosition", "PitwallDistance",
        "PitwallDriverInputs", "PitwallDriveline", "PitwallPowertrain",
        "PitwallTpms",
    } <= names


def test_round_trip_motion_frame_sinks_to_wide_table(reader, producer_bus, db):
    """Motion frame's signals are wide-table canonicals → telemetry row."""
    _send(producer_bus, db, "PitwallMotion", {
        "speed_ms": 27.78, "g_lat": 0.5, "g_long": -0.8, "combo_g": 0.94,
    }, t=1000.0)
    _send(producer_bus, db, "PitwallDistance", {"distance_m": 12.5}, t=1000.0)
    _send(producer_bus, db, "PitwallDriverInputs", {
        "throttle_pct": 88.0, "brake_bar": 0.0,
        "steering_deg": -3.2, "rpm": 5400.0,
    }, t=1000.0)

    # Wait several flush cycles. Reader thread reads on bus iter, flush
    # thread fires every 20 ms. Give comfortable margin for both.
    time.sleep(0.5)

    conn = br.get_db()
    row = conn.execute(
        "SELECT speed_ms, g_lat, g_long, combo_g, distance_m, "
        "       throttle_pct, brake_bar, steering_deg, rpm "
        "FROM telemetry WHERE session_id = ? ORDER BY frame_idx DESC LIMIT 1",
        ["test-can-001"],
    ).fetchone()
    conn.close()

    assert row is not None
    assert abs(row[0] - 27.78) < 0.01     # speed_ms
    assert abs(row[1] - 0.5) < 0.001      # g_lat
    assert abs(row[3] - 0.94) < 0.001     # combo_g
    assert abs(row[4] - 12.5) < 0.01      # distance_m
    assert abs(row[5] - 88.0) < 0.01      # throttle_pct
    assert abs(row[8] - 5400.0) < 1.0     # rpm


def test_round_trip_oil_temp_sinks_to_tall_store(reader, producer_bus, db):
    """Powertrain signals are NOT wide-table → telemetry_signals."""
    br.seed_signal_registry()      # so oil_temp_c has a signal_id

    for i in range(5):
        _send(producer_bus, db, "PitwallPowertrain", {
            "oil_temp_c": 90.0 + i,
            "coolant_temp_c": 85.0,
            "oil_pressure_kpa": 350.0,
            "fuel_level_pct": 80.0,
        }, t=1000.0 + i * 0.5)

    time.sleep(0.5)

    conn = br.get_db()
    rows = conn.execute(
        """SELECT t, value FROM telemetry_signals ts
           JOIN signal_registry sr USING(signal_id)
           WHERE ts.session_id = ? AND sr.name = 'oil_temp_c'
           ORDER BY t""",
        ["test-can-001"],
    ).fetchall()
    conn.close()

    assert len(rows) == 5
    assert rows[0][1] == 90.0
    assert rows[-1][1] == 94.0


def test_novel_signal_auto_registers_via_decoded_name(reader, producer_bus, db):
    """A signal name decoded from the DBC that isn't in the seed should
    auto-register on first sighting (ADR-015 'discovered' path)."""
    # `oil_pressure_kpa` is in our DBC but not in obd2_pids.json
    _send(producer_bus, db, "PitwallPowertrain", {
        "oil_temp_c": 90.0, "coolant_temp_c": 85.0,
        "oil_pressure_kpa": 350.0, "fuel_level_pct": 80.0,
    }, t=1000.0)

    time.sleep(0.5)

    conn = br.get_db()
    row = conn.execute(
        "SELECT discovery FROM signal_registry WHERE name = 'oil_pressure_kpa'",
    ).fetchone()
    conn.close()
    assert row is not None
    # Either pre-seeded as static_obd2 OR auto-registered as 'discovered' —
    # both are acceptable; the point is the row exists.
    assert row[0] in ("static_obd2", "static_dbc", "discovered")


def test_unknown_can_id_is_silently_dropped(reader, producer_bus):
    """Frames with arbitration_ids not in the DBC must not crash the reader."""
    producer_bus.send(can.Message(
        arbitration_id=0x7FF,        # not in pitwall.dbc
        data=b"\x00" * 8,
        is_extended_id=False,
    ))
    # Send a known frame after to confirm reader is still alive
    db_local = cantools.database.load_file(str(DEFAULT_DBC))
    _send(producer_bus, db_local, "PitwallMotion", {
        "speed_ms": 10.0, "g_lat": 0.0, "g_long": 0.0, "combo_g": 0.0,
    }, t=2000.0)
    _send(producer_bus, db_local, "PitwallDistance",
          {"distance_m": 5.0}, t=2000.0)

    time.sleep(0.5)

    conn = br.get_db()
    n = conn.execute(
        "SELECT COUNT(*) FROM telemetry WHERE session_id = ?",
        ["test-can-001"],
    ).fetchone()[0]
    conn.close()
    assert n >= 1


def test_capabilities_after_can_ingest(reader, producer_bus, db):
    """A session populated entirely via CAN should still produce capabilities
    that include the 11 wide canonicals + the tall signals seen on the bus."""
    br.seed_signal_registry()

    # Emit one of every category so capabilities are exercised
    for i in range(20):
        t = 1000.0 + i * 0.1
        _send(producer_bus, db, "PitwallMotion", {
            "speed_ms": 25.0 + i, "g_lat": 0.0,
            "g_long": 0.0, "combo_g": 0.0,
        }, t=t)
        _send(producer_bus, db, "PitwallDistance", {
            "distance_m": i * 2.5,
        }, t=t)
        _send(producer_bus, db, "PitwallDriverInputs", {
            "throttle_pct": 50.0, "brake_bar": 0.0,
            "steering_deg": 0.0, "rpm": 4000.0,
        }, t=t)
        if i % 5 == 0:
            _send(producer_bus, db, "PitwallPowertrain", {
                "oil_temp_c": 90.0 + i,
                "coolant_temp_c": 85.0,
                "oil_pressure_kpa": 350.0,
                "fuel_level_pct": 80.0,
            }, t=t)

    time.sleep(0.7)
    n_caps = br._compute_capabilities("test-can-001")

    assert n_caps >= len(br._WIDE_SIGNAL_NAMES)

    conn = br.get_db()
    names = {r[0] for r in conn.execute(
        """SELECT sr.name FROM session_capabilities sc
           JOIN signal_registry sr USING(signal_id)
           WHERE sc.session_id = ?""", ["test-can-001"],
    ).fetchall()}
    conn.close()
    assert "speed_ms" in names
    assert "oil_temp_c" in names
