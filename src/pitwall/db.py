"""bridge.db — DuckDB schema DDL, connection factory, and signal helpers.

Owns all table creation, the signal registry seeder, capability computation,
LLM friction sink, and the interpolation helpers used by the signal
synchroniser (ADR-015 Phase 3).
"""

import json
import os
import threading

from pitwall.state import state, SIM_DIR


# ── Constants ──────────────────────────────────────────────────────────────────

REGISTRY_SEED_PATH = os.path.abspath(os.path.join(
    os.path.dirname(__file__), "..", "..", "data", "registry", "obd2_pids.json",
))

# Wide-table columns that double as registry signals — used by capability
# computation to advertise canonical fields without round-tripping through
# the tall store.
WIDE_SIGNAL_NAMES = (
    "distance_m", "speed_ms", "g_lat", "g_long", "combo_g",
    "brake_bar", "throttle_pct", "steering_deg", "rpm", "lat", "lon",
)


# ── Connection factory ─────────────────────────────────────────────────────────

def get_db():
    """Open a DuckDB connection and ensure all schema tables exist."""
    if not state.has_duckdb:
        return None
    import duckdb
    conn = duckdb.connect(state.db_path)
    conn.execute("""
        CREATE SEQUENCE IF NOT EXISTS laps_id_seq;
        CREATE TABLE IF NOT EXISTS laps (
            id            INTEGER PRIMARY KEY DEFAULT nextval('laps_id_seq'),
            session_id    VARCHAR,
            lap_number    INTEGER,
            lap_time_s    DOUBLE,
            best_sector   DOUBLE,
            avg_speed_kmh DOUBLE,
            max_combo_g   DOUBLE,
            coast_pct     DOUBLE,
            recorded_at   TIMESTAMP DEFAULT now()
        );
        CREATE SEQUENCE IF NOT EXISTS notes_id_seq;
        CREATE TABLE IF NOT EXISTS coaching_notes (
            id            INTEGER PRIMARY KEY DEFAULT nextval('notes_id_seq'),
            session_id    VARCHAR,
            burst_id      INTEGER,
            distance_m    DOUBLE,
            text          VARCHAR,
            source        VARCHAR,
            recorded_at   TIMESTAMP DEFAULT now()
        );
        CREATE TABLE IF NOT EXISTS telemetry (
            session_id   VARCHAR,
            frame_idx    INTEGER,
            timestamp    DOUBLE,
            distance_m   DOUBLE,
            speed_ms     DOUBLE,
            g_lat        DOUBLE,
            g_long       DOUBLE,
            combo_g      DOUBLE,
            brake_bar    DOUBLE,
            throttle_pct DOUBLE,
            steering_deg DOUBLE,
            rpm          DOUBLE,
            lat          DOUBLE,
            lon          DOUBLE
        );
        CREATE INDEX IF NOT EXISTS idx_telemetry_session
            ON telemetry(session_id, frame_idx);
        CREATE TABLE IF NOT EXISTS video_frames (
            session_id    VARCHAR,
            timestamp     DOUBLE,    -- epoch seconds, canonical clock
            avitime_ms    BIGINT,    -- VBO avitime when synced from Racelogic
            file_path     VARCHAR,   -- on-disk MP4 (chunked, e.g. ~10s segments)
            file_offset_s DOUBLE,    -- seconds into file_path
            width         INTEGER,
            height        INTEGER
        );
        CREATE INDEX IF NOT EXISTS idx_video_frames_session_t
            ON video_frames(session_id, timestamp);

        -- ADR-015: Universal Telemetry Sink (Phase 1 — schema + registry) ──
        CREATE SEQUENCE IF NOT EXISTS signal_registry_id_seq;
        CREATE TABLE IF NOT EXISTS signal_registry (
            signal_id     INTEGER PRIMARY KEY DEFAULT nextval('signal_registry_id_seq'),
            name          VARCHAR UNIQUE NOT NULL,
            units         VARCHAR,
            semantics     VARCHAR,
            "group"       VARCHAR,
            expected_hz   DOUBLE,
            min_useful_hz DOUBLE,
            discovery     VARCHAR,
            obd2_pid      VARCHAR,
            discovered_at TIMESTAMP DEFAULT now()
        );
        CREATE TABLE IF NOT EXISTS telemetry_signals (
            session_id  VARCHAR NOT NULL,
            signal_id   INTEGER NOT NULL,
            t           DOUBLE  NOT NULL,
            value       DOUBLE  NOT NULL,
            PRIMARY KEY (session_id, signal_id, t)
        );
        CREATE INDEX IF NOT EXISTS idx_signals_sess_sig_t
            ON telemetry_signals (session_id, signal_id, t);
        CREATE TABLE IF NOT EXISTS session_capabilities (
            session_id  VARCHAR NOT NULL,
            signal_id   INTEGER NOT NULL,
            n_samples   INTEGER NOT NULL,
            mean_hz     DOUBLE  NOT NULL,
            t_start     DOUBLE  NOT NULL,
            t_end       DOUBLE  NOT NULL,
            PRIMARY KEY (session_id, signal_id)
        );

        -- Phase-6: lightweight sessions catalog. Auto-upserted on ingest.
        -- Powers /driver/<id>/evolution; otherwise optional metadata.
        CREATE TABLE IF NOT EXISTS sessions (
            session_id    VARCHAR PRIMARY KEY,
            driver        VARCHAR,
            driver_level  VARCHAR,
            track         VARCHAR,
            car           VARCHAR,
            started_at    TIMESTAMP DEFAULT now(),
            ended_at      TIMESTAMP,
            note          VARCHAR
        );

        -- ADR-018: LLM friction sink. Every LitertCoach._generate call lands
        -- a row here so we can spot edge degradation (latency spikes,
        -- truncations, fallbacks) at 130 mph instead of after the fact.
        CREATE SEQUENCE IF NOT EXISTS llm_friction_id_seq;
        CREATE TABLE IF NOT EXISTS llm_friction (
            id               INTEGER PRIMARY KEY DEFAULT nextval('llm_friction_id_seq'),
            session_id       VARCHAR,
            role             VARCHAR,           -- 'brief' | 'cue' | 'debrief'
            mode             VARCHAR,           -- DURING_DRIVE | PRE_BRIEF | POST_SESSION
            backend          VARCHAR,
            prompt_chars     INTEGER,
            completion_chars INTEGER,
            latency_ms       DOUBLE,
            truncated        BOOLEAN,
            fell_back        BOOLEAN,           -- true if templated fallback was used
            error            VARCHAR,
            emotion          VARCHAR,
            ts               TIMESTAMP DEFAULT now()
        );
        CREATE INDEX IF NOT EXISTS idx_llm_friction_session_ts
            ON llm_friction (session_id, ts);

        CREATE SEQUENCE IF NOT EXISTS conversations_id_seq;
        CREATE TABLE IF NOT EXISTS conversations (
            id           INTEGER PRIMARY KEY DEFAULT nextval('conversations_id_seq'),
            session_id   VARCHAR,
            driver_id    VARCHAR,
            role         VARCHAR,
            text         TEXT,
            focus_items  VARCHAR,
            emotion      VARCHAR,
            recorded_at  TIMESTAMP DEFAULT now()
        );
        CREATE INDEX IF NOT EXISTS idx_conversations_session
            ON conversations(session_id, recorded_at);
        CREATE INDEX IF NOT EXISTS idx_conversations_driver
            ON conversations(driver_id, recorded_at);

        -- ADK agent telemetry (ADR-021): one row per agent run or tool call.
        -- trace_id groups all events from a single run_adk() invocation.
        CREATE SEQUENCE IF NOT EXISTS agent_traces_id_seq;
        CREATE TABLE IF NOT EXISTS agent_traces (
            id          INTEGER PRIMARY KEY DEFAULT nextval('agent_traces_id_seq'),
            trace_id    VARCHAR,    -- ADK session UUID — groups one run_adk() call
            pitwall_sid VARCHAR,    -- pitwall session_id (may be empty for Q&A)
            agent_name  VARCHAR,
            event_type  VARCHAR,    -- 'agent' | 'tool'
            detail      VARCHAR,    -- tool name, or intent for orchestrator
            latency_ms  DOUBLE,
            success     BOOLEAN DEFAULT true,
            ts          TIMESTAMP DEFAULT now()
        );
        CREATE INDEX IF NOT EXISTS idx_agent_traces_trace
            ON agent_traces(trace_id, ts);
        CREATE INDEX IF NOT EXISTS idx_agent_traces_agent
            ON agent_traces(agent_name, ts);
    """)
    return conn


# ── Signal registry seeding ────────────────────────────────────────────────────

def seed_signal_registry() -> int:
    """Idempotently seed signal_registry from data/registry/obd2_pids.json.

    Returns the number of rows inserted (0 on subsequent calls — INSERT OR
    IGNORE preserves any unit-stamping a human did on previously-discovered
    signals).
    """
    if not state.has_duckdb or not os.path.exists(REGISTRY_SEED_PATH):
        return 0
    with open(REGISTRY_SEED_PATH) as fh:
        seed = json.load(fh)
    rows = seed.get("signals", [])
    inserted = 0
    with state.db_lock:
        conn = get_db()
        if conn is None:
            return 0
        for s in rows:
            try:
                conn.execute(
                    """INSERT INTO signal_registry
                       (name, units, semantics, "group", expected_hz,
                        min_useful_hz, discovery, obd2_pid)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                       ON CONFLICT (name) DO NOTHING""",
                    [s["name"], s.get("units"), s.get("semantics"),
                     s.get("group"), s.get("expected_hz"),
                     s.get("min_useful_hz"), s.get("discovery"),
                     s.get("obd2_pid")],
                )
                inserted += 1
            except Exception:
                pass
        conn.close()
    return inserted


# ── Signal resolution ──────────────────────────────────────────────────────────

def resolve_signal_id(conn, name: str) -> int:
    """Look up signal_id by name; auto-register a novel signal as 'discovered'.

    Discovered signals get units=NULL — the coach treats them as logged but
    not coachable until a human stamps the units in the registry.
    """
    row = conn.execute(
        "SELECT signal_id FROM signal_registry WHERE name = ?", [name],
    ).fetchone()
    if row is not None:
        return row[0]
    conn.execute(
        """INSERT INTO signal_registry (name, units, discovery)
           VALUES (?, NULL, 'discovered')""",
        [name],
    )
    return conn.execute(
        "SELECT signal_id FROM signal_registry WHERE name = ?", [name],
    ).fetchone()[0]


# ── Capability computation ─────────────────────────────────────────────────────

def compute_capabilities(sid: str) -> int:
    """Aggregate (signal_id, n_samples, mean_hz, t_start, t_end) per session.

    Reads from BOTH the wide telemetry table (for canonical fields) and
    telemetry_signals (for everything else) and rewrites session_capabilities
    for the session. Returns the number of capability rows written.
    """
    if not state.has_duckdb:
        return 0
    with state.db_lock:
        conn = get_db()
        if conn is None:
            return 0

        conn.execute("DELETE FROM session_capabilities WHERE session_id = ?", [sid])

        rows_written = 0

        # 1. Wide-table canonical fields — every present session has all 11.
        n, t_start, t_end = conn.execute(
            "SELECT COUNT(*), MIN(timestamp), MAX(timestamp) "
            "FROM telemetry WHERE session_id = ?",
            [sid],
        ).fetchone()
        if n and n > 0 and t_start is not None and t_end is not None:
            duration = max(t_end - t_start, 1e-6)
            mean_hz = n / duration
            placeholders = ",".join(["?"] * len(WIDE_SIGNAL_NAMES))
            sigs = conn.execute(
                f"SELECT signal_id FROM signal_registry WHERE name IN ({placeholders})",
                list(WIDE_SIGNAL_NAMES),
            ).fetchall()
            for (sig_id,) in sigs:
                conn.execute(
                    "INSERT INTO session_capabilities VALUES (?, ?, ?, ?, ?, ?)",
                    [sid, sig_id, n, mean_hz, t_start, t_end],
                )
                rows_written += 1

        # 2. Tall store — variable rate per signal; tall wins on overlap.
        tall = conn.execute(
            """SELECT signal_id, COUNT(*), MIN(t), MAX(t)
               FROM telemetry_signals
               WHERE session_id = ?
               GROUP BY signal_id""",
            [sid],
        ).fetchall()
        for sig_id, ns, ts, te in tall:
            duration = max((te - ts), 1e-6)
            hz = ns / duration
            conn.execute(
                """INSERT INTO session_capabilities VALUES (?, ?, ?, ?, ?, ?)
                   ON CONFLICT (session_id, signal_id) DO UPDATE SET
                       n_samples = excluded.n_samples,
                       mean_hz   = excluded.mean_hz,
                       t_start   = excluded.t_start,
                       t_end     = excluded.t_end""",
                [sid, sig_id, ns, hz, ts, te],
            )
            rows_written += 1

        conn.close()
    return rows_written


# ── LLM friction sink (ADR-018) ───────────────────────────────────────────────

def log_llm_friction(rec: dict) -> None:
    """Persist one LitertCoach friction record. Called from coach_engine via
    `set_friction_logger`, so it must be silent on failure — a misbehaving
    sink mustn't stall the inference call."""
    if not state.has_duckdb:
        return
    try:
        with state.db_lock:
            conn = get_db()
            if conn is None:
                return
            try:
                conn.execute(
                    """INSERT INTO llm_friction
                       (session_id, role, mode, backend, prompt_chars,
                        completion_chars, latency_ms, truncated, fell_back,
                        error, emotion)
                       VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                    [
                        rec.get("session_id"),
                        rec.get("role", ""),
                        rec.get("mode", ""),
                        rec.get("backend", ""),
                        int(rec.get("prompt_chars") or 0),
                        int(rec.get("completion_chars") or 0),
                        float(rec.get("latency_ms") or 0.0),
                        bool(rec.get("truncated", False)),
                        bool(rec.get("fell_back", False)),
                        (rec.get("error") or "")[:512],
                        rec.get("emotion") or "",
                    ],
                )
            finally:
                conn.close()
    except Exception:
        pass


# ── Session helpers ────────────────────────────────────────────────────────────

def session_has_telemetry(sid: str) -> bool:
    """Check if a session has any telemetry frames."""
    if not state.has_duckdb:
        return False
    with state.db_lock:
        conn = get_db()
        if conn is None:
            return False
        n = conn.execute(
            "SELECT COUNT(*) FROM telemetry WHERE session_id = ?", [sid],
        ).fetchone()[0]
        conn.close()
    return bool(n)


def reset_live_session():
    """Drop all rows for the synthetic `_live` session.

    Called on bridge boot when the CAN reader is launched without an
    explicit `--can-session-id`. Keeps stale values from a previous run
    out of the Pit Stall Setup live-state view.
    """
    if not state.has_duckdb:
        return
    with state.db_lock:
        conn = get_db()
        if conn is None:
            return
        try:
            conn.execute("DELETE FROM telemetry            WHERE session_id = ?", ["_live"])
            conn.execute("DELETE FROM telemetry_signals    WHERE session_id = ?", ["_live"])
            conn.execute("DELETE FROM session_capabilities WHERE session_id = ?", ["_live"])
            conn.execute("DELETE FROM coaching_notes       WHERE session_id = ?", ["_live"])
        finally:
            conn.close()


def ensure_session_row(sid: str, *, driver=None, driver_level=None,
                       track=None, car=None, note=None):
    """Idempotently upsert a sessions row. Called on every ingest path."""
    if not state.has_duckdb:
        return
    with state.db_lock:
        conn = get_db()
        if conn is None:
            return
        existing = conn.execute(
            "SELECT driver, driver_level, track, car, note "
            "FROM sessions WHERE session_id = ?", [sid],
        ).fetchone()
        if existing is None:
            conn.execute(
                "INSERT INTO sessions (session_id, driver, driver_level, track, car, note) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                [sid, driver, driver_level, track, car, note],
            )
        else:
            cur = dict(zip(
                ["driver", "driver_level", "track", "car", "note"], existing,
            ))
            merged = {
                "driver":       driver       if driver       is not None else cur["driver"],
                "driver_level": driver_level if driver_level is not None else cur["driver_level"],
                "track":        track        if track        is not None else cur["track"],
                "car":          car          if car          is not None else cur["car"],
                "note":         note         if note         is not None else cur["note"],
            }
            conn.execute(
                """UPDATE sessions SET driver = ?, driver_level = ?,
                                       track = ?, car = ?, note = ?
                   WHERE session_id = ?""",
                [merged["driver"], merged["driver_level"], merged["track"],
                 merged["car"], merged["note"], sid],
            )
        conn.close()


# ── Signal reading + interpolation (ADR-015 Phase 3) ──────────────────────────

def read_signal(conn, sid: str, name: str, t_from=None, t_to=None) -> list:
    """Return sorted [(t, value), ...] for a signal in either store.

    Resolves wide-table canonicals (speed_ms, brake_bar, …) directly off
    the wide column; everything else routes through telemetry_signals.
    Returns [] if the signal is unknown or has no samples for this session.
    """
    if name in WIDE_SIGNAL_NAMES:
        sql = (f"SELECT timestamp, {name} FROM telemetry "
               "WHERE session_id = ?")
        params: list = [sid]
        if t_from is not None:
            sql += " AND timestamp >= ?"
            params.append(t_from)
        if t_to is not None:
            sql += " AND timestamp <= ?"
            params.append(t_to)
        sql += " ORDER BY timestamp"
        return [(float(t), float(v)) for t, v in conn.execute(sql, params).fetchall()
                if t is not None and v is not None]
    row = conn.execute(
        "SELECT signal_id FROM signal_registry WHERE name = ?", [name],
    ).fetchone()
    if row is None:
        return []
    sig_id = row[0]
    sql = ("SELECT t, value FROM telemetry_signals "
           "WHERE session_id = ? AND signal_id = ?")
    params = [sid, sig_id]
    if t_from is not None:
        sql += " AND t >= ?"
        params.append(t_from)
    if t_to is not None:
        sql += " AND t <= ?"
        params.append(t_to)
    sql += " ORDER BY t"
    return [(float(t), float(v)) for t, v in conn.execute(sql, params).fetchall()]


def interp_hold(axis_ts: list, samples: list) -> list:
    """ASOF: for each axis_t, return v of last (t,v) with t ≤ axis_t; else None."""
    if not samples:
        return [None] * len(axis_ts)
    out = []
    j = 0
    n = len(samples)
    for at in axis_ts:
        while j < n and samples[j][0] <= at:
            j += 1
        out.append(None if j == 0 else samples[j - 1][1])
    return out


def interp_lerp(axis_ts: list, samples: list) -> list:
    """Linear interp between bracketing samples; None outside the sample range."""
    if not samples:
        return [None] * len(axis_ts)
    out = []
    n = len(samples)
    j = 0
    for at in axis_ts:
        while j < n and samples[j][0] < at:
            j += 1
        if j == 0:
            out.append(samples[0][1] if samples[0][0] == at else None)
        elif j == n:
            out.append(samples[-1][1] if samples[-1][0] == at else None)
        else:
            t0, v0 = samples[j - 1]
            t1, v1 = samples[j]
            out.append(v0 if t1 == t0 else v0 + (v1 - v0) * (at - t0) / (t1 - t0))
    return out


def interp(axis_ts: list, samples: list, kind: str) -> list:
    """Dispatch to hold or lerp interpolation."""
    return interp_lerp(axis_ts, samples) if kind == "lerp" else interp_hold(axis_ts, samples)
