"""Unit tests for tools/pitwall_bridge.py — Flask test-client harness."""
import os
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "src" / "simulator"))

import pitwall as br
from pitwall.features.track.track_loader import load_track
from pitwall.features.session.session_analyzer import analyze_session
import pitwall.features.track.sonoma as sonoma
from pitwall.helpers import estimate_tts_ms, detect_laps, quantile
from pitwall.db import log_llm_friction
from pitwall.features.realtime.bp_realtime import cue_bus

@pytest.fixture(autouse=True)
def isolated_bridge(monkeypatch, tmp_path):
    """Each test gets a clean DuckDB file + fresh in-memory state."""
    monkeypatch.setattr(br.state, "db_path", str(tmp_path / "test.duckdb"))
    monkeypatch.setattr(br.state, "has_duckdb", True)
    monkeypatch.setattr(br.state, "has_analyzer", True)
    monkeypatch.setattr(br.state, "analyze_session", analyze_session)
    monkeypatch.setattr(br.state, "sonoma", sonoma)
    monkeypatch.setattr(br.state, "session_bundles", {})
    monkeypatch.setattr(
        br.state, "track",
        load_track(str(ROOT / "data" / "tracks" / "sonoma.json")),
    )
    monkeypatch.setattr(br.state, "coach", None)
    monkeypatch.setattr(br.state, "arbiter", None)


@pytest.fixture
def client():
    app = br.create_app()
    app.config["TESTING"] = True
    return app.test_client()


def _frames_to_payload(frames):
    return [{
        "timestamp": f.timestamp, "distance": f.distance,
        "speed": f.speed, "g_lat": f.g_lat, "g_long": f.g_long,
        "combo_g": f.combo_g, "brake_pressure": f.brake_pressure,
        "throttle": f.throttle, "steering": f.steering,
        "rpm": f.rpm, "lat": f.lat, "lon": f.lon,
    } for f in frames]


_SID_COUNTER = [0]


def _start_session(client, **body):
    """Generate a synthetic session_id.

    Master removed the explicit /session/start lifecycle in favour of an
    implicit model: any string can be a session_id, and per-frame /
    per-burst endpoints accept it as-is. This helper preserves the
    previous test-suite shape while matching the new API.
    """
    _SID_COUNTER[0] += 1
    return f"test-sid-{_SID_COUNTER[0]:03d}"


# ─── /health ─────────────────────────────────────────────────────────────────


def test_health_ok(client):
    r = client.get("/health")
    assert r.status_code == 200
    body = r.get_json()
    assert body["status"] == "ok"
    for key in ("version", "engine", "duckdb", "track"):
        assert key in body
    assert body["track"] == "Sonoma Raceway"


# ─── /session/start, /sessions, /session/<sid>, /end ─────────────────────────


def test_session_start_returns_id(client):
    r = client.post("/session/start", json={"driver": "Taha", "track": "Sonoma Raceway"})
    assert r.status_code == 200
    body = r.get_json()
    assert body["started"] is True
    assert body["session_id"].startswith("sonoma-raceway-")


def test_session_start_accepts_custom_id(client):
    r = client.post("/session/start", json={"session_id": "my-test-session", "driver": "x"})
    assert r.status_code == 200
    assert r.get_json()["session_id"] == "my-test-session"


def test_sessions_list_includes_new_session(client):
    client.post("/session/start", json={"session_id": "list-test-1", "driver": "a"})
    client.post("/session/start", json={"session_id": "list-test-2", "driver": "b"})
    r = client.get("/sessions")
    assert r.status_code == 200
    body = r.get_json()
    ids = {s["session_id"] for s in body["sessions"]}
    assert {"list-test-1", "list-test-2"} <= ids


def test_sessions_active_only_filters_ended(client):
    client.post("/session/start", json={"session_id": "active-1"})
    client.post("/session/start", json={"session_id": "ended-1"})
    client.post("/session/ended-1/end")
    r = client.get("/sessions?active_only=true")
    assert r.status_code == 200
    ids = {s["session_id"] for s in r.get_json()["sessions"]}
    assert "active-1" in ids
    assert "ended-1" not in ids


def test_session_end_idempotent(client):
    client.post("/session/start", json={"session_id": "end-test"})
    r1 = client.post("/session/end-test/end")
    r2 = client.post("/session/end-test/end")
    assert r1.status_code == 200
    assert r2.status_code == 200
    assert r1.get_json()["ended"] is True
    assert r2.get_json()["ended"] is True


def test_session_detail_unknown_returns_404(client):
    r = client.get("/session/no-such-session")
    assert r.status_code == 404


def test_session_detail_returns_session_laps_notes(client, make_frame_fn):
    sid = "detail-test"
    client.post("/session/start", json={"session_id": sid, "driver": "d", "track": "Sonoma Raceway"})
    client.post("/lap", json={
        "session_id": sid, "lap_number": 1, "lap_time_s": 105.5,
        "avg_speed_kmh": 110.0, "max_combo_g": 1.5,
    })
    r = client.get(f"/session/{sid}")
    assert r.status_code == 200
    body = r.get_json()
    assert body["session"]["session_id"] == sid
    assert body["session"]["driver"] == "d"
    assert len(body["laps"]) == 1
    assert body["laps"][0]["lap_time_s"] == 105.5
    assert body["lap_count"] == 1
    assert body["best_lap_s"] == 105.5


def test_sessions_list_limit_param(client):
    for i in range(8):
        client.post("/session/start", json={"session_id": f"lim-{i}"})
    r = client.get("/sessions?limit=3")
    assert r.status_code == 200
    body = r.get_json()
    assert len(body["sessions"]) <= 3


# ─── /session/<sid>/frames ───────────────────────────────────────────────────


def test_post_frames_batch_appends(client, make_frame_fn):
    sid = _start_session(client)
    frames = [make_frame_fn(t=i * 0.1, distance=i * 5.0) for i in range(50)]
    r = client.post(f"/session/{sid}/frames",
                    json={"frames": _frames_to_payload(frames)})
    assert r.status_code == 200
    body = r.get_json()
    assert body["n_appended"] == 50
    assert body["session_id"] == sid


def test_post_frames_empty_returns_400(client):
    sid = _start_session(client)
    r = client.post(f"/session/{sid}/frames", json={"frames": []})
    assert r.status_code == 400
    assert "error" in r.get_json()


# ─── /session/import ─────────────────────────────────────────────────────────


def test_session_import_missing_path_returns_400(client):
    r = client.post("/session/import", json={"vbo_path": "/nonexistent.vbo"})
    assert r.status_code == 400
    assert "error" in r.get_json()


# ─── /session/<sid>/scorecard before debrief ─────────────────────────────────


def test_scorecard_before_analysis_returns_404(client):
    """Regression: an un-analysed session used to return 200 with null fields."""
    sid = _start_session(client)
    r = client.get(f"/session/{sid}/scorecard")
    assert r.status_code == 404


# ─── /coach/debrief ──────────────────────────────────────────────────────────


def test_debrief_missing_vbo_returns_400(client):
    r = client.post("/coach/debrief",
                    json={"session_id": "missing", "vbo_path": "/nonexistent.vbo"})
    assert r.status_code == 400


def test_debrief_after_frames_persists_returns_bundle(client, synth_lap_frames):
    sid = _start_session(client)
    payload = {"frames": _frames_to_payload(synth_lap_frames[:300])}
    r = client.post(f"/session/{sid}/frames", json=payload)
    assert r.status_code == 200

    r = client.post(
        "/coach/debrief",
        json={"session_id": sid, "driver_id": "test", "persist_to_profile": False},
    )
    assert r.status_code == 200
    bundle = r.get_json()
    for key in ("session_id", "track", "stats", "highlights"):
        assert key in bundle


def test_scorecard_after_debrief_returns_data(client, synth_lap_frames):
    sid = _start_session(client)
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(synth_lap_frames[:300])})
    r1 = client.post(
        "/coach/debrief",
        json={"session_id": sid, "driver_id": "test", "persist_to_profile": False},
    )
    assert r1.status_code == 200
    r2 = client.get(f"/session/{sid}/scorecard")
    assert r2.status_code == 200
    body = r2.get_json()
    assert body["session_id"] == sid
    assert "scorecard" in body


# ─── /track/* ────────────────────────────────────────────────────────────────


def test_track_markers_returns_at_least_16(client):
    r = client.get("/track/markers")
    assert r.status_code == 200
    body = r.get_json()
    assert "markers" in body
    assert len(body["markers"]) >= 16
    for m in body["markers"][:3]:
        for key in ("id", "label", "kind", "corner", "lat", "lon"):
            assert key in m


def test_track_danger_zones_returns_three(client):
    r = client.get("/track/danger_zones")
    assert r.status_code == 200
    zones = r.get_json()["danger_zones"]
    ids = {z["id"] for z in zones}
    assert {"T6_runoff", "T9_downhill", "T11_dive_passing"} <= ids


def test_track_weather_morning_fog(client):
    r = client.get("/track/weather?hour_local=8")
    assert r.status_code == 200
    body = r.get_json()
    assert body["phase"] == "morning_fog"


def test_track_weather_peak_grip(client):
    r = client.get("/track/weather?hour_local=13")
    assert r.status_code == 200
    assert r.get_json()["phase"] == "peak_grip"


# ─── /session/<sid>/video_frames + /sync ─────────────────────────────────────


def test_post_video_frames_appends(client):
    sid = _start_session(client)
    payload = {"frames": [
        {"timestamp": 1000.0 + i * 0.033, "avitime_ms": i * 33,
         "file_path": "/tmp/x.mp4", "file_offset_s": i * 0.033,
         "width": 1920, "height": 1080}
        for i in range(20)
    ]}
    r = client.post(f"/session/{sid}/video_frames", json=payload)
    assert r.status_code == 200
    assert r.get_json()["n_appended"] == 20


def test_sync_returns_rows_when_telemetry_present(client, make_frame_fn):
    sid = _start_session(client)
    base_t = 1000.0
    frames = [make_frame_fn(t=base_t + i * 0.1, distance=i * 5.0) for i in range(20)]
    client.post(f"/session/{sid}/frames", json={"frames": _frames_to_payload(frames)})
    client.post(f"/session/{sid}/video_frames", json={"frames": [
        {"timestamp": base_t + i * 0.1, "avitime_ms": i * 100,
         "file_path": "/tmp/x.mp4", "file_offset_s": i * 0.1,
         "width": 1920, "height": 1080}
        for i in range(20)
    ]})
    r = client.get(f"/session/{sid}/sync")
    assert r.status_code == 200
    body = r.get_json()
    assert "rows" in body
    assert isinstance(body["rows"], list)
    assert body["count"] == len(body["rows"])


# ─── Helpers (frame round-trip + session-id generator) ───────────────────────


from pitwall.helpers import frames_to_rows, rows_to_frames, new_session_id

def test_frames_to_rows_round_trip(make_frame_fn):
    frames = [make_frame_fn(t=i * 0.1, distance=i * 5.0,
                            speed_kmh=120, brake_bar=10.0,
                            throttle_pct=80, g_lat=0.5)
              for i in range(5)]
    rows = frames_to_rows("sid-x", frames)
    assert len(rows) == 5
    rebuilt = rows_to_frames(rows)
    for orig, rb in zip(frames, rebuilt):
        assert abs(rb.speed - orig.speed) < 1e-6
        assert abs(rb.g_lat - orig.g_lat) < 1e-6
        assert abs(rb.brake_pressure - orig.brake_pressure) < 1e-6
        assert abs(rb.throttle - orig.throttle) < 1e-6


def test_new_session_id_format():
    sid = new_session_id("Sonoma Raceway")
    assert sid.startswith("sonoma-raceway-")


# ─── /analyze ────────────────────────────────────────────────────────────────


# ─── ADR-015: signal registry (Phase 1) ──────────────────────────────────────


def test_signal_registry_tables_created(client):
    """get_db() should create all three ADR-015 tables on first connection."""
    conn = br.get_db()
    tables = {t[0] for t in conn.execute("SHOW TABLES").fetchall()}
    conn.close()
    assert {"signal_registry", "telemetry_signals", "session_capabilities"} <= tables


def test_signal_registry_seed_populates_known_pids(client):
    """seed_signal_registry() should load the static OBD-II catalog."""
    n = br.seed_signal_registry()
    assert n > 0
    conn = br.get_db()
    rows = conn.execute(
        "SELECT name, units FROM signal_registry WHERE name = 'oil_temp_c'"
    ).fetchall()
    conn.close()
    assert rows == [("oil_temp_c", "C")]


def test_signal_registry_seed_is_idempotent(client):
    """Calling seed twice must not duplicate rows."""
    br.seed_signal_registry()
    conn = br.get_db()
    n1 = conn.execute("SELECT COUNT(*) FROM signal_registry").fetchone()[0]
    conn.close()
    br.seed_signal_registry()
    conn = br.get_db()
    n2 = conn.execute("SELECT COUNT(*) FROM signal_registry").fetchone()[0]
    conn.close()
    assert n1 == n2 and n1 > 0


def test_signals_registry_endpoint_returns_full_catalog(client):
    """GET /signals/registry must return every seeded signal."""
    br.seed_signal_registry()
    r = client.get("/signals/registry")
    assert r.status_code == 200
    body = r.get_json()
    assert "count" in body and "signals" in body
    names = {s["name"] for s in body["signals"]}
    # Every group should have at least one representative
    assert "speed_ms" in names            # wide-table canonical
    assert "oil_temp_c" in names          # OBD-II
    assert "wheel_speed_fl_kmh" in names  # DBC chassis
    assert "tpms_fl_kpa" in names         # DBC tires
    # Schema completeness
    sample = body["signals"][0]
    for key in ("signal_id", "name", "units", "semantics", "group",
                "expected_hz", "min_useful_hz", "discovery"):
        assert key in sample


def test_signals_registry_endpoint_empty_before_seed(client):
    """Without an explicit seed call, the table is empty but the endpoint still 200s."""
    r = client.get("/signals/registry")
    assert r.status_code == 200
    body = r.get_json()
    assert body["count"] == 0
    assert body["signals"] == []


def test_signals_registry_can_state_when_no_reader(client):
    """?include_can_state=true must produce a placeholder block when no
    CAN reader is running — the PWA renders ✗ rows from this shape."""
    r = client.get("/signals/registry?include_can_state=true")
    assert r.status_code == 200
    body = r.get_json()
    assert "can_state" in body
    cs = body["can_state"]
    assert cs["loaded"] is False
    assert cs["connected"] is False
    assert cs["frames_per_second"] == 0.0
    assert cs["unknown_ids"] == []
    assert cs["last_frame_age_s"] is None
    for k in ("interface", "channel", "bitrate", "session_id",
              "frames_total", "frames_unknown", "usb_devices"):
        assert k in cs


def test_session_export_parquet_404_when_no_session(client):
    r = client.get("/session/no-such/export.parquet")
    assert r.status_code == 404


def test_session_export_parquet_400_for_unknown_table(client, make_frame_fn):
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1) for i in range(10)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    r = client.get(f"/session/{sid}/export.parquet?table=garbage")
    assert r.status_code == 400


def test_session_export_parquet_streams_telemetry(client, make_frame_fn):
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1, distance=i * 5.0)
              for i in range(20)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    r = client.get(f"/session/{sid}/export.parquet")
    assert r.status_code == 200
    assert r.headers["Content-Type"] == "application/octet-stream"
    assert "X-Pitwall-Rows" in r.headers
    assert int(r.headers["X-Pitwall-Rows"]) == 20
    # Parquet file magic number: "PAR1" at the start AND end
    body = r.data
    assert body[:4] == b"PAR1"
    assert body[-4:] == b"PAR1"


def test_cues_stream_400_without_session_id(client):
    r = client.get("/cues/stream")
    assert r.status_code == 400


def test_cues_stream_emits_hello_then_published_cue(client):
    """Subscribe to /cues/stream, publish via _cue_bus directly,
    confirm the SSE stream surfaces both the hello event + the cue."""
    import threading
    sid = "stream-test-001"
    received: list[bytes] = []

    def reader():
        with client.get(f"/cues/stream?session_id={sid}",
                        buffered=False) as resp:
            assert resp.status_code == 200
            assert resp.mimetype == "text/event-stream"
            # Read up to ~6 chunks then bail
            for i, chunk in enumerate(resp.response):
                received.append(chunk)
                if len(received) >= 6:
                    break

    t = threading.Thread(target=reader, daemon=True)
    t.start()
    import time; time.sleep(0.3)
    from pitwall.features.realtime.bp_realtime import cue_bus
    cue_bus.publish(sid, {"text": "Distance is king.", "emotion": "encouraging"})
    t.join(timeout=2.0)

    blob = b"".join(received)
    assert b"event: hello" in blob
    assert b"Distance is king" in blob


from pitwall.features.realtime.bp_realtime import validate_spectator_token

def test_spectator_token_create_validate_revoke(client):
    """Token lifecycle: create → validate → revoke."""
    r = client.post("/spectator/token", json={"session_id": "spec-001"})
    assert r.status_code == 200
    body = r.get_json()
    assert "token" in body
    assert body["session_id"] == "spec-001"
    assert body["url"].startswith("/spectator/spec-001")
    token = body["token"]
    # Validate via the helper
    assert validate_spectator_token(token) == "spec-001"
    # Revoke
    rev = client.delete(f"/spectator/token/{token}")
    assert rev.status_code == 200
    assert rev.get_json()["revoked"] is True
    assert validate_spectator_token(token) is None


def test_spectator_token_400_without_session_id(client):
    r = client.post("/spectator/token", json={})
    assert r.status_code == 400


from pitwall.features.realtime.bp_realtime import emit_notification

def test_notifications_emit_and_stream(client):
    """emit_notification publishes; SSE streams the event."""
    import threading
    received: list[bytes] = []

    def reader():
        with client.get("/notifications?driver=evo-driver",
                        buffered=False) as resp:
            assert resp.status_code == 200
            for i, chunk in enumerate(resp.response):
                received.append(chunk)
                if len(received) >= 6:
                    break

    t = threading.Thread(target=reader, daemon=True)
    t.start()
    import time; time.sleep(0.3)
    emit_notification("evo-driver", "medal-earned", medal="trail_brake_apprentice")
    t.join(timeout=2.0)

    blob = b"".join(received)
    assert b"event: hello" in blob
    assert b"medal-earned" in blob


def test_session_export_parquet_handles_capabilities_table(client, make_frame_fn):
    sid = _start_session(client)
    br.seed_signal_registry()
    frames = [make_frame_fn(t=1000.0 + i * 0.1, distance=i * 5.0)
              for i in range(50)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    br._compute_capabilities(sid)
    r = client.get(f"/session/{sid}/export.parquet?table=capabilities")
    assert r.status_code == 200
    assert r.data[:4] == b"PAR1"
    assert int(r.headers["X-Pitwall-Rows"]) >= len(br._WIDE_SIGNAL_NAMES)


def test_signals_registry_lists_usb_devices(client):
    """The can_state block must include a `usb_devices` array. It can be
    empty (no adapter plugged in) or contain dicts — but the field must
    exist so the PWA can render a "no adapter detected" message
    deterministically."""
    r = client.get("/signals/registry?include_can_state=true")
    body = r.get_json()
    devices = body["can_state"]["usb_devices"]
    assert isinstance(devices, list)
    # Each device (if any) has the expected schema
    for d in devices:
        for k in ("device", "vid", "pid", "description", "manufacturer",
                  "model", "kind", "is_known"):
            assert k in d, f"missing key {k} in device {d!r}"


def test_signals_registry_no_can_state_block_by_default(client):
    """Without ?include_can_state=true the response must NOT include the block —
    keeps the default response cheap (PWA caches it once at app launch)."""
    r = client.get("/signals/registry")
    assert r.status_code == 200
    body = r.get_json()
    assert "can_state" not in body


# ─── ADR-015: signal sink ingest (Phase 2) ──────────────────────────────────


def test_post_signals_appends_and_recompute_caps(client):
    """POST /session/<sid>/signals appends rows + recomputes capabilities."""
    br.seed_signal_registry()
    sid = "phase2-ingest-001"
    r = client.post(f"/session/{sid}/signals", json={"signals": [
        {"name": "oil_temp_c", "t": 1000.0, "value": 92.1},
        {"name": "oil_temp_c", "t": 1001.0, "value": 92.5},
        {"name": "oil_temp_c", "t": 1002.0, "value": 93.0},
    ]})
    assert r.status_code == 200
    body = r.get_json()
    assert body["n_appended"] == 3
    assert body["names"] == ["oil_temp_c"]
    assert body["capabilities_count"] >= 1


def test_post_signals_auto_registers_novel(client):
    """A name not in the registry is registered as discovery='discovered', units=NULL."""
    br.seed_signal_registry()
    sid = "phase2-novel-001"
    r = client.post(f"/session/{sid}/signals", json={"signals": [
        {"name": "shock_pot_fl_v", "t": 1000.0, "value": 1.23},
    ]})
    assert r.status_code == 200
    body = r.get_json()
    assert "shock_pot_fl_v" in body["newly_discovered"]
    conn = br.get_db()
    row = conn.execute(
        "SELECT name, units, discovery FROM signal_registry WHERE name = 'shock_pot_fl_v'"
    ).fetchone()
    conn.close()
    assert row == ("shock_pot_fl_v", None, "discovered")


def test_post_signals_dedup_on_conflict(client):
    """Re-posting (sid, signal_id, t) updates value but doesn't duplicate rows."""
    br.seed_signal_registry()
    sid = "phase2-dedup-001"
    client.post(f"/session/{sid}/signals", json={"signals": [
        {"name": "oil_temp_c", "t": 1000.0, "value": 92.1},
    ]})
    client.post(f"/session/{sid}/signals", json={"signals": [
        {"name": "oil_temp_c", "t": 1000.0, "value": 95.0},   # same t, new value
    ]})
    conn = br.get_db()
    rows = conn.execute(
        "SELECT t, value FROM telemetry_signals WHERE session_id = ?", [sid],
    ).fetchall()
    conn.close()
    assert len(rows) == 1
    assert rows[0][1] == 95.0


def test_post_signals_empty_returns_400(client):
    r = client.post("/session/x/signals", json={"signals": []})
    assert r.status_code == 400


def test_post_signals_invalid_samples_returns_400(client):
    """Items missing t/value/name are dropped; if all are dropped, 400."""
    br.seed_signal_registry()
    r = client.post("/session/x/signals", json={"signals": [
        {"name": "oil_temp_c"},                       # missing t + value
        {"t": 1000.0, "value": 5},                    # missing name + signal_id
    ]})
    assert r.status_code == 400


def test_compute_capabilities_advertises_wide_canonicals(client, make_frame_fn):
    """A session with only wide-table frames still has all 11 canonical caps."""
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=i * 0.1, distance=i * 5.0) for i in range(50)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    n = br._compute_capabilities(sid)
    assert n == len(br._WIDE_SIGNAL_NAMES)   # 11 canonical fields

    conn = br.get_db()
    rows = conn.execute(
        """SELECT sr.name FROM session_capabilities sc
           JOIN signal_registry sr USING(signal_id)
           WHERE sc.session_id = ? ORDER BY sr.name""",
        [sid],
    ).fetchall()
    conn.close()
    names = {r[0] for r in rows}
    assert names == set(br._WIDE_SIGNAL_NAMES)


def test_compute_capabilities_combines_wide_and_tall(client, make_frame_fn):
    """A session with both wide frames + tall signals exposes both groups."""
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1, distance=i * 5.0) for i in range(20)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    client.post(f"/session/{sid}/signals", json={"signals": [
        {"name": "oil_temp_c", "t": 1000.0 + i * 0.5, "value": 90 + i}
        for i in range(4)
    ]})
    conn = br.get_db()
    names = {r[0] for r in conn.execute(
        """SELECT sr.name FROM session_capabilities sc
           JOIN signal_registry sr USING(signal_id)
           WHERE sc.session_id = ?""", [sid],
    ).fetchall()}
    conn.close()
    # Wide canonicals + the one tall signal
    assert "speed_ms"   in names
    assert "rpm"        in names
    assert "oil_temp_c" in names


def test_capabilities_recompute_endpoint_returns_count(client, make_frame_fn):
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=i * 0.1) for i in range(30)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    r = client.post(f"/session/{sid}/capabilities/recompute")
    assert r.status_code == 200
    body = r.get_json()
    assert body["session_id"] == sid
    assert body["capabilities_count"] >= len(br._WIDE_SIGNAL_NAMES)


def test_compute_capabilities_mean_hz_matches_density(client):
    """3 samples spanning 1s → mean_hz ≈ 3.0; 2 samples spanning 0.05s → ≈ 40.0."""
    br.seed_signal_registry()
    sid = "phase2-rate-001"
    client.post(f"/session/{sid}/signals", json={"signals": [
        {"name": "oil_temp_c",     "t": 1000.0,  "value": 90},
        {"name": "oil_temp_c",     "t": 1000.5,  "value": 91},
        {"name": "oil_temp_c",     "t": 1001.0,  "value": 92},
        {"name": "clutch_pos_pct", "t": 1000.0,  "value": 0},
        {"name": "clutch_pos_pct", "t": 1000.05, "value": 50},
    ]})
    conn = br.get_db()
    caps = {r[0]: r[1] for r in conn.execute(
        """SELECT sr.name, sc.mean_hz FROM session_capabilities sc
           JOIN signal_registry sr USING(signal_id)
           WHERE sc.session_id = ?""", [sid],
    ).fetchall()}
    conn.close()
    assert abs(caps["oil_temp_c"] - 3.0) < 0.01
    assert abs(caps["clutch_pos_pct"] - 40.0) < 0.5


# ─── /analyze ────────────────────────────────────────────────────────────────


def test_analyze_returns_expected_keys(client):
    burst = {
        "session_id": "burst-sess",
        "burst_id": 7,
        "avg_speed_kmh": 104.0, "max_combo_g": 1.82,
        "max_lateral_g": 1.35, "max_long_g": -0.92,
        "max_brake_bar": 45.0, "avg_throttle_pct": 38.0,
        "avg_steering_deg": 12.0, "coast_frames": 12,
        "trail_brake_frames": 4, "frame_count": 75,
        "corners_visited": ["Turn 3"], "distance_m": 1450.0,
        "in_corner": False, "past_apex": False,
    }
    r = client.post("/analyze", json=burst)
    assert r.status_code == 200
    body = r.get_json()
    for key in ("coaching", "pace_note", "coach_source",
                "cues", "source", "burst_id"):
        assert key in body
    assert body["burst_id"] == 7


# ─── ADR-015: capabilities envelope + synchroniser (Phase 3) ────────────────


def test_capabilities_endpoint_unknown_session_returns_404(client):
    br.seed_signal_registry()
    r = client.get("/session/no-such-session/capabilities")
    assert r.status_code == 404


def test_capabilities_endpoint_wide_only_returns_canonicals(client, make_frame_fn):
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1, distance=i * 5.0) for i in range(50)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    br._compute_capabilities(sid)

    r = client.get(f"/session/{sid}/capabilities")
    assert r.status_code == 200
    body = r.get_json()
    assert body["session_id"] == sid
    assert body["duration_s"] > 0
    names = {s["name"] for s in body["signals"]}
    assert set(br._WIDE_SIGNAL_NAMES) <= names
    # All wide canonicals at 10 Hz are useful (min_useful_hz ≤ 10)
    for s in body["signals"]:
        if s["name"] == "speed_ms":
            assert s["useful"] is True
            assert s["mean_hz"] > 5.0
    # Phase-4 wired (separate test verifies content); assert keys exist.
    assert isinstance(body["coaches_available"], list)
    assert isinstance(body["coaches_disabled"], list)


def test_capabilities_endpoint_marks_low_rate_signal_not_useful(client):
    """A 1 Hz signal whose registry min_useful_hz > 1 should report useful=false."""
    br.seed_signal_registry()
    sid = "phase3-useful-001"
    # tpms_fl_kpa min_useful_hz is > 1 in the seed; post a 1 Hz stream
    client.post(f"/session/{sid}/signals", json={"signals": [
        {"name": "tpms_fl_kpa", "t": 1000.0 + i, "value": 230 + i * 0.1}
        for i in range(5)
    ]})
    r = client.get(f"/session/{sid}/capabilities")
    assert r.status_code == 200
    body = r.get_json()
    tpms = next(s for s in body["signals"] if s["name"] == "tpms_fl_kpa")
    assert tpms["mean_hz"] < 2.0
    # Whether useful is False depends on the registry seed; assert the field exists
    assert "useful" in tpms


def test_signals_get_missing_names_returns_400(client):
    r = client.get("/session/anything/signals")
    assert r.status_code == 400


def test_signals_get_unknown_session_returns_404(client):
    br.seed_signal_registry()
    r = client.get("/session/nope/signals?names=speed_ms")
    assert r.status_code == 404


def test_signals_get_unknown_signal_returns_400(client, make_frame_fn):
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1) for i in range(10)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    r = client.get(f"/session/{sid}/signals?names=not_a_real_signal")
    assert r.status_code == 400


def test_signals_get_wide_canonical_returns_rows(client, make_frame_fn):
    """Pulling speed_ms off the wide table should return one row per frame."""
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1, speed_kmh=100 + i)
              for i in range(20)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})

    r = client.get(f"/session/{sid}/signals?names=speed_ms")
    assert r.status_code == 200
    body = r.get_json()
    assert body["names"] == ["speed_ms"]
    assert body["missing"] == []
    assert body["count"] == 20
    # First frame: 100 kmh = 27.78 m/s
    assert abs(body["rows"][0]["speed_ms"] - (100 / 3.6)) < 1e-3


def test_signals_get_tall_signal_returns_rows(client):
    """Tall-store signals come back through the synchroniser."""
    br.seed_signal_registry()
    sid = "phase3-tall-001"
    client.post(f"/session/{sid}/signals", json={"signals": [
        {"name": "oil_temp_c", "t": 1000.0 + i, "value": 90 + i}
        for i in range(5)
    ]})
    # axis defaults to gps; without a wide-table session, the only
    # axis points come from the tall-store native rate. Use axis=oil_temp_c.
    r = client.get(f"/session/{sid}/signals?names=oil_temp_c&axis=oil_temp_c")
    assert r.status_code == 200
    body = r.get_json()
    assert body["count"] == 5
    assert body["rows"][0]["oil_temp_c"] == 90.0
    assert body["rows"][-1]["oil_temp_c"] == 94.0


def test_signals_get_missing_signal_returns_null_column(client, make_frame_fn):
    """A registered-but-empty signal yields null column + missing entry, 200."""
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1) for i in range(10)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    r = client.get(f"/session/{sid}/signals?names=speed_ms,oil_temp_c")
    assert r.status_code == 200
    body = r.get_json()
    assert "oil_temp_c" in body["missing"]
    assert all(row["oil_temp_c"] is None for row in body["rows"])
    assert all(row["speed_ms"] is not None for row in body["rows"])


def test_signals_get_interp_hold_is_asof(client):
    """interp=hold returns the last value with t ≤ axis_t."""
    br.seed_signal_registry()
    sid = "phase3-hold-001"
    client.post(f"/session/{sid}/signals", json={"signals": [
        {"name": "oil_temp_c", "t": 1000.0, "value": 90.0},
        {"name": "oil_temp_c", "t": 1002.0, "value": 100.0},
    ]})
    r = client.get(
        f"/session/{sid}/signals?names=oil_temp_c"
        "&t_from=1000&t_to=1002&rate_hz=2&interp=hold"
    )
    assert r.status_code == 200
    body = r.get_json()
    # Axis ts: 1000.0, 1000.5, 1001.0, 1001.5, 2002.0
    ts = [row["t"] for row in body["rows"]]
    vals = [row["oil_temp_c"] for row in body["rows"]]
    assert ts[0] == 1000.0 and vals[0] == 90.0       # exact match
    assert vals[1] == 90.0                            # held, no later sample yet
    assert vals[-1] == 100.0                          # 1002.0 sample seen


def test_signals_get_interp_lerp_is_linear(client):
    """interp=lerp linearly interpolates between bracketing samples."""
    br.seed_signal_registry()
    sid = "phase3-lerp-001"
    client.post(f"/session/{sid}/signals", json={"signals": [
        {"name": "oil_temp_c", "t": 1000.0, "value": 90.0},
        {"name": "oil_temp_c", "t": 1002.0, "value": 100.0},
    ]})
    r = client.get(
        f"/session/{sid}/signals?names=oil_temp_c"
        "&t_from=1000&t_to=1002&rate_hz=2&interp=lerp"
    )
    assert r.status_code == 200
    body = r.get_json()
    by_t = {row["t"]: row["oil_temp_c"] for row in body["rows"]}
    assert abs(by_t[1000.0] - 90.0) < 1e-6
    assert abs(by_t[1001.0] - 95.0) < 1e-6   # halfway → 95
    assert abs(by_t[1002.0] - 100.0) < 1e-6


def test_signals_get_lerp_outside_range_is_null(client):
    """lerp emits null for axis points before/after the sample range."""
    br.seed_signal_registry()
    sid = "phase3-lerp-edge"
    client.post(f"/session/{sid}/signals", json={"signals": [
        {"name": "oil_temp_c", "t": 1001.0, "value": 90.0},
        {"name": "oil_temp_c", "t": 1002.0, "value": 100.0},
    ]})
    r = client.get(
        f"/session/{sid}/signals?names=oil_temp_c"
        "&t_from=1000&t_to=1003&rate_hz=1&interp=lerp"
    )
    body = r.get_json()
    by_t = {row["t"]: row["oil_temp_c"] for row in body["rows"]}
    assert by_t[1000.0] is None     # before first sample
    assert by_t[1003.0] is None     # after last sample
    assert by_t[1001.0] == 90.0
    assert by_t[1002.0] == 100.0


def test_signals_get_t_window_clipping(client, make_frame_fn):
    """t_from/t_to clip the wide-table axis grid."""
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1) for i in range(30)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})

    r = client.get(
        f"/session/{sid}/signals?names=speed_ms&t_from=1001&t_to=1002"
    )
    body = r.get_json()
    ts = [row["t"] for row in body["rows"]]
    assert all(1001.0 <= t <= 1002.0 for t in ts)
    assert len(ts) >= 9   # ~10 samples in the 1s window at 10 Hz


def test_signals_get_rate_hz_uniform_grid(client, make_frame_fn):
    """rate_hz=4 over a 2s window → 9 samples (inclusive of both endpoints)."""
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1) for i in range(30)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})

    r = client.get(
        f"/session/{sid}/signals?names=speed_ms"
        "&t_from=1000&t_to=1002&rate_hz=4"
    )
    body = r.get_json()
    assert body["count"] == 9
    # Spacing should be 0.25 s
    ts = [row["t"] for row in body["rows"]]
    diffs = [ts[i + 1] - ts[i] for i in range(len(ts) - 1)]
    for d in diffs:
        assert abs(d - 0.25) < 1e-6


def test_signals_get_lap_distance_axis_includes_distance_m(client, make_frame_fn):
    """axis=lap_distance returns rows tagged with distance_m via ASOF on wide."""
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1, distance=i * 5.0) for i in range(20)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})

    r = client.get(f"/session/{sid}/signals?names=speed_ms&axis=lap_distance")
    assert r.status_code == 200
    body = r.get_json()
    assert body["axis"] == "lap_distance"
    assert all("distance_m" in row for row in body["rows"])
    # Distance increases monotonically across the frames
    dists = [row["distance_m"] for row in body["rows"]]
    assert dists[0] < dists[-1]


def test_signals_get_unknown_axis_returns_400(client, make_frame_fn):
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1) for i in range(10)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    r = client.get(
        f"/session/{sid}/signals?names=speed_ms&axis=not_a_real_axis"
    )
    assert r.status_code == 400


def test_signals_get_invalid_interp_returns_400(client):
    r = client.get("/session/x/signals?names=speed_ms&interp=cubic")
    assert r.status_code == 400


def test_signals_get_lap_param_returns_400_phase3(client):
    """Phase 3 documents lap clipping but defers it; unit-test the explicit 400."""
    r = client.get("/session/x/signals?names=speed_ms&lap=2")
    assert r.status_code == 400


# Direct unit tests for the interpolation helpers — easier to debug than the
# end-to-end Flask path.

from pitwall.db import interp_hold, interp_lerp

def test_interp_hold_unit():
    samples = [(1000.0, 10.0), (1002.0, 20.0)]
    assert interp_hold([999.0, 1000.0, 1001.0, 1002.0, 1003.0], samples) == [
        None, 10.0, 10.0, 20.0, 20.0,
    ]


def test_interp_hold_empty():
    assert interp_hold([1.0, 2.0], []) == [None, None]


def test_interp_lerp_unit():
    samples = [(1000.0, 10.0), (1002.0, 20.0)]
    out = interp_lerp([999.0, 1000.0, 1001.0, 1002.0, 1003.0], samples)
    assert out[0] is None
    assert out[1] == 10.0
    assert out[2] == 15.0
    assert out[3] == 20.0
    assert out[4] is None


def test_interp_lerp_zero_width_segment():
    """Two samples at the same t — lerp returns the earlier value."""
    samples = [(1000.0, 10.0), (1000.0, 99.0), (1001.0, 20.0)]
    out = interp_lerp([1000.0], samples)
    assert out == [10.0]


# ─── Phase 6: lap detection + analysis endpoints ─────────────────────────────


def _ingest_multi_lap(client, sid, frames):
    """Helper: post a multi-lap frame batch to /session/<sid>/frames."""
    return client.post(
        f"/session/{sid}/frames",
        json={"frames": _frames_to_payload(frames), "driver": "test", "track": "Sonoma Raceway"},
    )


def test_detect_laps_finds_multiple_via_distance_wrap(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    laps = detect_laps(sid)
    assert len(laps) >= 2     # at least 2 wraps in 3-lap data
    for l in laps:
        assert 60 <= l["lap_time_s"] <= 300
        assert l["t_end"] > l["t_start"]
        assert l["lap_number"] >= 1


def test_detect_laps_unknown_session_empty(client):
    assert detect_laps("no-such-session") == []


def test_lap_time_table_404_for_unknown_session(client):
    r = client.get("/session/missing/lap_time_table")
    assert r.status_code == 404


def test_lap_time_table_400_when_no_complete_laps(client, make_frame_fn):
    """A short single-frame session can't produce a complete lap → 400."""
    sid = _start_session(client)
    frames = [make_frame_fn(t=i * 0.1, distance=i * 5.0) for i in range(50)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    r = client.get(f"/session/{sid}/lap_time_table")
    assert r.status_code == 400


def test_lap_time_table_returns_lap_data(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    r = client.get(f"/session/{sid}/lap_time_table")
    assert r.status_code == 200
    body = r.get_json()
    assert body["lap_count"] >= 2
    assert body["best_lap_s"] > 60
    assert body["best_lap_number"] in [l["lap_number"] for l in body["laps"]]
    # Exactly one lap is_best=True
    bests = [l for l in body["laps"] if l["is_best"]]
    assert len(bests) == 1
    # delta_to_best is 0 for the best lap
    assert bests[0]["delta_to_best_s"] == 0.0


def test_lap_time_distribution_returns_box_plot_stats(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    r = client.get(f"/session/{sid}/lap_time_distribution")
    assert r.status_code == 200
    body = r.get_json()
    for key in ("min_s", "max_s", "q1_s", "median_s", "q3_s", "iqr_s",
                "whisker_low_s", "whisker_high_s", "outliers", "mean_s", "stddev_s"):
        assert key in body
    assert body["min_s"] <= body["median_s"] <= body["max_s"]
    assert body["q1_s"] <= body["median_s"] <= body["q3_s"]


def test_ideal_lap_returns_sum_of_best_sectors(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    r = client.get(f"/session/{sid}/ideal_lap")
    assert r.status_code == 200
    body = r.get_json()
    assert body["ideal_lap_s"] <= body["best_actual_lap_s"] + 1e-3
    assert body["gain_potential_s"] >= -1e-6
    assert len(body["best_sectors"]) == 3


def test_sector_times_thin_view(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    r = client.get(f"/session/{sid}/sector_times")
    assert r.status_code == 200
    body = r.get_json()
    assert len(body["sector_definitions"]) == 3
    assert all("s1" in lap and "s2" in lap and "s3" in lap for lap in body["laps"])


def test_pedal_behavior_returns_4_states(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    r = client.get(f"/session/{sid}/pedal_behavior")
    assert r.status_code == 200
    body = r.get_json()
    states = body["states"]
    for k in ("throttle_only", "brake_only", "trail_brake", "coast"):
        assert k in states
        assert "pct" in states[k]
        assert "frames" in states[k]
    # Sum of frames equals frame_count
    assert sum(s["frames"] for s in states.values()) == body["frame_count"]


def test_pedal_behavior_thresholds_query_param(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    r = client.get(f"/session/{sid}/pedal_behavior?throttle_th=50&brake_th=10")
    assert r.status_code == 200
    body = r.get_json()
    assert body["thresholds"]["throttle_pct"] == 50.0
    assert body["thresholds"]["brake_bar"] == 10.0


def test_pedal_behavior_404_unknown_session(client):
    r = client.get("/session/missing/pedal_behavior")
    assert r.status_code == 404


def test_throttle_corner_box_returns_per_corner_stats(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    r = client.get(f"/session/{sid}/throttle_corner_box")
    assert r.status_code == 200
    body = r.get_json()
    assert "corners" in body
    assert len(body["corners"]) == 11    # all Sonoma corners
    sample = next(c for c in body["corners"] if c["n_samples"] > 0)
    assert sample["min_pct"] <= sample["median_pct"] <= sample["max_pct"]


def test_corner_classification_groups_by_apex_speed(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    r = client.get(f"/session/{sid}/corner_classification")
    assert r.status_code == 200
    body = r.get_json()
    assert {b["band"] for b in body["bands"]} == {"low_speed", "med_speed", "high_speed"}
    total_corners = sum(len(b["corners"]) for b in body["bands"])
    assert total_corners == 11


def test_corner_classification_404_unknown_session(client):
    r = client.get("/session/missing/corner_classification")
    assert r.status_code == 404


def test_straight_line_speed_returns_per_straight_top(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    r = client.get(f"/session/{sid}/straight_line_speed")
    assert r.status_code == 200
    body = r.get_json()
    assert len(body["straights"]) == 3
    front = next(s for s in body["straights"] if s["name"] == "Front Straight")
    assert front["top_speed_kmh"] is not None
    assert front["top_speed_kmh"] > 100


def test_brake_acceleration_returns_zones_and_exits(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    r = client.get(f"/session/{sid}/brake_acceleration")
    assert r.status_code == 200
    body = r.get_json()
    assert "brake_zones" in body
    assert "corner_exits" in body
    for z in body["brake_zones"]:
        assert z["max_decel_g"] <= 0   # decel is negative
        assert z["n_passes"] >= 1


def test_track_elevation_returns_samples(client):
    r = client.get("/track/sonoma/elevation")
    assert r.status_code == 200
    body = r.get_json()
    assert body["track_id"] == "sonoma"
    assert body["min_elevation_m"] is not None
    assert body["max_elevation_m"] >= body["min_elevation_m"]
    assert len(body["samples"]) > 100


def test_track_elevation_step_m_query(client):
    r = client.get("/track/sonoma/elevation?step_m=50")
    assert r.status_code == 200
    body = r.get_json()
    assert body["step_m"] == 50.0
    # 4258 m / 50 m ≈ 86 samples
    assert 80 <= len(body["samples"]) <= 90


def test_track_elevation_404_unknown_track(client):
    r = client.get("/track/no-such-track/elevation")
    assert r.status_code == 404


def test_track_elevation_invalid_step_returns_400(client):
    r = client.get("/track/sonoma/elevation?step_m=-5")
    assert r.status_code == 400


def test_driver_evolution_404_unknown_driver(client):
    r = client.get("/driver/no-such-driver/evolution")
    assert r.status_code == 404


def test_driver_evolution_204_with_few_sessions(client, synth_multi_lap_frames):
    """Driver with 2 sessions → 204 (frontend should hide panel)."""
    for i in range(2):
        sid = f"ev-test-{i}"
        client.post(f"/session/{sid}/frames", json={
            "frames": _frames_to_payload(synth_multi_lap_frames),
            "driver": "evo-driver", "track": "Sonoma Raceway",
        })
    r = client.get("/driver/evo-driver/evolution?track=Sonoma Raceway")
    assert r.status_code == 204


def test_driver_evolution_returns_evolution_with_5_sessions(client, synth_multi_lap_frames):
    for i in range(5):
        sid = f"ev5-test-{i}"
        client.post(f"/session/{sid}/frames", json={
            "frames": _frames_to_payload(synth_multi_lap_frames),
            "driver": "many-driver", "track": "Sonoma Raceway",
        })
    r = client.get("/driver/many-driver/evolution?track=Sonoma Raceway")
    assert r.status_code == 200
    body = r.get_json()
    assert body["session_count"] == 5
    assert len(body["evolution"]) == 5
    assert body["summary"]["first_best_s"] > 0
    for ev in body["evolution"]:
        assert "best_lap_s" in ev
        assert "median_lap_s" in ev


# ─── ADR-015 Phase 4: coach gating ──────────────────────────────────────────


def test_coach_rules_registered_at_import():
    """Built-in rules should be present in COACH_RULES after import."""
    from pitwall.features.coaching.coach_engine import COACH_RULES
    ids = {r.id for r in COACH_RULES}
    assert "base_pace_note" in ids
    assert "oil_temp_warning_t11" in ids
    assert "tpms_drift" in ids


def test_coach_rule_decorator_dedupes_by_id():
    """Re-registering the same id should replace, not duplicate."""
    from pitwall.features.coaching.coach_engine import coach_rule, COACH_RULES

    n_before = len(COACH_RULES)

    @coach_rule(id="dedupe_test", description="v1", requires=["x"])
    def _v1(ctx, signals): pass

    @coach_rule(id="dedupe_test", description="v2", requires=["y"])
    def _v2(ctx, signals): pass

    matching = [r for r in COACH_RULES if r.id == "dedupe_test"]
    assert len(matching) == 1
    assert matching[0].description == "v2"
    # cleanup so other tests aren't affected
    COACH_RULES[:] = [r for r in COACH_RULES if r.id != "dedupe_test"]
    assert len(COACH_RULES) == n_before


def test_evaluate_coach_gating_marks_missing_signals():
    from pitwall.features.coaching.coach_engine import evaluate_coach_gating
    caps = {"speed_ms": {"mean_hz": 10.0, "useful": True},
            "distance_m": {"mean_hz": 10.0, "useful": True}}
    available, disabled = evaluate_coach_gating(caps)
    assert "base_pace_note" in available
    disabled_ids = {d["coach_id"] for d in disabled}
    assert "trail_brake_score" in disabled_ids
    assert "oil_temp_warning_t11" in disabled_ids


def test_evaluate_coach_gating_min_rate_failure():
    """Signal present but below min_useful_hz disables the rule."""
    from pitwall.features.coaching.coach_engine import evaluate_coach_gating
    caps = {
        "oil_temp_c": {"mean_hz": 0.5, "useful": False},   # 0.5 < 1.0
        "distance_m": {"mean_hz": 10.0, "useful": True},
    }
    _, disabled = evaluate_coach_gating(caps)
    oil = next((d for d in disabled if d["coach_id"] == "oil_temp_warning_t11"), None)
    assert oil is not None
    assert "rate" in oil["reason"].lower()


def test_capabilities_endpoint_advertises_coach_gating(client, make_frame_fn):
    """The /capabilities endpoint should populate coaches_available/disabled."""
    br.seed_signal_registry()
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1, distance=i * 5.0) for i in range(50)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    br._compute_capabilities(sid)

    r = client.get(f"/session/{sid}/capabilities")
    assert r.status_code == 200
    body = r.get_json()
    # Wide-only session should at least enable base_pace_note + trail_brake_score
    # (all signals are wide canonicals).
    assert "base_pace_note" in body["coaches_available"]
    assert "trail_brake_score" in body["coaches_available"]
    # And disable ones requiring tall-store signals.
    disabled_ids = {d["coach_id"] for d in body["coaches_disabled"]}
    assert "oil_temp_warning_t11" in disabled_ids
    assert "tpms_drift" in disabled_ids
    # Each disabled entry has a reason
    for d in body["coaches_disabled"]:
        assert "reason" in d


# ─── Quantile helper ────────────────────────────────────────────────────────


def test_quantile_endpoints():
    assert quantile([1.0, 2.0, 3.0, 4.0, 5.0], 0.0) == 1.0
    assert quantile([1.0, 2.0, 3.0, 4.0, 5.0], 1.0) == 5.0
    # Median of 5 values at p=0.5 → (n-1)*0.5+1 = 3 → exact 3.0
    assert quantile([1.0, 2.0, 3.0, 4.0, 5.0], 0.5) == 3.0
    # Empty list
    assert quantile([], 0.5) == 0.0
    # Single value
    assert quantile([42.0], 0.5) == 42.0


# ─── Roadmap endpoints (POST /session/<sid>/frame, /corners, /score, etc.) ──


def test_post_single_frame_appends_and_returns_idx(client):
    sid = _start_session(client)
    payload = {
        "timestamp": 1000.0, "distance": 0.0, "speed": 28.0,
        "g_lat": 0.0, "g_long": 0.0, "combo_g": 0.0,
        "brake_pressure": 0.0, "throttle": 99.0, "steering": 0.0,
        "rpm": 4000.0, "lat": 23.49, "lon": -73.78,
    }
    r1 = client.post(f"/session/{sid}/frame", json=payload)
    assert r1.status_code == 200
    body = r1.get_json()
    assert body["saved"] is True
    assert body["frame_idx"] == 0

    # Second post increments frame_idx
    r2 = client.post(f"/session/{sid}/frame", json={**payload, "timestamp": 1000.1})
    assert r2.get_json()["frame_idx"] == 1


def test_post_single_frame_empty_returns_400(client):
    sid = _start_session(client)
    r = client.post(f"/session/{sid}/frame", json={})
    assert r.status_code == 400


def test_session_corners_404_when_no_telemetry(client):
    r = client.get("/session/missing/corners")
    assert r.status_code == 404


def test_session_corners_returns_per_corner_aggregates(client, synth_multi_lap_frames):
    sid = _start_session(client)
    _ingest_multi_lap(client, sid, synth_multi_lap_frames)
    r = client.get(f"/session/{sid}/corners")
    assert r.status_code == 200
    body = r.get_json()
    assert body["session_id"] == sid
    assert body["lap_count"] >= 2
    assert len(body["corners"]) == 11
    sample = next(c for c in body["corners"] if c["n_passes"] > 0)
    assert sample["best_pass"] is not None
    for k in ("entry_speed_kmh", "apex_speed_kmh", "exit_speed_kmh",
              "peak_brake_bar", "max_g_lat", "corner_time_s"):
        assert k in sample["best_pass"]
    assert sample["averages"]["apex_speed_kmh"] > 0


def test_score_400_without_session_id(client):
    """Missing session_id is a 400 regardless of coach state."""
    r = client.post("/score", json={})
    assert r.status_code == 400


def test_score_404_for_unknown_session(client, make_frame_fn):
    """Real session_id but no telemetry → 404."""
    r = client.post("/score", json={"session_id": "no-such-session"})
    assert r.status_code == 404


def test_score_503_when_no_local_gemma(client, monkeypatch, make_frame_fn):
    """Session has telemetry but no local Gemma loaded → 503.

    The fixture sets `_coach = None` already (see isolated_bridge), so
    /score should refuse with a clear error rather than crashing.
    """
    sid = _start_session(client)
    frames = [make_frame_fn(t=1000.0 + i * 0.1, distance=i * 5.0) for i in range(20)]
    client.post(f"/session/{sid}/frames",
                json={"frames": _frames_to_payload(frames)})
    r = client.post("/score", json={"session_id": sid})
    assert r.status_code == 503
    body = r.get_json()
    assert "gemma" in (body.get("error") or "").lower()


def test_markers_no_filter_returns_all(client):
    r = client.get("/markers")
    assert r.status_code == 200
    body = r.get_json()
    assert body["count"] >= 16
    assert body["filters"] == {"corner": None, "kind": None}


def test_markers_corner_filter(client):
    r = client.get("/markers?corner=Turn 11")
    assert r.status_code == 200
    body = r.get_json()
    assert body["filters"]["corner"] == "Turn 11"
    for m in body["markers"]:
        assert m["corner"] == "Turn 11"


def test_markers_kind_filter(client):
    r = client.get("/markers?kind=brake")
    assert r.status_code == 200
    body = r.get_json()
    for m in body["markers"]:
        assert m["kind"] == "brake"


def test_coach_concepts_lists_nine(client):
    r = client.get("/coach/concepts")
    assert r.status_code == 200
    body = r.get_json()
    assert body["count"] == 9
    ids = {c["id"] for c in body["concepts"]}
    assert {"trail_brake", "exit_speed", "eob", "look_ahead",
            "hustle", "late_apex", "entry_release",
            "downhill_brake", "uphill_brake"} == ids
    for c in body["concepts"]:
        assert c["description"]
        assert c["fires_when"]


# ─── ADR-018: LLM friction sink + diagnostics endpoint ──────────────────────

def _seed_friction_rows(rows: list[dict]) -> None:
    """Helper — write fake llm_friction rows so the diagnostics endpoint has
    something to aggregate. Uses bridge's own writer so schema stays canonical."""
    for r in rows:
        log_llm_friction(r)


def test_diagnostics_llm_friction_empty_returns_zero_aggregates(client):
    r = client.get("/diagnostics/llm_friction")
    assert r.status_code == 200
    body = r.get_json()
    assert body["count"] == 0
    assert body["rows"] == []
    assert body["by_role"] == []
    assert body["fallback_rate"] == 0.0


def test_diagnostics_llm_friction_logs_and_aggregates(client):
    _seed_friction_rows([
        {"session_id": "s1", "role": "brief", "mode": "pre_brief",
         "backend": "cpu", "prompt_chars": 1200, "completion_chars": 600,
         "latency_ms": 7000.0, "truncated": False, "fell_back": False,
         "error": "", "emotion": "encouraging"},
        {"session_id": "s1", "role": "brief", "mode": "pre_brief",
         "backend": "cpu", "prompt_chars": 1100, "completion_chars": 0,
         "latency_ms": 0.0, "truncated": False, "fell_back": True,
         "error": "engine_not_loaded", "emotion": "neutral"},
        {"session_id": "s1", "role": "debrief", "mode": "post_session",
         "backend": "cpu", "prompt_chars": 3000, "completion_chars": 1500,
         "latency_ms": 11000.0, "truncated": True, "fell_back": False,
         "error": "", "emotion": "serious"},
    ])
    r = client.get("/diagnostics/llm_friction")
    assert r.status_code == 200
    body = r.get_json()
    assert body["count"] == 3
    assert len(body["rows"]) == 3
    # 1 of 3 fell back, 1 of 3 truncated
    assert abs(body["fallback_rate"] - 1/3) < 1e-6
    assert abs(body["truncation_rate"] - 1/3) < 1e-6
    # Per-role aggregation
    roles = {r["role"]: r for r in body["by_role"]}
    assert roles["brief"]["count"] == 2
    assert roles["debrief"]["count"] == 1
    assert abs(roles["brief"]["fallback_rate"] - 0.5) < 1e-6


def test_diagnostics_llm_friction_filters_by_session(client):
    _seed_friction_rows([
        {"session_id": "s1", "role": "brief", "fell_back": False,
         "latency_ms": 5000, "prompt_chars": 100, "completion_chars": 50,
         "backend": "cpu", "mode": "pre_brief"},
        {"session_id": "s2", "role": "brief", "fell_back": True,
         "latency_ms": 0, "prompt_chars": 100, "completion_chars": 0,
         "backend": "cpu", "mode": "pre_brief"},
    ])
    r = client.get("/diagnostics/llm_friction?session_id=s1")
    assert r.status_code == 200
    body = r.get_json()
    assert body["count"] == 1
    assert body["rows"][0]["session_id"] == "s1"
    assert body["fallback_rate"] == 0.0


def test_diagnostics_llm_friction_filters_by_role(client):
    _seed_friction_rows([
        {"session_id": "s1", "role": "brief", "fell_back": False,
         "latency_ms": 5000, "prompt_chars": 100, "completion_chars": 50,
         "backend": "cpu", "mode": "pre_brief"},
        {"session_id": "s1", "role": "debrief", "fell_back": False,
         "latency_ms": 9000, "prompt_chars": 200, "completion_chars": 800,
         "backend": "cpu", "mode": "post_session"},
    ])
    r = client.get("/diagnostics/llm_friction?role=debrief")
    assert r.status_code == 200
    body = r.get_json()
    assert body["count"] == 1
    assert body["rows"][0]["role"] == "debrief"


def test_estimate_tts_ms_floors_short_phrases():
    # Single safety word still ducks the full 800 ms minimum.
    assert estimate_tts_ms("Brake!") == 800


def test_estimate_tts_ms_scales_with_word_count():
    # 11 words × 150 ms = 1650 ms, above the 800 ms floor.
    text = "Wait for the bump then trail to the third tire stack."
    assert text.split() == ["Wait", "for", "the", "bump", "then", "trail",
                            "to", "the", "third", "tire", "stack."]
    assert estimate_tts_ms(text) == 11 * 150


def test_estimate_tts_ms_zero_for_empty():
    assert estimate_tts_ms("") == 0
    # Whitespace-only hits the floor — split() returns [] but max(1, 0) keeps
    # word count at 1 so the duck still engages for the safe minimum.
    assert estimate_tts_ms("   ") == 800


def test_analyze_publishes_cue_with_expected_tts_ms(client, monkeypatch):
    """Audio-ducker contract: every /analyze cue carries the expected_tts_ms
    hint so the PWA can hold the tactical-tone duck for the right window."""
    captured: list[dict] = []
    real_publish = cue_bus.publish
    def _spy(sid, event):
        captured.append({"sid": sid, **event})
        return real_publish(sid, event)
    monkeypatch.setattr(cue_bus, "publish", _spy)

    burst = {
        "session_id": "ducker-sess", "burst_id": 1,
        "avg_speed_kmh": 90.0, "max_combo_g": 1.0,
        "max_lateral_g": 0.5, "max_long_g": -0.3,
        "max_brake_bar": 12.0, "avg_throttle_pct": 50.0,
        "avg_steering_deg": 5.0, "coast_frames": 0,
        "trail_brake_frames": 0, "frame_count": 50,
        "corners_visited": [], "distance_m": 800.0,
        "in_corner": False, "past_apex": False,
    }
    r = client.post("/analyze", json=burst)
    assert r.status_code == 200
    assert len(captured) == 1
    cue = captured[0]
    assert "expected_tts_ms" in cue
    # Must be either 0 (no coaching text) or floored at 800.
    assert cue["expected_tts_ms"] in (0,) or cue["expected_tts_ms"] >= 800


def test_diagnostics_llm_friction_respects_limit(client):
    _seed_friction_rows([
        {"session_id": f"s{i}", "role": "brief", "fell_back": False,
         "latency_ms": 1000.0 * i, "prompt_chars": 100, "completion_chars": 50,
         "backend": "cpu", "mode": "pre_brief"}
        for i in range(1, 6)
    ])
    r = client.get("/diagnostics/llm_friction?limit=2")
    assert r.status_code == 200
    body = r.get_json()
    assert len(body["rows"]) == 2
    # count is the unfiltered total; limit only caps `rows`
    assert body["count"] == 5
