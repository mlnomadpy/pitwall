#!/usr/bin/env python3
"""
pitwall_bridge.py — Local HTTP bridge server for the Pitwall Android app.

Integrates the full src/simulator coaching stack (sonic_model, track_loader,
vbo_parser) so Android gets real cues — not stub rules.

Endpoints:
    GET  /health           → {"status": "ok", "engine": "sonic_model" | "rules"}
    POST /analyze          → telemetry burst JSON → coaching message + cues
    GET  /laps             → lap history from DuckDB
    POST /lap              → save a completed lap record

Install:
    pip3 install flask duckdb

Run from repo root (so imports resolve):
    python3 tools/pitwall_bridge.py --track src/simulator/sonoma.json

Emulator tunnel (once per adb session):
    ~/Library/Android/sdk/platform-tools/adb reverse tcp:8765 tcp:8765
"""

import argparse
import os
import sys
import threading
import json
from datetime import datetime
try:
    from google import genai
    HAS_GENAI = True
except ImportError:
    HAS_GENAI = False

# Load .env file
env_path = os.path.join(os.path.dirname(__file__), "..", ".env")
if os.path.exists(env_path):
    with open(env_path) as f:
        for line in f:
            if line.strip() and not line.startswith("#"):
                key, val = line.strip().split("=", 1)
                os.environ[key.strip()] = val.strip().strip('"\'')

from flask import Flask, request, jsonify

# ── Add src/simulator to path so we can import the real coaching engine ────────
SIM_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "simulator")
sys.path.insert(0, os.path.abspath(SIM_DIR))

try:
    from sonic_model import compute_cues, AudioCue, Pattern
    from track_loader import load_track, find_nearest_corner, distance_to_corner
    HAS_SONIC = True
    print("✓  sonic_model loaded")
except ImportError as e:
    HAS_SONIC = False
    print(f"⚠  sonic_model not available ({e}) — falling back to rule engine")

try:
    from coach_engine import make_coach, CoachArbiter, build_context
    _coach = make_coach(kind="auto")
    _arbiter = CoachArbiter()
    print(f"✓  coach_engine loaded ({_coach.name})")
except ImportError as e:
    _coach = None
    _arbiter = None
    print(f"⚠  coach_engine not available ({e})")

try:
    import duckdb
    HAS_DUCKDB = True
except ImportError:
    HAS_DUCKDB = False
    print("⚠  duckdb not installed — lap history disabled. pip3 install duckdb")

# ── Global state ───────────────────────────────────────────────────────────────
app = Flask(__name__)
_track = None           # loaded on startup via --track
_db_lock = threading.Lock()
DB_PATH = os.path.join(os.path.dirname(__file__), "pitwall_sessions.duckdb")

# Session burst accumulator — cleared by POST /session/reset
_session_bursts: list = []
_burst_lock = threading.Lock()


# ── DuckDB helpers ─────────────────────────────────────────────────────────────
def get_db():
    if not HAS_DUCKDB:
        return None
    conn = duckdb.connect(DB_PATH)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS laps (
            id            INTEGER PRIMARY KEY,
            session_id    VARCHAR,
            lap_number    INTEGER,
            lap_time_s    DOUBLE,
            best_sector   DOUBLE,
            avg_speed_kmh DOUBLE,
            max_combo_g   DOUBLE,
            coast_pct     DOUBLE,
            recorded_at   TIMESTAMP DEFAULT now()
        )
    """)
    return conn


# ── Coaching engine ────────────────────────────────────────────────────────────

def _cues_to_coaching(cues: list) -> str:
    """Convert AudioCue list from sonic_model into a single coaching sentence."""
    if not cues:
        return "Smooth sector. Maintain pace."

    # Sort by priority descending — highest priority cue drives the message
    top = sorted(cues, key=lambda c: c.priority, reverse=True)[0]
    reason = top.reason

    # Map patterns + layers to brief coaching language
    pat = top.pattern.value if hasattr(top.pattern, "value") else str(top.pattern)
    layer = top.layer

    if pat == "buzz":
        return f"OVER GRIP LIMIT — ease off. {reason}"
    if layer == "grip" and top.priority >= 2:
        return f"At grip limit. {reason}"
    if layer == "brake_approach":
        return f"Brake marker. {reason}"
    if layer == "trail_brake":
        return f"Trail brake — hold pressure. {reason}"
    if layer == "throttle":
        return f"Throttle now. {reason}"
    if layer == "lap_estimate":
        return reason

    return reason[:120] if reason else "Stay focused."


def _sonic_coaching(burst: dict) -> tuple[str, list]:
    """
    Run the real sonic_model over the burst's representative frame data.
    Returns (coaching_text, serialised_cues).
    """
    if not HAS_SONIC:
        return None, []

    # Reconstruct a representative frame at the peak-stress snapshot.
    # Use MAX values for safety-critical channels (G, brake) so the
    # coaching engine fires on the worst moment within the burst.
    frame = {
        "speed":             burst.get("avg_speed_kmh", 0) / 3.6,   # → m/s
        "g_lat":             burst.get("max_lateral_g", burst.get("max_combo_g", 0) * 0.7),
        "g_long":            burst.get("max_long_g",    burst.get("max_combo_g", 0) * 0.7),
        "combo_g":           burst.get("max_combo_g", 0),           # peak, not avg
        "brake_pressure":    burst.get("max_brake_bar", 0),         # peak
        "throttle":          burst.get("avg_throttle_pct", 0),
        "steering":          burst.get("avg_steering_deg", 0),
        "distance_to_corner": burst.get("dist_to_next_corner_m", 50),  # assume near corner
        "corner_severity":   burst.get("next_corner_severity", 3),
        "past_apex":         burst.get("past_apex", False),
        "in_corner":         burst.get("in_corner", len(burst.get("corners_visited", [])) > 0),
    }

    # Also compute with track context if available
    corners = burst.get("corners_visited", [])
    if _track and corners:
        nearest = find_nearest_corner(_track, burst.get("distance_m", 0))
        if nearest:
            frame["distance_to_corner"] = distance_to_corner(
                _track, burst.get("distance_m", 0), nearest
            )
            frame["corner_severity"] = nearest.severity

    cues = compute_cues(frame, prev_frame=None)
    coaching = _cues_to_coaching(cues)

    # Serialise cues for the response payload
    serialised = [
        {
            "layer":     c.layer,
            "frequency": c.frequency,
            "volume":    c.volume,
            "pattern":   c.pattern.value if hasattr(c.pattern, "value") else str(c.pattern),
            "priority":  c.priority,
            "reason":    c.reason,
        }
        for c in cues
    ]
    return coaching, serialised


def _rule_coaching(burst: dict) -> str:
    """Fallback rule engine — used when sonic_model is unavailable."""
    avg_speed    = burst.get("avg_speed_kmh", 0)
    max_g        = burst.get("max_combo_g", 0)
    coast_frames = burst.get("coast_frames", 0)
    trail_frames = burst.get("trail_brake_frames", 0)
    frame_count  = burst.get("frame_count", 1)
    corners      = burst.get("corners_visited", [])

    coast_pct = coast_frames / max(frame_count, 1) * 100

    if max_g > 2.1:
        return "Back off — at grip limit. Smooth inputs only."
    if coast_pct > 25:
        corner = corners[0] if corners else "next corner"
        return f"Coasting {coast_pct:.0f}% of sector. Get on throttle earlier through {corner}."
    if trail_frames > 0 and max_g > 1.5:
        return "Good trail braking. Carry that commitment to the next corner."
    if avg_speed < 60:
        return "Below 60 km/h avg — check braking points, you may be braking too early."
    if avg_speed > 120:
        return "Strong sector pace. Stay smooth on the wheel."
    return f"Sector avg {avg_speed:.0f} km/h, max {max_g:.2f}G. Focus on exit speed — throttle at apex."


# ── Routes ─────────────────────────────────────────────────────────────────────

@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status":    "ok",
        "version":   "2.0",
        "engine":    "sonic_model" if HAS_SONIC else "rules",
        "track":     _track.name if _track else None,
        "duckdb":    HAS_DUCKDB,
        "timestamp": datetime.utcnow().isoformat(),
    })


@app.route("/analyze", methods=["POST"])
def analyze():
    """
    Receive a telemetry burst from AntigravityPipeline.kt and return coaching.

    Expected JSON (from serialiseBurst in AntigravityPipeline):
    {
      "session_id": "...",  "burst_id": 7,
      "avg_speed_kmh": 104, "max_combo_g": 1.82,
      "max_brake_bar": 45,  "avg_throttle_pct": 38,
      "coast_frames": 12,   "trail_brake_frames": 4,
      "frame_count": 75,    "corners_visited": ["Turn 3"],
      "distance_m": 1450,   "in_corner": false
    }
    """
    burst = request.get_json(force=True, silent=True) or {}
    burst_id = burst.get("burst_id", 0)

    # Accumulate burst for /insights scoring
    with _burst_lock:
        _session_bursts.append(burst)

    # Tier 1: Full sonic_model pipeline (audio cues)
    coaching, cues = _sonic_coaching(burst)

    # Tier 2: Rule fallback
    if not coaching:
        coaching = _rule_coaching(burst)
        source = "bridge_rules"
    else:
        source = "sonic_model"

    # Tier 3: Insightful Coach Engine (Pace Notes)
    pace_note = None
    coach_source = None
    
    if _coach and _track:
        import time
        import types
        
        frame = types.SimpleNamespace(
            speed=burst.get("avg_speed_kmh", 0) / 3.6,
            brake_pressure=burst.get("max_brake_bar", 0),
            throttle=burst.get("avg_throttle_pct", 0),
            g_lat=burst.get("max_lateral_g", burst.get("max_combo_g", 0) * 0.7),
            g_long=burst.get("max_long_g", burst.get("max_combo_g", 0) * 0.7)
        )
        
        distance_m = burst.get("distance_m", 0)
        nearest = find_nearest_corner(_track, distance_m)
        dist_to_corner = distance_to_corner(_track, distance_m, nearest) if nearest else 999.0
        in_corner_obj = nearest if burst.get("in_corner", False) else None
        
        ctx = build_context(
            driver_level=burst.get("driver_level", "intermediate"),
            track=_track,
            frame=frame,
            next_corner=nearest,
            meters_to_entry=dist_to_corner,
            in_corner_obj=in_corner_obj,
            past_apex=burst.get("past_apex", False),
        )
        
        msg = _coach.propose(ctx)
        msg = _arbiter.submit(msg, now=time.time(), on_straight=abs(frame.g_lat) < 0.3)
        if msg:
            pace_note = msg.text
            coach_source = _coach.name
            # Override generic sonic_model coaching with the more insightful pace note
            coaching = pace_note
            source = coach_source

    return jsonify({
        "coaching":     coaching,
        "cues":         cues,
        "burst_id":     burst_id,
        "source":       source,
        "pace_note":    pace_note,
        "coach_source": coach_source,
    })


# ── Insights engine ───────────────────────────────────────────────────────────

def _score_insights(bursts: list) -> list:
    """
    Score 4 insight dimensions across accumulated session bursts.
    Returns up to 3 insights sorted by effort ASC, est_gain DESC.
    """
    if not bursts:
        return []

    # --- Dimension accumulators ---
    total_frames   = sum(b.get("frame_count", 1) for b in bursts)
    coast_frames   = sum(b.get("coast_frames", 0) for b in bursts)
    trail_frames   = sum(b.get("trail_brake_frames", 0) for b in bursts)
    corner_bursts  = [b for b in bursts if b.get("corners_visited")]
    all_g          = [b.get("max_combo_g", 0) for b in bursts]
    avg_g          = sum(all_g) / len(all_g) if all_g else 0
    avg_speed      = sum(b.get("avg_speed_kmh", 0) for b in bursts) / len(bursts)

    coast_pct      = (coast_frames / max(total_frames, 1)) * 100
    grip_headroom  = 2.29 - avg_g   # Gs below the tyre limit

    # Collect corner names with issues
    coast_corners  = []
    grip_corners   = []
    for b in corner_bursts:
        if (b.get("coast_frames", 0) / max(b.get("frame_count", 1), 1)) > 0.20:
            coast_corners.extend(b.get("corners_visited", []))
        if b.get("max_combo_g", 0) < 1.5:
            grip_corners.extend(b.get("corners_visited", []))
    coast_corners = list(dict.fromkeys(coast_corners))[:4]  # dedupe, max 4
    grip_corners  = list(dict.fromkeys(grip_corners))[:4]

    insights = []

    # 1. Coast excess — easiest gain, just lift the foot earlier
    if coast_pct > 15:
        est = round(min(coast_pct * 0.03, 1.5), 1)
        insights.append({
            "id":             "coast_excess",
            "title":          "Early Throttle Pickup",
            "detail":         f"You're coasting {coast_pct:.0f}% of corners. "
                              f"Get to full throttle at the apex instead of mid-exit. "
                              f"Every tenth of a second off throttle is lost time.",
            "corners":        coast_corners,
            "metric_label":   "Coast",
            "metric_value":   f"{coast_pct:.0f}%",
            "effort":         1,
            "est_gain_s":     est,
            "evidence_bursts": len([b for b in bursts if b.get("coast_frames", 0) > 0]),
        })

    # 2. Grip headroom — easy, just carry more speed
    if avg_g < 1.6 and len(grip_corners) >= 2:
        est = round(min(grip_headroom * 0.4, 1.0), 1)
        insights.append({
            "id":             "grip_headroom",
            "title":          "Unused Grip Budget",
            "detail":         f"Peak G averaging {avg_g:.2f}G — tyres support 2.29G. "
                              f"You have {grip_headroom:.2f}G of headroom. "
                              f"Carry more entry speed through the corners listed.",
            "corners":        grip_corners,
            "metric_label":   "Peak G",
            "metric_value":   f"{avg_g:.2f}G",
            "effort":         1,
            "est_gain_s":     est,
            "evidence_bursts": len(grip_corners),
        })

    # 3. Trail braking absent — moderate effort, technique change
    in_corner_bursts = [b for b in corner_bursts if b.get("in_corner")]
    if in_corner_bursts and trail_frames == 0:
        trail_corners = list(dict.fromkeys(
            c for b in in_corner_bursts for c in b.get("corners_visited", [])
        ))[:4]
        insights.append({
            "id":             "trail_absent",
            "title":          "Add Trail Braking",
            "detail":         "No trail braking detected. Holding light brake pressure "
                              "through corner entry adds rotation, lets you brake later, "
                              "and improves mid-corner balance.",
            "corners":        trail_corners,
            "metric_label":   "Trail frames",
            "metric_value":   "0",
            "effort":         2,
            "est_gain_s":     0.4,
            "evidence_bursts": len(in_corner_bursts),
        })

    # 4. Braking late / low entry speed
    slow_entry_corners = []
    for b in corner_bursts:
        if b.get("avg_speed_kmh", 999) < 70 and b.get("in_corner"):
            slow_entry_corners.extend(b.get("corners_visited", []))
    slow_entry_corners = list(dict.fromkeys(slow_entry_corners))[:4]
    if slow_entry_corners:
        insights.append({
            "id":             "braking_late",
            "title":          "Brake Point Optimisation",
            "detail":         f"Corner entry averaging {avg_speed:.0f} km/h at the corners "
                              f"listed. Try moving your brake marker 15–20m later — you may "
                              f"be over-braking and scrubbing speed unnecessarily.",
            "corners":        slow_entry_corners,
            "metric_label":   "Avg entry",
            "metric_value":   f"{avg_speed:.0f} km/h",
            "effort":         2,
            "est_gain_s":     0.5,
            "evidence_bursts": len(slow_entry_corners),
        })

    # Sort: effort ASC, then est_gain DESC, pick top 3
    insights.sort(key=lambda x: (x["effort"], -x["est_gain_s"]))
    for i, ins in enumerate(insights[:3], 1):
        ins["rank"] = i
    return insights[:3]


def _gemini_insights(bursts: list, lap: int = None) -> list:
    if not bursts: return []
    if not HAS_GENAI or not os.environ.get("GEMINI_API_KEY"):
        return _score_insights(bursts)
    
    base_dir = os.path.dirname(__file__)
    repo_dir = os.path.abspath(os.path.join(base_dir, ".."))
    
    try:
        with open(os.path.join(repo_dir, "docs/transcript.txt"), "r") as f:
            transcript = f.read()
    except Exception:
        transcript = ""
        
    try:
        with open(os.path.join(repo_dir, "docs/Performance-Driving-Illustrated-2-23-24.txt"), "r") as f:
            pedagogy = f.read()[:5000]
    except Exception:
        pedagogy = ""

    gold_standard = """
    Sonoma Raceway Gold Standard (Turn, Speed, Gear):
    Turn 1: Entry 111 km/h, Apex 113 km/h, Exit 117 km/h, Gear 2
    Turn 3: Entry 104 km/h, Apex 87 km/h, Exit 102 km/h, Gear 4
    Turn 6: Entry 92 km/h, Apex 77 km/h, Exit 105 km/h, Gear 5
    Turn 9: Entry 121 km/h, Apex 116 km/h, Exit 132 km/h, Gear 3
    Turn 10: Entry 106 km/h, Apex 73 km/h, Exit 108 km/h, Gear 6
    Turn 11: Entry 88 km/h, Apex 64 km/h, Exit 95 km/h, Gear 5
    """
    driver_level = bursts[0].get("driver_level", "intermediate") if bursts else "intermediate"

    prompt = f"""
    You are an expert racing coach. Provide exactly 3 actionable racing insights for a {driver_level} driver who just completed{' lap ' + str(lap) if lap is not None else ' a lap'} at Sonoma Raceway.
    Compare their telemetry with the Gold Standard lap. Use the coaching pedagogy provided. Adjust the tone, terminology, and complexity of your feedback to suit a {driver_level} driver.

    ## Pedagogy
    {pedagogy}
    
    ## Transcript
    {transcript}

    ## Gold Standard
    {gold_standard}

    ## Driver Lap Telemetry
    """
    
    for i, b in enumerate(bursts):
        prompt += f"Burst {i+1}:\n"
        prompt += f"- Corners Visited: {b.get('corners_visited', [])}\n"
        prompt += f"- Avg Speed: {b.get('avg_speed_kmh', 0):.0f} km/h\n"
        prompt += f"- Max Combo G: {b.get('max_combo_g', 0):.2f} G\n"
        prompt += f"- Coasting Frames: {b.get('coast_frames', 0)} / {b.get('frame_count', 1)}\n"
        prompt += f"- Trail Braking Frames: {b.get('trail_brake_frames', 0)}\n\n"

    prompt += """
    Output ONLY a valid JSON array of 3 objects with exactly this structure:
    [
      {
        "id": "insight_id_like_brake_late",
        "title": "Short Title (max 4 words)",
        "detail": "Actionable coaching advice. Mention specific corners.",
        "corners": ["Turn 3"],
        "metric_label": "Short label (e.g. Avg Speed)",
        "metric_value": "Value (e.g. 104 km/h)",
        "effort": 1,
        "est_gain_s": 0.5
      }
    ]
    effort should be 1, 2, or 3.
    est_gain_s should be a float.
    Make the advice sound professional, encouraging, and highly specific to the corners.
    """

    client = genai.Client()
    try:
        response = client.models.generate_content(
            model='gemini-2.5-flash',
            contents=prompt,
        )
        
        text = response.text
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
            
        data = json.loads(text.strip())
        
        for i, item in enumerate(data):
            item["rank"] = i + 1
            
        insights = data[:3]
        print(f"\\n🏁 Gemini Insights Generated Successfully:\\n{json.dumps(insights, indent=2)}\\n")
        return insights
    except Exception as e:
        print(f"Gemini generation failed: {e}")
        return _score_insights(bursts)

@app.route("/insights", methods=["GET"])
def get_insights():
    """Return top-3 prioritised driver insights from the current session bursts."""
    lap_param = request.args.get("lap")
    lap = int(lap_param) if lap_param else None

    with _burst_lock:
        bursts_snapshot = list(_session_bursts)

    if lap is not None:
        bursts_snapshot = [b for b in bursts_snapshot if b.get("lap") == lap]

    insights = _gemini_insights(bursts_snapshot, lap=lap)
    return jsonify({
        "insights":       insights,
        "session_bursts": len(bursts_snapshot),
        "generated_at":   datetime.utcnow().isoformat(),
    })


@app.route("/session/reset", methods=["POST"])
def session_reset():
    """Clear the burst accumulator — call this when a new session starts."""
    with _burst_lock:
        count = len(_session_bursts)
        _session_bursts.clear()
    return jsonify({"cleared_bursts": count, "status": "ok"})


@app.route("/laps", methods=["GET"])
def get_laps():
    session_id = request.args.get("session_id")
    limit = int(request.args.get("limit", 20))

    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"laps": [], "error": "duckdb not available"})
        query = "SELECT * FROM laps"
        params = []
        if session_id:
            query += " WHERE session_id = ?"
            params.append(session_id)
        query += f" ORDER BY recorded_at DESC LIMIT {limit}"
        rows = conn.execute(query, params).fetchall()
        cols = [d[0] for d in conn.description]
        conn.close()

    return jsonify({"laps": [dict(zip(cols, r)) for r in rows], "count": len(rows)})


@app.route("/lap", methods=["POST"])
def save_lap():
    data = request.get_json(force=True, silent=True) or {}
    with _db_lock:
        conn = get_db()
        if conn is None:
            return jsonify({"saved": False, "error": "duckdb not available"})
        conn.execute("""
            INSERT INTO laps
                (session_id, lap_number, lap_time_s, best_sector,
                 avg_speed_kmh, max_combo_g, coast_pct)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, [
            data.get("session_id", ""),  data.get("lap_number", 0),
            data.get("lap_time_s", 0),   data.get("best_sector", 0),
            data.get("avg_speed_kmh", 0),data.get("max_combo_g", 0),
            data.get("coast_pct", 0),
        ])
        conn.close()
    return jsonify({"saved": True})


# ── Entrypoint ─────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Pitwall Bridge Server")
    parser.add_argument("--track", default=os.path.join(SIM_DIR, "sonoma.json"),
                        help="Path to track JSON (default: src/simulator/sonoma.json)")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    if HAS_SONIC and os.path.exists(args.track):
        try:
            _track = load_track(args.track)
            print(f"✓  Track: {_track.name} ({len(_track.corners)} corners)")
        except Exception as e:
            print(f"⚠  Track load failed: {e}")

    port = args.port
    print(f"\n🏁  Pitwall Bridge v2 on http://127.0.0.1:{port}")
    print(f"    Engine: {'sonic_model (real cues)' if HAS_SONIC else 'rule fallback'}")
    print(f"    DuckDB: {'enabled → ' + DB_PATH if HAS_DUCKDB else 'disabled'}")
    print(f"\n    Emulator tunnel:")
    print(f"    ~/Library/Android/sdk/platform-tools/adb reverse tcp:{port} tcp:{port}\n")
    app.run(host="127.0.0.1", port=port, debug=False, threaded=True)
