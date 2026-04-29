"""Unit tests for tools/pitwall_bridge.py — Flask test-client harness."""
import os
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "src" / "simulator"))

import pitwall_bridge as br
from track_loader import load_track


@pytest.fixture(autouse=True)
def isolated_bridge(monkeypatch, tmp_path):
    """Each test gets a clean DuckDB file + fresh in-memory state."""
    monkeypatch.setattr(br, "DB_PATH", str(tmp_path / "test.duckdb"))
    monkeypatch.setattr(br, "_session_bundles", {})
    monkeypatch.setattr(
        br, "_track",
        load_track(str(ROOT / "data" / "tracks" / "sonoma.json")),
    )
    monkeypatch.setattr(br, "_coach", None)
    monkeypatch.setattr(br, "_arbiter", None)


@pytest.fixture
def client():
    br.app.config["TESTING"] = True
    return br.app.test_client()


def _frames_to_payload(frames):
    return [{
        "timestamp": f.timestamp, "distance": f.distance,
        "speed": f.speed, "g_lat": f.g_lat, "g_long": f.g_long,
        "combo_g": f.combo_g, "brake_pressure": f.brake_pressure,
        "throttle": f.throttle, "steering": f.steering,
        "rpm": f.rpm, "lat": f.lat, "lon": f.lon,
    } for f in frames]


def _start_session(client, **body):
    payload = {"driver": "Taha", "driver_level": "intermediate",
               "car": "M3", **body}
    r = client.post("/session/start", json=payload)
    assert r.status_code == 200
    return r.get_json()["session_id"]


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
    r = client.post(
        "/session/start",
        json={"driver": "Taha", "driver_level": "intermediate", "car": "M3"},
    )
    assert r.status_code == 200
    body = r.get_json()
    assert body["started"] is True
    assert isinstance(body["session_id"], str)
    assert len(body["session_id"]) > 0


def test_sessions_list_includes_new_session(client):
    sid = _start_session(client)
    r = client.get("/sessions")
    assert r.status_code == 200
    body = r.get_json()
    ids = [s["session_id"] for s in body["sessions"]]
    assert sid in ids


def test_sessions_active_only_filters_ended(client):
    """active_only=true should drop sessions with ended_at stamped."""
    sid_active = _start_session(client, driver="A")
    sid_done = _start_session(client, driver="B")
    client.post(f"/session/{sid_done}/end")

    r = client.get("/sessions?active_only=true")
    assert r.status_code == 200
    ids = [s["session_id"] for s in r.get_json()["sessions"]]
    assert sid_active in ids
    assert sid_done not in ids


def test_session_end_idempotent(client):
    sid = _start_session(client)
    r1 = client.post(f"/session/{sid}/end")
    r2 = client.post(f"/session/{sid}/end")
    assert r1.status_code == 200
    assert r2.status_code == 200
    assert r1.get_json()["ended"] is True
    assert r2.get_json()["ended"] is True


def test_session_detail_unknown_returns_404(client):
    r = client.get("/session/nonexistent-sid-xyz")
    assert r.status_code == 404
    assert "error" in r.get_json()


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


def test_frames_to_rows_round_trip(make_frame_fn):
    frames = [make_frame_fn(t=i * 0.1, distance=i * 5.0,
                            speed_kmh=120, brake_bar=10.0,
                            throttle_pct=80, g_lat=0.5)
              for i in range(5)]
    rows = br._frames_to_rows("sid-x", frames)
    assert len(rows) == 5
    rebuilt = br._rows_to_frames(rows)
    for orig, rb in zip(frames, rebuilt):
        assert abs(rb.speed - orig.speed) < 1e-6
        assert abs(rb.g_lat - orig.g_lat) < 1e-6
        assert abs(rb.brake_pressure - orig.brake_pressure) < 1e-6
        assert abs(rb.throttle - orig.throttle) < 1e-6


def test_new_session_id_format():
    sid = br._new_session_id("Sonoma Raceway")
    assert sid.startswith("sonoma-raceway-")


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
