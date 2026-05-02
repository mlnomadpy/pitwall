"""
ADK multi-agent topology for pitwall paddock coaching.

Model: Gemini(base_url=...) → LiteRT-LM server (lit serve --port 8001)
       running Gemma 4 E4B on Pixel 10 Tensor G5 NPU via Termux.

Startup (Termux):
    lit pull gemma-4-e4b
    lit serve --port 8001

Environment overrides:
    PITWALL_LITERT_URL   default: localhost:8001
    PITWALL_LITERT_MODEL default: gemma-4-e4b

Public API for pitwall_bridge.py:
    run_adk(prompt, user_id="driver") -> str   — synchronous, thread-safe
    coach_orchestrator                          — PitwallOrchestrator instance
    AGENT_REGISTRY                              — list[dict] for /coach/agents

Agent roster (17 agents + PitwallOrchestrator):

  PitwallOrchestrator        Root. Deterministic keyword routing.
  ├── DebriefPipeline         Sequential: ParallelAgent([Highlights, Telemetry, Pedagogy]) → Narrative
  ├── BriefPipeline           Sequential: Pedagogy → Narrative (separate narrative instance)
  ├── TelemetryAgent          Session data (output_key: telemetry_data)
  ├── LapComparisonAgent      Lap-vs-lap delta
  ├── CornerCoachAgent        Single-corner deep-dive
  ├── ProgressTrackerAgent    Multi-session arc
  ├── SetupAdvisorAgent       Car balance from telemetry
  ├── HighlightFinderAgent    Best moments (output_key: highlights_data)
  ├── MindsetCoachAgent       Plateau + motivation
  ├── GoldLapAgent            Driver vs AJ reference lap
  ├── WeatherAdaptationAgent  Weather phase → adjustments
  ├── SessionPlannerAgent     N-lap practice plan
  ├── IncidentReviewAgent     Anomaly detection
  ├── RacePaceAgent           Lap degradation model
  ├── GoalSettingAgent        Realistic PB targets
  ├── MentalMapAgent          Corner consistency variance
  ├── VoiceScriptAgent        TTS audio cache scripts (ADR-017)
  ├── PedagogyAgent           Driver profile + Bentley concepts (output_key: pedagogy_data)
  └── NarrativeAgent          All human-facing output. Always the final stage.
"""
from __future__ import annotations

import asyncio
import logging
import os
import threading
import time
from collections import deque
from typing import AsyncGenerator

from adk_tools import (
    query_pitwall_db,
    get_lap_delta,
    get_corner_history,
    get_progress_report,
    get_setup_indicators,
    get_session_highlights,
    get_gold_lap_comparison,
    get_weather_adaptation_context,
    get_session_plan_context,
    get_incident_moments,
    get_race_pace_model,
    get_goal_targets,
    get_track_variance_map,
    get_agent_telemetry,
    get_audio_script_context,
    save_voice_scripts,
)

_log = logging.getLogger(__name__)

HAS_ADK = False
try:
    from google.adk.agents import Agent, BaseAgent, ParallelAgent, SequentialAgent
    from google.adk.models import Gemini
    from google.adk.plugins import BasePlugin
    from google.adk.runners import Runner
    from google.adk.sessions import InMemorySessionService
    from google.genai.types import Content, Part
    HAS_ADK = True
except ImportError:
    pass

# ── Agent trace buffer (drained by pitwall_bridge after each run_adk call) ────

_pending_traces: deque[dict] = deque(maxlen=2000)
_trace_lock = threading.Lock()


def get_pending_traces() -> list[dict]:
    """Drain and return all buffered traces. Called by pitwall_bridge to write to DuckDB."""
    with _trace_lock:
        traces = list(_pending_traces)
        _pending_traces.clear()
        return traces


# ── Persistent session registry (KV cache reuse across calls per driver) ──────
# lit serve has no prefix-cache flags — KV cache reuse happens at the ADK
# session level via LiteRT-LM's session-cloning mechanism. Keeping the same
# ADK session alive means the system instruction tokens are already in the
# KV cache for subsequent calls (~30-50% prefill reduction on warm requests).

_driver_sessions: dict[str, str] = {}       # user_id → ADK session_id
_driver_sessions_lock = threading.Lock()
_session_turn_count: dict[str, int] = {}
_SESSION_MAX_TURNS = 50  # auto-reset after N turns to prevent context overflow


def reset_driver_session(user_id: str) -> None:
    """Expire a driver's ADK session — call at start of each new driving session.

    Forces a fresh InMemorySessionService session on the next run_adk() call,
    clearing accumulated context while the cold-start KV cache rebuild is
    acceptable (new session = new day = driver expects a moment to start).
    """
    with _driver_sessions_lock:
        _driver_sessions.pop(user_id, None)
        _session_turn_count.pop(user_id, None)


# run_adk / reset_driver_session are always importable — raise at call time
def run_adk(prompt: str, user_id: str = "driver") -> str:
    raise RuntimeError("google-adk not installed — pip install google-adk")


if not HAS_ADK:
    coach_orchestrator = None  # type: ignore[assignment]
    AGENT_REGISTRY: list = []
else:
    _model = Gemini(
        base_url=os.getenv("PITWALL_LITERT_URL", "localhost:8001"),
        model=os.getenv("PITWALL_LITERT_MODEL", "gemma-4-e4b"),
    )

    # ── Intent classifier ──────────────────────────────────────────────────────

    def _classify_intent(query: str) -> str:
        """Keyword-based deterministic router. Avoids LLM routing 17 sub-agents."""
        q = query.lower()
        if any(k in q for k in ("debrief", "how did i do", "session summary", "review my session")):
            return "debrief"
        if any(k in q for k in ("brief", "pre-session", "pre session", "today's plan", "before i go out")):
            return "brief"
        if any(k in q for k in ("gold lap", "reference lap", " aj ", "gold standard")):
            return "gold_lap"
        if any(k in q for k in ("weather", "fog", "conditions", "greasy", "track temperature")):
            return "weather"
        if any(k in q for k in ("practice plan", "laps available", "how should i structure", "i have")):
            return "session_plan"
        if any(k in q for k in ("incident", "moment at", "close call", "scary", "saved it", "nearly off")):
            return "incident"
        if any(k in q for k in ("race pace", "stint", "degradation", "tyre drop", "20 laps", "how consistent")):
            return "race_pace"
        if any(k in q for k in ("target", "goal", "pb target", "lap time goal", "what time should")):
            return "goal"
        if any(k in q for k in ("variance", "consistency", "consistent", "inconsistent", "mental map")):
            return "mental_map"
        if any(k in q for k in ("audio", "tts", "cue script", "pace notes", "voice script")):
            return "voice_script"
        if any(k in q for k in ("vs lap", "compare lap", "why was lap", "lap 1 vs", "lap 2 vs")):
            return "lap_comparison"
        if any(k in q for k in ("turn ", " t6", " t7", " t10", " t11", "corner", "carousel")):
            return "corner"
        if any(k in q for k in ("progress", "improving", "getting faster", "this month", "over sessions")):
            return "progress"
        if any(k in q for k in ("setup", "understeer", "oversteer", "balance", "car feel", "nervous mid")):
            return "setup"
        if any(k in q for k in ("frustrated", "frustration", "plateau", "not working", "motivation")):
            return "mindset"
        if any(k in q for k in ("agent trace", "slowest agent", "which agent", "tool call", "agent latency")):
            return "agent_meta"
        return "telemetry"

    # ── Data agents ────────────────────────────────────────────────────────────

    telemetry_agent = Agent(
        name="TelemetryAgent",
        model=_model,
        output_key="telemetry_data",
        description="Session data: laps, coaching notes, and telemetry signals for a single session.",
        tools=[query_pitwall_db, get_session_highlights],
    )

    lap_comparison_agent = Agent(
        name="LapComparisonAgent",
        model=_model,
        description="Frame-by-frame delta between two laps. Identifies where time was gained or lost.",
        tools=[get_lap_delta, query_pitwall_db],
    )

    corner_coach_agent = Agent(
        name="CornerCoachAgent",
        model=_model,
        description="Grade history, coaching notes, and improvement trend for one corner across sessions.",
        tools=[get_corner_history, query_pitwall_db],
    )

    progress_tracker_agent = Agent(
        name="ProgressTrackerAgent",
        model=_model,
        description="Multi-session lap time trend, corner arcs, and plateau detection.",
        tools=[get_progress_report, query_pitwall_db],
    )

    setup_advisor_agent = Agent(
        name="SetupAdvisorAgent",
        model=_model,
        description="Reads telemetry to infer car balance — coasting, oscillation, brake pressure.",
        tools=[get_setup_indicators, query_pitwall_db],
    )

    highlight_finder_agent = Agent(
        name="HighlightFinderAgent",
        model=_model,
        output_key="highlights_data",
        description="Finds session best moments: fastest lap, peak grip, cleanest sector.",
        tools=[get_session_highlights, query_pitwall_db],
    )

    mindset_coach_agent = Agent(
        name="MindsetCoachAgent",
        model=_model,
        description="Plateau and frustration coaching. Detects stagnation and suggests mindset resets.",
        tools=[get_progress_report, get_corner_history, query_pitwall_db],
    )

    gold_lap_agent = Agent(
        name="GoldLapAgent",
        model=_model,
        description="Compares driver's best lap to AJ's gold standard corner-by-corner with leverage weights.",
        tools=[get_gold_lap_comparison, query_pitwall_db],
    )

    weather_adaptation_agent = Agent(
        name="WeatherAdaptationAgent",
        model=_model,
        description="Translates Sonoma's 4 weather phases into concrete line, braking, and tyre advice.",
        tools=[get_weather_adaptation_context, query_pitwall_db],
    )

    session_planner_agent = Agent(
        name="SessionPlannerAgent",
        model=_model,
        description="Builds a lap-by-lap practice plan given N laps, weighted by corner leverage and weakness.",
        tools=[get_session_plan_context, query_pitwall_db],
    )

    incident_review_agent = Agent(
        name="IncidentReviewAgent",
        model=_model,
        description="Detects over-limit grip events, emergency brakes, and steering saves in telemetry.",
        tools=[get_incident_moments, query_pitwall_db],
    )

    race_pace_agent = Agent(
        name="RacePaceAgent",
        model=_model,
        description="Models lap time degradation to separate qualifying pace from sustainable race pace.",
        tools=[get_race_pace_model, query_pitwall_db],
    )

    goal_setting_agent = Agent(
        name="GoalSettingAgent",
        model=_model,
        description="Sets realistic PB targets from improvement rate and corner leverage. Top 3 corners to attack.",
        tools=[get_goal_targets, get_progress_report, query_pitwall_db],
    )

    mental_map_agent = Agent(
        name="MentalMapAgent",
        model=_model,
        description="Corner-by-corner speed variance map. High variance = inconsistent; low = repeatable.",
        tools=[get_track_variance_map, query_pitwall_db],
    )

    voice_script_agent = Agent(
        name="VoiceScriptAgent",
        model=_model,
        description="Generates 2-3 word TTS cue phrases per driving phase per corner. Writes to audio cache.",
        tools=[get_audio_script_context, save_voice_scripts],
    )

    pedagogy_agent = Agent(
        name="PedagogyAgent",
        model=_model,
        output_key="pedagogy_data",
        description="Maps driver profile to Ross Bentley concepts. Recommends one focus concept per session.",
        tools=[query_pitwall_db],
    )

    agent_meta_agent = Agent(
        name="AgentMetaAgent",
        model=_model,
        description="Answers meta questions about agent performance: slowest agents, most-called tools, trace history.",
        tools=[get_agent_telemetry],
    )

    # ── Output agents (separate instances per pipeline — no shared mutable state) ──

    _NARRATIVE_INSTRUCTION = (
        "Write the final coaching narrative from the structured data below.\n\n"
        "Session highlights: {highlights_data}\n"
        "Telemetry analysis: {telemetry_data}\n"
        "Pedagogy context:   {pedagogy_data}\n\n"
        "Output format rules:\n"
        "- Brief: 2-4 sentences + FOCUS list of 3 items\n"
        "- Debrief: 1 highlight opener + 3-item next-session focus\n"
        "- Q&A: conversational, max 4 sentences, specific data references\n"
        "- Voice scripts: 2-3 word rally-style cues in T-Rod's voice\n"
        "Always end with [EMOTION:tag] — one of: "
        "neutral, encouraging, focused, concerned, excited."
    )

    # Debrief uses all three output_keys; brief only needs pedagogy_data.
    # Separate instances prevent session-state bleed if requests overlap.
    narrative_agent = Agent(
        name="NarrativeAgent",
        model=_model,
        description="Generates all human-facing coaching text. Always the final output stage.",
        instruction=_NARRATIVE_INSTRUCTION,
        tools=[],
    )

    _narrative_brief = Agent(
        name="NarrativeAgentBrief",
        model=_model,
        description="Generates pre-session brief text from pedagogy context.",
        instruction=_NARRATIVE_INSTRUCTION,
        tools=[],
    )

    _narrative_debrief = Agent(
        name="NarrativeAgentDebrief",
        model=_model,
        description="Generates post-session debrief text from highlights, telemetry, and pedagogy.",
        instruction=_NARRATIVE_INSTRUCTION,
        tools=[],
    )

    # ── Pipelines ──────────────────────────────────────────────────────────────

    _debrief_data_phase = ParallelAgent(
        name="DebriefDataPhase",
        sub_agents=[highlight_finder_agent, telemetry_agent, pedagogy_agent],
    )

    debrief_pipeline = SequentialAgent(
        name="DebriefPipeline",
        sub_agents=[_debrief_data_phase, _narrative_debrief],
    )

    brief_pipeline = SequentialAgent(
        name="BriefPipeline",
        sub_agents=[pedagogy_agent, _narrative_brief],
    )

    # ── Intent → specialist agent map (QA paths) ───────────────────────────────

    _INTENT_TO_AGENT: dict[str, Agent] = {
        "gold_lap":       gold_lap_agent,
        "weather":        weather_adaptation_agent,
        "session_plan":   session_planner_agent,
        "incident":       incident_review_agent,
        "race_pace":      race_pace_agent,
        "goal":           goal_setting_agent,
        "mental_map":     mental_map_agent,
        "voice_script":   voice_script_agent,
        "lap_comparison": lap_comparison_agent,
        "corner":         corner_coach_agent,
        "progress":       progress_tracker_agent,
        "setup":          setup_advisor_agent,
        "mindset":        mindset_coach_agent,
        "agent_meta":     agent_meta_agent,
        "telemetry":      telemetry_agent,
    }

    # ── Root orchestrator ──────────────────────────────────────────────────────

    class PitwallOrchestrator(BaseAgent):
        """Deterministic-routing orchestrator. _classify_intent replaces LLM routing."""

        async def _run_async_impl(
            self, ctx
        ) -> AsyncGenerator:  # type: ignore[override]
            try:
                query: str = ctx.user_content.parts[0].text
            except (AttributeError, IndexError, TypeError) as exc:
                _log.warning("PitwallOrchestrator: could not read user_content (%s) — defaulting to telemetry", exc)
                query = ""

            intent = _classify_intent(query)

            if intent == "debrief":
                async for event in debrief_pipeline.run_async(ctx):
                    yield event
            elif intent == "brief":
                async for event in brief_pipeline.run_async(ctx):
                    yield event
            else:
                agent = _INTENT_TO_AGENT.get(intent, telemetry_agent)
                async for event in agent.run_async(ctx):
                    yield event

    coach_orchestrator = PitwallOrchestrator(
        name="PitwallOrchestrator",
        sub_agents=[
            debrief_pipeline,
            brief_pipeline,
            telemetry_agent,
            lap_comparison_agent,
            corner_coach_agent,
            progress_tracker_agent,
            setup_advisor_agent,
            highlight_finder_agent,
            mindset_coach_agent,
            gold_lap_agent,
            weather_adaptation_agent,
            session_planner_agent,
            incident_review_agent,
            race_pace_agent,
            goal_setting_agent,
            mental_map_agent,
            voice_script_agent,
            pedagogy_agent,
            narrative_agent,
            agent_meta_agent,
        ],
    )

    # ── Tracing plugin — writes to _pending_traces, drained by bridge → DuckDB ──

    class PitwallTracingPlugin(BasePlugin):
        """Logs every agent run and tool call to the module-level trace buffer.

        pitwall_bridge.py drains get_pending_traces() after each run_adk()
        call and writes rows to the agent_traces DuckDB table (ADR-021).
        """
        name = "pitwall_tracing"

        def before_agent(self, ctx, **_kw):
            # Store start time in temp state so after_agent can compute latency.
            # temp: prefix means ADK discards it after this invocation.
            try:
                ctx.session.state["temp:_agent_start_ms"] = time.time() * 1000
            except Exception:
                pass
            return None

        def after_agent(self, ctx, **_kw):
            try:
                start = ctx.session.state.pop("temp:_agent_start_ms", None)
                latency = round(time.time() * 1000 - start, 1) if start else None
                agent_name = getattr(ctx, "agent_name", None) or "unknown"
                trace_id = getattr(ctx.session, "id", "unknown") if hasattr(ctx, "session") else "unknown"
                with _trace_lock:
                    _pending_traces.append({
                        "trace_id":   trace_id,
                        "agent_name": agent_name,
                        "event_type": "agent",
                        "detail":     "",
                        "latency_ms": latency,
                        "success":    True,
                    })
            except Exception:
                pass
            return None

        def after_tool(self, tool, args, tool_context, response, **_kw):
            try:
                tool_name = getattr(tool, "name", str(tool))
                agent_name = getattr(tool_context, "agent_name", "unknown")
                trace_id = (
                    getattr(tool_context.session, "id", "unknown")
                    if hasattr(tool_context, "session") else "unknown"
                )
                with _trace_lock:
                    _pending_traces.append({
                        "trace_id":   trace_id,
                        "agent_name": agent_name,
                        "event_type": "tool",
                        "detail":     tool_name,
                        "latency_ms": None,
                        "success":    True,
                    })
            except Exception:
                pass
            return None

    # ── Runner (canonical ADK invocation — BaseAgent has no .run() shortcut) ──

    _session_service = InMemorySessionService()
    _runner = Runner(
        agent=coach_orchestrator,
        app_name="pitwall",
        session_service=_session_service,
        plugins=[PitwallTracingPlugin()],
    )

    async def _get_or_create_session(user_id: str):
        """Return a persistent ADK session for this driver, creating one if needed.

        Reusing the session lets LiteRT-LM clone the KV cache for the agent
        system instructions rather than rebuilding it from scratch each call.
        Sessions auto-reset after _SESSION_MAX_TURNS to prevent context overflow.
        """
        with _driver_sessions_lock:
            existing_sid = _driver_sessions.get(user_id)
            turns = _session_turn_count.get(user_id, 0)
            if turns >= _SESSION_MAX_TURNS:
                _log.info("ADK session for '%s' hit %d turns — rotating", user_id, turns)
                existing_sid = None
                _driver_sessions.pop(user_id, None)
                _session_turn_count.pop(user_id, None)

        if existing_sid:
            try:
                session = await _session_service.get_session(
                    app_name="pitwall", user_id=user_id, session_id=existing_sid
                )
                if session:
                    return session
            except Exception as exc:
                _log.warning("ADK session lookup failed (%s) — creating new", exc)

        session = await _session_service.create_session(
            app_name="pitwall", user_id=user_id
        )
        with _driver_sessions_lock:
            _driver_sessions[user_id] = session.id
            _session_turn_count[user_id] = 0
        _log.debug("ADK session created: user=%s sid=%s", user_id, session.id)
        return session

    async def _run_adk_async(prompt: str, user_id: str) -> str:
        session = await _get_or_create_session(user_id)
        final_text = ""
        async for event in _runner.run_async(
            user_id=user_id,
            session_id=session.id,
            new_message=Content(parts=[Part(text=prompt)]),
        ):
            if hasattr(event, "is_final_response") and event.is_final_response():
                if event.content and event.content.parts:
                    final_text = event.content.parts[0].text or final_text
        with _driver_sessions_lock:
            _session_turn_count[user_id] = _session_turn_count.get(user_id, 0) + 1
        return final_text

    def run_adk(prompt: str, user_id: str = "driver") -> str:  # noqa: F811
        """Synchronous entry point for pitwall_bridge.py (Flask is sync).

        Reuses the persistent ADK session for this driver so the LiteRT-LM
        server can clone the KV cache for warm requests.
        """
        return asyncio.run(_run_adk_async(prompt, user_id))

    # Registry exposed via GET /coach/agents for Vue PWA discovery
    AGENT_REGISTRY = [
        {"name": "TelemetryAgent",
         "role": "Session data — laps, coaching notes, telemetry signals",
         "example_questions": ["What happened in today's session?",
                               "How many coaching notes fired?"]},
        {"name": "LapComparisonAgent",
         "role": "Lap-vs-lap delta analysis",
         "example_questions": ["Why was lap 4 faster than lap 2?",
                               "What changed between my best and worst lap?"]},
        {"name": "CornerCoachAgent",
         "role": "Single-corner deep-dive across sessions",
         "example_questions": ["What am I doing wrong at Turn 7?",
                               "Am I improving at the Carousel?"]},
        {"name": "ProgressTrackerAgent",
         "role": "Multi-session improvement arc",
         "example_questions": ["Am I getting faster?",
                               "Where am I stuck?"]},
        {"name": "SetupAdvisorAgent",
         "role": "Telemetry → car balance and setup inference",
         "example_questions": ["My car feels understeery at T3, what does the data say?",
                               "What setup changes should I try?"]},
        {"name": "HighlightFinderAgent",
         "role": "Best moments and peak performance finder",
         "example_questions": ["What was my best moment today?",
                               "When was I highest on the limit?"]},
        {"name": "MindsetCoachAgent",
         "role": "Plateau detection + motivational coaching",
         "example_questions": ["I'm stuck at the same lap time.",
                               "I'm frustrated, nothing is clicking."]},
        {"name": "GoldLapAgent",
         "role": "Compare your lap against AJ's gold standard corner by corner",
         "example_questions": ["How do I compare to the reference lap?",
                               "Which corner costs me the most vs gold?"]},
        {"name": "WeatherAdaptationAgent",
         "role": "Weather phase → concrete line and braking adjustments",
         "example_questions": ["How should I adjust for morning fog?",
                               "The track is greasy, what should I do differently?"]},
        {"name": "SessionPlannerAgent",
         "role": "N laps available → structured lap-by-lap practice plan",
         "example_questions": ["I have 8 laps, what should I focus on?",
                               "Build me a practice plan for this session."]},
        {"name": "IncidentReviewAgent",
         "role": "Anomaly detection — over-limit events, emergency brakes, saves",
         "example_questions": ["I had a moment at T6, what does the data show?",
                               "Did I have any close calls today?"]},
        {"name": "RacePaceAgent",
         "role": "Lap degradation model — qualifying pace vs sustainable race pace",
         "example_questions": ["What would my race pace be?",
                               "How much do my tyres drop off over a stint?"]},
        {"name": "GoalSettingAgent",
         "role": "Next realistic PB target + top 3 corners to attack",
         "example_questions": ["What lap time should I target next session?",
                               "Where should I focus to go faster?"]},
        {"name": "MentalMapAgent",
         "role": "Corner-by-corner consistency map from speed variance",
         "example_questions": ["Where is my biggest lap-to-lap variance?",
                               "Which corners am I most consistent through?"]},
        {"name": "VoiceScriptAgent",
         "role": "Pre-rendered TTS audio cache script generator (ADR-017)",
         "example_questions": ["Generate cue scripts for Turn 7.",
                               "Write the audio cache phrases for T10."]},
        {"name": "PedagogyAgent",
         "role": "Driver profile + Ross Bentley concept selection",
         "example_questions": ["What Bentley concept should I focus on today?",
                               "What is my weakest skill right now?"]},
        {"name": "NarrativeAgent",
         "role": "Final output — converts everything into driver language",
         "example_questions": ["(always called last by the orchestrator)"]},
        {"name": "AgentMetaAgent",
         "role": "ADK system telemetry — agent latency, tool call frequency, trace history",
         "example_questions": ["Which agent is slowest?",
                               "What tools are called most often?"]},
    ]
