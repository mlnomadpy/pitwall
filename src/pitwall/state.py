"""pitwall.state — shared mutable state for the Pitwall bridge process.

Every Blueprint imports `state` from this module instead of reaching into
module-level globals. This eliminates the coupling that forced 4,500 lines
into a single file.

The singleton is initialised lazily by the entry-point after CLI parsing —
Blueprints just read from it.
"""

import os
import sys
import threading
from typing import Optional


# ── Path setup ─────────────────────────────────────────────────────────────────
# src/pitwall/ is a sibling of src/simulator/
SIM_DIR = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "simulator")
)
if SIM_DIR not in sys.path:
    sys.path.insert(0, SIM_DIR)

# Project root for resolving data/ paths
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

# DuckDB lives in data/
DB_PATH = os.path.join(PROJECT_ROOT, "data", "pitwall_sessions.duckdb")


class BridgeState:
    """Shared mutable state for the bridge process.

    Attributes are set by the entry-point at startup; Blueprints read them
    at request time. Thread-safe access to DuckDB goes through `db_lock`.
    """

    def __init__(self):
        # ── Track ──────────────────────────────────────────────────────────
        self.track = None              # loaded TrackMap (from track_loader)

        # ── DuckDB ─────────────────────────────────────────────────────────
        self.db_lock = threading.Lock()
        self.db_path: str = os.path.abspath(DB_PATH)

        # ── Coach engine ───────────────────────────────────────────────────
        self.coach = None              # CoachEngine instance (LitertCoach / RuleCoach)
        self.arbiter = None            # CoachArbiter instance
        self.set_friction_logger = None  # coach_engine.set_friction_logger

        # ── Session burst accumulator (for /insights) ──────────────────────
        self.session_bursts: list = []
        self.burst_lock = threading.Lock()

        # ── Session analysis bundle cache ──────────────────────────────────
        self.session_bundles: dict[str, dict] = {}
        self.bundles_lock = threading.Lock()

        # ── CAN reader ─────────────────────────────────────────────────────
        self.can_reader = None

        # ── ADK Q&A ───────────────────────────────────────────────────────
        self.qa_histories: dict = {}
        self.qa_lock = threading.Lock()
        self.adk_orchestrator = None
        self.adk_agent_registry: list = []
        self.run_adk = None
        self.stream_adk = None
        self.get_adk_traces = None
        self.reset_adk_session = None

        # ── Feature flags ──────────────────────────────────────────────────
        self.has_sonic: bool = False
        self.has_coach: bool = False
        self.has_analyzer: bool = False
        self.has_duckdb: bool = False
        self.has_adk: bool = False
        self.has_genai: bool = False    # always False — kept for short-circuit

        # ── Module references (set during init) ────────────────────────────
        self.compute_cues = None       # sonic_model.compute_cues
        self.AudioCue = None           # sonic_model.AudioCue
        self.Pattern = None            # sonic_model.Pattern
        self.load_track = None         # track_loader.load_track
        self.find_nearest_corner = None  # track_loader.find_nearest_corner
        self.distance_to_corner = None   # track_loader.distance_to_corner
        self.build_context = None      # coach_engine.build_context
        self.analyze_session = None    # session_analyzer.analyze_session
        self.ensure_driver_schema = None  # driver_profile.ensure_driver_schema
        self.append_session_events = None  # driver_profile.append_session_events
        self.compute_profile = None    # driver_profile.compute_profile
        self.sonoma = None             # sonoma module

        # ── Constants ──────────────────────────────────────────────────────
        self.sim_dir: str = SIM_DIR

    def init_imports(self):
        """Attempt to import optional dependencies and set feature flags.

        Called once at startup. Failures are logged but non-fatal — each
        feature degrades gracefully when its flag is False.
        """
        # ── sonic_model ────────────────────────────────────────────────────
        try:
            from pitwall.features.coaching.sonic_model import compute_cues, AudioCue, Pattern
            from pitwall.features.track.track_loader import load_track, find_nearest_corner, distance_to_corner
            self.compute_cues = compute_cues
            self.AudioCue = AudioCue
            self.Pattern = Pattern
            self.load_track = load_track
            self.find_nearest_corner = find_nearest_corner
            self.distance_to_corner = distance_to_corner
            self.has_sonic = True
            print("✓  sonic_model loaded")
        except ImportError as e:
            print(f"⚠  sonic_model not available ({e}) — falling back to rule engine")

        # ── coach_engine ───────────────────────────────────────────────────
        try:
            from pitwall.features.coaching.coach_engine import (
                make_coach, CoachArbiter, build_context, set_friction_logger,
            )
            self.coach = make_coach(kind="auto")
            self.arbiter = CoachArbiter()
            self.build_context = build_context
            self.set_friction_logger = set_friction_logger
            self.has_coach = True
            print(f"✓  coach_engine loaded ({self.coach.name})")
        except ImportError as e:
            print(f"⚠  coach_engine not available ({e})")

        # ── session_analyzer + driver_profile ──────────────────────────────
        try:
            import pitwall.features.track.sonoma as _sonoma                        # noqa: F401
            from pitwall.features.session.session_analyzer import analyze_session
            from pitwall.features.session.driver_profile import (
                ensure_schema as ensure_driver_schema,
                append_session_events, compute_profile,
            )
            self.sonoma = _sonoma
            self.analyze_session = analyze_session
            self.ensure_driver_schema = ensure_driver_schema
            self.append_session_events = append_session_events
            self.compute_profile = compute_profile
            self.has_analyzer = True
            print("✓  session_analyzer + driver_profile loaded")
        except ImportError as e:
            self.has_analyzer = False
            print(f"⚠  session_analyzer not available ({e}) — debrief disabled")

        # ── DuckDB ─────────────────────────────────────────────────────────
        try:
            import duckdb  # noqa: F401
            self.has_duckdb = True
        except ImportError:
            print("⚠  duckdb not installed — lap history disabled. pip3 install duckdb")

        # ── ADK multi-agent backend ────────────────────────────────────────
        try:
            from pitwall.adk_agents import (
                coach_orchestrator,
                HAS_ADK as _adk_loaded,
                AGENT_REGISTRY,
                run_adk, stream_adk,
                get_pending_traces,
                reset_driver_session,
            )
            self.adk_orchestrator = coach_orchestrator
            self.adk_agent_registry = AGENT_REGISTRY
            self.run_adk = run_adk
            self.stream_adk = stream_adk
            self.get_adk_traces = get_pending_traces
            self.reset_adk_session = reset_driver_session
            self.has_adk = _adk_loaded and coach_orchestrator is not None
            if self.has_adk:
                print(f"✓  ADK coach_orchestrator loaded — {len(AGENT_REGISTRY)} agents (LiteRT-LM E4B)")
            else:
                print("⚠  adk_agents imported but google-adk not installed — ADK disabled")
        except ImportError as e:
            print(f"⚠  adk_agents not importable ({e}) — ADK disabled")

        # If we don't have sonoma loaded yet (e.g. session_analyzer failed
        # but we still want track constants), try a standalone import.
        if self.sonoma is None:
            try:
                import pitwall.features.track.sonoma as _sonoma
                self.sonoma = _sonoma
            except ImportError:
                pass


# Module-level singleton — all Blueprints import this.
state = BridgeState()
