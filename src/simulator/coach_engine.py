"""
Coach Engine — LLM-agnostic verbal coaching layer.

Sits on top of sonic_model_v2's continuous-tone cues. Where the sonic model
gives reflexive, sub-50ms feedback (pitch IS the delta), the coach engine
gives rally-style pace notes for the *upcoming* corner, debounced to fire
every few seconds on straights only.

Two implementations behind one interface:

  - RuleCoach       : zero-dependency templated phrases keyed by a small
                      pedagogical-vector matcher (Ross Bentley curriculum
                      distilled in project_pitwall_bentley_pedagogy.md).
  - LlamaCppCoach   : POSTs to an OpenAI-compatible /v1/chat/completions
                      endpoint. Defaults to http://127.0.0.1:8080 (the
                      llama.cpp llama-server default per
                      https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md).
                      The same client works against Ollama, OpenAI, Together,
                      Groq, etc. — anything OpenAI-compatible.

The arbiter (P3 safety / P2 technique / P1 strategy with cooldown + corner
suppression) lives at the call site in pitwall_app.SessionManager so this
file stays a pure "given context, produce zero or one CoachingMessage".
"""

from __future__ import annotations

import enum
import json
import os
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional


class CoachMode(enum.Enum):
    """Three operational modes per ADR-014 — same engine, different prompts."""
    DURING_DRIVE = "during_drive"   # one-line pace note per burst (existing)
    PRE_BRIEF    = "pre_brief"      # pre-session focus plan paragraph
    POST_SESSION = "post_session"   # full session debrief narrative


# ─── Data types ───────────────────────────────────────────────────────────────


@dataclass
class CoachContext:
    """Everything the coach needs to decide whether and what to say."""
    driver_level: str            # "beginner" | "intermediate" | "pro"
    track_name: str
    # Upcoming corner (may be None if no corner within lookahead)
    next_corner_name: str = ""
    next_corner_direction: str = ""    # "L" | "R" | ""
    next_corner_severity: int = 0      # 1-6
    meters_to_entry: float = 999.0
    next_brake_zone_m: float = 0.0
    next_brake_peak_bar: float = 0.0
    next_apex_speed_kmh: float = 0.0
    next_elevation_change_m: float = 0.0
    # Marker context — preferred over raw distance when present
    next_brake_marker_label: str = ""    # e.g. "the bridge", "the bump"
    next_apex_marker_label: str = ""     # e.g. "the K-wall bend"
    next_corner_tip: str = ""            # one-line Bentley-style tip per corner
    next_corner_nickname: str = ""       # e.g. "the Carousel", "Calamity Corner"
    # Current state
    speed_kmh: float = 0.0
    brake_pct: float = 0.0
    throttle_pct: float = 0.0
    g_lat: float = 0.0
    g_long: float = 0.0
    in_corner: bool = False
    past_apex: bool = False
    # Pedagogy
    bentley_concept: str = ""
    bentley_tip: str = ""


@dataclass
class CoachingMessage:
    """One verbal coaching line, ready for the arbiter."""
    text: str
    priority: int              # 1=strategy, 2=technique, 3=safety
    layer: str = "coach"
    reason: str = ""           # debug only — why we said this


# ─── Pedagogical vector matcher ───────────────────────────────────────────────


def match_bentley_concept(ctx: CoachContext) -> tuple[str, str]:
    """Pick the single most relevant Ross Bentley concept for this frame.

    Returns (concept_name, one_short_tip). Empty strings if nothing fits.
    Source: docs/Performance-Driving-Illustrated-2-23-24.pdf, distilled in
    memory/project_pitwall_bentley_pedagogy.md.
    """
    # Trail brake on entry
    if ctx.in_corner and ctx.brake_pct > 10 and abs(ctx.g_lat) > 0.4:
        return "trail_brake", "smooth release as steering increases"
    # Entry-released-brake antipattern
    if ctx.in_corner and ctx.brake_pct < 1 and abs(ctx.g_lat) > 0.6:
        return "entry_release", "keep some brake to load the fronts"
    # Past apex with no throttle = wasted exit
    if ctx.past_apex and ctx.throttle_pct < 20 and abs(ctx.g_lat) > 0.3:
        return "exit_speed", "throttle now, exit speed beats corner speed"
    # Coast on a straight
    if not ctx.in_corner and ctx.throttle_pct < 5 and ctx.brake_pct < 2 \
            and ctx.speed_kmh > 50 and ctx.meters_to_entry > 100:
        return "hustle", "100 percent throttle even briefly"
    # Approaching brake zone
    if 30 < ctx.meters_to_entry < ctx.next_brake_zone_m + 30 and ctx.brake_pct < 2:
        return "eob", "look at end of braking, not start"
    # Downhill braking
    if ctx.next_elevation_change_m < -5 and ctx.meters_to_entry < 200:
        return "downhill_brake", "downhill, brake earlier"
    # Uphill braking
    if ctx.next_elevation_change_m > 5 and ctx.meters_to_entry < 200:
        return "uphill_brake", "uphill, zone is shorter"
    # Default approach reminder for upcoming corner
    if 0 < ctx.meters_to_entry < 250:
        return "late_apex", "late apex for a faster exit"
    # On a clean straight far from anything
    return "look_ahead", "eyes far ahead"


# ─── Engine interface ─────────────────────────────────────────────────────────


class CoachEngine:
    """Base interface. Subclass and override propose()."""

    name: str = "base"

    def propose(self, ctx: CoachContext) -> Optional[CoachingMessage]:
        raise NotImplementedError


# ─── Templated rule coach (no LLM) ────────────────────────────────────────────


class RuleCoach(CoachEngine):
    """Zero-dependency templated coach. Always available, useful as a baseline
    and as the fallback when the LLM is unreachable.

    Phrasing follows the rally-pace-note convention:
        <distance> <corner direction> <severity> [, brake at <m>] [, hint]
    Tuned per driver level.
    """

    name = "rule"

    def __init__(self, driver_level: str = "intermediate"):
        self.driver_level = driver_level

    def propose(self, ctx: CoachContext) -> Optional[CoachingMessage]:
        # Decide priority & whether to fire at all
        if not ctx.next_corner_name and not ctx.in_corner:
            return None
        if 0 < ctx.meters_to_entry < 250:
            prio = 1  # strategy: corner ahead
        elif ctx.in_corner and ctx.bentley_concept in ("entry_release",):
            prio = 2  # technique correction mid-corner
        elif ctx.bentley_concept == "hustle":
            prio = 1
        else:
            return None

        line = self._render(ctx)
        if not line:
            return None
        return CoachingMessage(
            text=line,
            priority=prio,
            reason=f"rule:{ctx.bentley_concept}",
        )

    def _render(self, ctx: CoachContext) -> str:
        c = ctx
        d = "right" if c.next_corner_direction == "R" else "left" if c.next_corner_direction == "L" else ""
        sev = c.next_corner_severity
        m_in = int(round(c.meters_to_entry))
        bz = int(round(c.next_brake_zone_m))
        apex = int(round(c.next_apex_speed_kmh))
        elev_hint = ""
        if c.next_elevation_change_m <= -5:
            elev_hint = ", downhill"
        elif c.next_elevation_change_m >= 5:
            elev_hint = ", uphill"

        # Use the corner's nickname when one exists ("the Carousel" beats "Turn 6")
        corner_name = c.next_corner_nickname or c.next_corner_name
        # Prefer the named brake marker when available ("brake at the bridge"
        # beats "brake at 60m") — Sonoma intel doc finding.
        brake_phrase = (
            f"brake at {c.next_brake_marker_label}"
            if c.next_brake_marker_label
            else (f"brake {bz}" if bz > 0 else "")
        )

        if self.driver_level == "beginner":
            if 0 < m_in < 250 and corner_name:
                if c.bentley_concept == "downhill_brake":
                    return f"{corner_name} ahead, brake early, it goes down"
                if c.bentley_concept == "eob":
                    return f"{corner_name} coming up, look into the corner"
                if c.next_brake_marker_label:
                    return f"{corner_name} in {m_in} meters, {brake_phrase}"
                return f"{corner_name} in {m_in} meters, brake smoothly"
            if c.bentley_concept == "hustle":
                return "full throttle here"
            if c.bentley_concept == "entry_release":
                return "keep a little brake on"
            return ""

        if self.driver_level == "pro":
            if 0 < m_in < 250 and corner_name:
                if c.next_brake_marker_label:
                    return f"{corner_name} {m_in}m, {c.next_brake_marker_label}, apex {apex}"
                return f"{corner_name} {m_in}m, BP {bz}m, apex {apex}"
            if c.bentley_concept == "hustle":
                return "hustle"
            return ""

        # intermediate (default) — pace-note shorthand with optional landmark
        if 0 < m_in < 250 and corner_name:
            head = f"{m_in}, {d} {sev}".strip(", ")
            base = head
            if c.next_corner_nickname:
                base = f"{m_in}, {c.next_corner_nickname}"
            if brake_phrase:
                base += f", {brake_phrase}"
            if c.bentley_concept == "late_apex":
                base += ", late apex"
            elif c.bentley_concept == "eob":
                base += ", look in"
            base += elev_hint
            return base
        if c.bentley_concept == "hustle":
            return "full throttle, even briefly"
        if c.bentley_concept == "entry_release":
            return "trail brake to apex"
        if c.bentley_concept == "exit_speed":
            return "throttle now, exit speed"
        return ""


# ─── System prompts (single source of truth, used by every LLM coach) ───────
#
# Backend owns all prompt construction. Frontends should never assemble or
# tune these — they call the bridge and render the resulting text. Adding
# a new track or new driver level = edit here, no Kotlin/Flutter change.

_BASE_SYSTEM_PROMPT = (
    "You are a rally-style racing co-driver riding shotgun. Your job is "
    "to call the next corner BEFORE the driver reaches it, not narrate "
    "what they're doing now. Coaching is grounded in Ross Bentley's "
    "Speed Secrets curriculum: smooth is fast, exit speed beats corner "
    "speed, late apex, end-of-braking before begin-of-braking, look "
    "where you want to go.\n\n"
    "RULES:\n"
    "- ONE line. No emoji. No quotes around the line.\n"
    "- Reply with an EMPTY string when there is nothing useful to say.\n"
    "- Prefer named landmarks over distances when one is given.\n"
    "- Never overlap a previous message — if in doubt, stay silent.\n"
    "- Safety overrides everything: if gLat > 1.5G or combo G > 2.0, "
    "say only the safety word ('Lift!', 'Brake!', 'Easy!') and nothing else.\n"
)

_LEVEL_SYSTEM_PROMPT = {
    "beginner": (
        "DRIVER LEVEL: beginner. Use full short sentences, simple verbs, "
        "8–12 words max. Say what to do, not why. Encouragement is fine "
        "between corners. Examples: 'Brake at the bridge, then turn in.', "
        "'Stay smooth here.', 'Wait for the corner to open.'"
    ),
    "intermediate": (
        "DRIVER LEVEL: intermediate. Use rally-pace-note shorthand: "
        "<distance> <direction> <severity> [, named landmark or technique hint] "
        "[, hint about elevation/grip]. 6–12 words. Examples: "
        "'185, left 6, brake at the bridge, uphill', "
        "'128, the Carousel, brake at the slight crest, downhill', "
        "'230, Calamity Corner, wait for the bump.'"
    ),
    "pro": (
        "DRIVER LEVEL: pro. Terse: corner_id + meters + landmark or "
        "delta. 3–7 words. Examples: 'T11 230m, the bump.', "
        "'T6 80m, lift not brake.', 'BP +8m.'"
    ),
}

_TRACK_LORE = {
    "Sonoma Raceway": (
        "TRACK CONTEXT: Sonoma Raceway, 4.06 km, 49 m elevation. "
        "Reference vocabulary at Sonoma is environmental, not numeric. "
        "Use these names when relevant: 'the bridge' (T2 brake), "
        "'the K-wall bend' (T1 apex), 'the Carousel' (T6), "
        "'the slight crest' (T6 brake), 'the 300 board' (T7 brake), "
        "'the Toyota sign letters' (T10 visual), "
        "'Calamity Corner' (T11 hairpin), "
        "'the bump where the road widens left' (T11 brake — wait for "
        "the car to settle), 'the third tire stack' (T11 apex). "
        "\n"
        "STRATEGY: T3 is a give-away (sacrifice for T3a/T4); "
        "T5 is throwaway (preserve T6 entry); T6 punishes early throttle; "
        "T10 is fastest — most drivers brake when they only need a lift; "
        "T11 has no painted brake board — the bump is the reference. "
        "\n"
        "T-ROD VOICE (canonical pace-note phrasings, BMW M3 intermediate-driver "
        "session at Sonoma): "
        "'Distance is king' for long sweepers (T6, T7, T11 — cut the inside, "
        "do not open up to carry mph). "
        "'Be closer to the tire stacks' for T11. "
        "'Open up nine, straight shot to ten.' "
        "'Single apex, treat as double' for T7 (cut entry, rotate, hit "
        "second apex). "
        "'Just go 100' when the driver hesitates at 60% throttle "
        "(60→100% is only ~20 ft-lbs of torque). "
        "'Wait, you're not at the apex yet' to delay early throttle. "
        "'Roll the brake to the apex' (peak then taper, not square-wave; "
        "off-brake at maximum-grip mid-corner). "
        "'Trust the curb, it catches you' (Sonoma's serrated berms are "
        "banked in the driver's favour). "
        "'Cool-down means same line, slower.' "
        "M3-specific: boosted brake is feathery — focus on application "
        "before adding more inputs."
    ),
}


_PRE_BRIEF_SYSTEM_PROMPT = (
    "You are a professional race coach giving a PRE-SESSION BRIEF before "
    "the driver heads out. The driver is at Sonoma Raceway in a BMW M3, "
    "intermediate level. Goal of the brief: focus the driver's attention on "
    "their three chosen corners + today's specific conditions + one safety "
    "reminder. Keep it warm but tight — drivers are putting a helmet on, not "
    "studying. Use named landmarks ('the bridge', 'the bump', 'the Toyota "
    "sign letters'). Use canonical T-Rod phrasings where they fit. End with "
    "the literal token <FOCUS> followed by a JSON list of exactly 3 short "
    "actionable focus items. Total ~150 words."
)


_POST_SESSION_SYSTEM_PROMPT = (
    "You are a professional race engineer giving a POST-SESSION DEBRIEF. "
    "The driver just finished a session at Sonoma Raceway in a BMW M3, "
    "intermediate level. The user message contains structured session data: "
    "scorecard with per-corner A-F grades, time-loss decomposition per "
    "corner, top highlight moments, stat cards, slip-angle band, EoB "
    "summary, consistency, and incidents. Use this data — DO NOT invent "
    "events that aren't in the data. Speak in T-Rod's canonical voice "
    "where it fits ('Distance is king', 'Just go 100', 'Wait, you're not "
    "at the apex yet', 'Roll the brake to the apex'). Reference named "
    "Sonoma landmarks. Structure: 1) one-paragraph headline assessment, "
    "2) one paragraph per highlight (≤3 of them, lap N, what happened, "
    "what to do next time), 3) one paragraph identifying the single "
    "biggest lap-time-leverage opportunity (typically T10 or T11), 4) "
    "three focus items for next session formatted as a JSON list after "
    "the literal token <NEXT_FOCUS>. Total ~300 words."
)


_SYSTEM_PROMPTS_BY_MODE = {
    CoachMode.DURING_DRIVE: _BASE_SYSTEM_PROMPT,
    CoachMode.PRE_BRIEF:    _PRE_BRIEF_SYSTEM_PROMPT,
    CoachMode.POST_SESSION: _POST_SESSION_SYSTEM_PROMPT,
}


def build_system_prompt(driver_level: str, track_name: str = "",
                        mode: CoachMode = CoachMode.DURING_DRIVE) -> str:
    """Compose the full system prompt for any LLM coach.

    Single source of truth used by LitertCoach (and any future cloud coach).
    Frontends never call this — they call the bridge.
    """
    base = _SYSTEM_PROMPTS_BY_MODE.get(mode, _BASE_SYSTEM_PROMPT)
    parts = [base.strip()]
    if mode == CoachMode.DURING_DRIVE:
        parts.append(
            _LEVEL_SYSTEM_PROMPT.get(
                driver_level, _LEVEL_SYSTEM_PROMPT["intermediate"]
            ).strip()
        )
    if track_name and track_name in _TRACK_LORE:
        parts.append(_TRACK_LORE[track_name].strip())
    return "\n\n".join(parts)


def build_pre_brief_user_prompt(
    driver_id: str,
    today_iso: str,
    weather_phase: str,
    surface_state: str,
    markers_selected: list[str],
    weakest_recent_corner: Optional[str],
    biggest_recent_improvement: Optional[dict],
    danger_zones_today: list[str],
    goal: str = "personal best lap",
) -> str:
    """User prompt for PRE_BRIEF mode."""
    parts = [
        f"DRIVER: {driver_id}",
        f"DATE: {today_iso}",
        f"WEATHER PHASE: {weather_phase} ({surface_state})",
        f"GOAL: {goal}",
        f"FOCUS CORNERS THIS SESSION: {', '.join(markers_selected) or '(driver did not pick)'}",
    ]
    if weakest_recent_corner:
        parts.append(f"WEAKEST RECENT CORNER: {weakest_recent_corner}")
    if biggest_recent_improvement:
        parts.append(
            f"BIGGEST RECENT IMPROVEMENT: "
            f"{biggest_recent_improvement.get('corner')} "
            f"(score Δ {biggest_recent_improvement.get('delta_score', 0):+.2f})"
        )
    if danger_zones_today:
        parts.append(f"DANGER ZONES TO WATCH: {'; '.join(danger_zones_today)}")
    parts.append("Brief the driver now.")
    return "\n".join(parts)


def build_post_session_user_prompt(bundle: dict) -> str:
    """User prompt for POST_SESSION mode — feeds the analyzer bundle to the LLM.

    Keeps the prompt compact: scorecard + top highlights + stats + the
    handful of analytics the coach needs to talk about. Skips full friction
    samples / hustle map (those are for the frontend, not the LLM).
    """
    sc = bundle.get("scorecard") or {}
    hs = bundle.get("highlights") or []
    stats = bundle.get("stats") or {}
    eob = bundle.get("eob") or {}
    sb = bundle.get("slip_band") or {}
    cons = bundle.get("consistency") or {}

    # Compact scorecard
    scorecard_lines = []
    for c in sc.get("corners", []):
        attrs = c.get("time_loss_attribution") or []
        biggest = (max(attrs, key=lambda a: a["seconds_lost"]) if attrs else None)
        scorecard_lines.append(
            f"  {c['corner']:<8} grade={c['grade']:<2} "
            f"Δt={c['delta_time_s']:+.2f}s "
            f"apex={c['apex_delta_kmh']:+.1f}kmh "
            f"exit={c['exit_delta_kmh']:+.1f}kmh "
            f"BP={c['brake_point_delta_m']:+.1f}m"
            + (f" cause={biggest['cause']}({biggest['seconds_lost']:.2f}s)"
               if biggest else "")
        )

    highlight_lines = []
    for h in hs[:5]:
        highlight_lines.append(
            f"  [{h['severity']}] {h['title']} — {h['narrative_seed']}"
        )

    parts = [
        f"SESSION: {sc.get('session_id', '?')}",
        f"LAPS: {sc.get('n_laps', 0)} — best {sc.get('best_lap_s', 0):.2f}s "
        f"vs gold {sc.get('gold_lap_s', 0):.2f}s "
        f"(Δ {sc.get('best_lap_s', 0) - sc.get('gold_lap_s', 0):+.2f}s)",
        f"SESSION GRADE: {sc.get('session_grade', '?')} "
        f"({sc.get('weighted_total_pct', 0):.1%})",
        "",
        "SCORECARD (best pass per corner):",
        *(scorecard_lines or ["  (no scorecard available)"]),
        "",
        "TOP HIGHLIGHTS:",
        *(highlight_lines or ["  (no highlights detected)"]),
        "",
        f"STATS: top {stats.get('top_speed_kmh', '?')} km/h, "
        f"max {stats.get('max_combo_g', '?')} G, "
        f"max brake {stats.get('max_brake_bar', '?')} bar, "
        f"longest 100% throttle {stats.get('longest_full_throttle_s', '?')} s",
        f"SLIP-ANGLE BAND: {sb.get('dominant_band', '?')} — "
        f"{sb.get('interpretation', '')}",
        f"EOB: avg nothing-time {eob.get('average_nothing_time_s', 0):.2f}s, "
        f"worst at {eob.get('worst_corner', '—')}",
        f"CONSISTENCY: lap std {cons.get('lap_time_std', 0):.2f}s; "
        f"most variable corner: {(cons.get('most_variable_corner') or {}).get('corner', '—')}",
        "",
        "Generate the debrief now per the system instructions.",
    ]
    return "\n".join(parts)


_USER_PROMPT_TEMPLATE = (
    "UPCOMING: {corner} {direction} sev{severity} in {m_in} m, "
    "brake_zone {bz} m peaking {bar} bar, apex {apex} km/h, elev {elev} m.\n"
    "{landmark_hint}"
    "NOW: {speed} km/h, brake {brake}%, throttle {thr}%, gLat {glat:+.2f}.\n"
    "PEDAGOGY: {concept} — {tip}\n"
    "{tip_hint}"
    "Speak the pace note now."
)


def build_user_prompt(ctx) -> str:
    """Compose the per-frame user prompt. Reuses every CoachContext field
    including the marker labels populated by build_context().
    """
    landmark_hint = ""
    if ctx.next_brake_marker_label:
        landmark_hint = f"BRAKE LANDMARK: {ctx.next_brake_marker_label}\n"
    elif ctx.next_apex_marker_label:
        landmark_hint = f"APEX LANDMARK: {ctx.next_apex_marker_label}\n"
    tip_hint = f"COACHING TIP: {ctx.next_corner_tip}\n" if ctx.next_corner_tip else ""

    return _USER_PROMPT_TEMPLATE.format(
        corner=ctx.next_corner_nickname or ctx.next_corner_name or "none",
        direction=ctx.next_corner_direction or "-",
        severity=ctx.next_corner_severity,
        m_in=int(round(ctx.meters_to_entry)) if ctx.meters_to_entry < 999 else 999,
        bz=int(round(ctx.next_brake_zone_m)),
        bar=int(round(ctx.next_brake_peak_bar)),
        apex=int(round(ctx.next_apex_speed_kmh)),
        elev=int(round(ctx.next_elevation_change_m)),
        speed=int(round(ctx.speed_kmh)),
        brake=int(round(ctx.brake_pct)),
        thr=int(round(ctx.throttle_pct)),
        glat=ctx.g_lat,
        concept=ctx.bentley_concept or "look_ahead",
        tip=ctx.bentley_tip or "eyes far ahead",
        landmark_hint=landmark_hint,
        tip_hint=tip_hint,
    )




# ─── On-device LiteRT-LM coach — MediaPipe Genai LLM Inference ────────────────


class LitertCoach(CoachEngine):
    """Backend-owned on-device LLM inference via MediaPipe Genai's
    `LlmInference` API. Targets the Gemma 4 LiteRT-LM `.task` artifact published
    at `litert-community/gemma-4-E2B-it-litert-lm` on Hugging Face.

    Why MediaPipe Genai (not tflite_runtime):
      - Gemma 4 ships as a `.task` file (LiteRT-LM format), not a raw `.tflite`.
        The `.task` bundle includes the model, tokenizer, KV-cache config, and
        prompt template — all handled by the LlmInference API.
      - On supported devices, the API picks the best backend (XNNPack on CPU,
        ML Drift on GPU, Tensor TPU when available).
      - One call: `llm.generate_response(prompt)` instead of a manual decode
        loop. No tokenizer juggling, no tensor shape gymnastics.

    On a Pixel running Termux:
        pkg install python
        pip install mediapipe
        # Push the .task file to ~/storage/shared/Pitwall/models/
        # (the canonical path is gemma-4-E2B-it.task)

    If MediaPipe isn't available, the model file is missing, or any runtime
    error occurs, this coach falls through to RuleCoach so the bridge stays
    useful. Backend keeps a single source of truth for system instructions
    via build_system_prompt / build_user_prompt per ADR-013.
    """

    name = "litert"

    DEFAULT_MODEL_PATHS = [
        # Termux on Pixel — shared storage that both Kotlin and Python can read
        "~/storage/shared/Pitwall/models/gemma-4-E2B-it.task",
        # Repo-local fallback for dev machines
        "models/gemma-4-E2B-it.task",
    ]

    def __init__(
        self,
        model_path: str = "",
        *,
        driver_level: str = "intermediate",
        max_tokens: int = 32,
        temperature: float = 0.4,
    ):
        self.driver_level = driver_level
        self.max_tokens = max_tokens
        self.temperature = temperature
        self._fallback = RuleCoach(driver_level)
        self._llm = None
        self._init_error: Optional[str] = None

        # All heavy imports are lazy + caught — LitertCoach must construct
        # cleanly on machines without mediapipe so make_coach("auto") can
        # probe + fall back without crashing the bridge.
        try:
            self._init_runtime(model_path)
        except Exception as e:
            self._init_error = f"{type(e).__name__}: {e}"

    # ---- runtime init -------------------------------------------------------

    def _init_runtime(self, model_path: str):
        try:
            from mediapipe.tasks.python.genai import inference  # type: ignore
        except ImportError as e:
            raise RuntimeError(
                f"mediapipe not installed ({e}). "
                f"On Termux: pip install mediapipe"
            )

        path = self._resolve_model_path(model_path)
        if path is None:
            raise FileNotFoundError(
                f"no Gemma 4 .task file found in {self.DEFAULT_MODEL_PATHS}"
            )

        opts = inference.LlmInferenceOptions(
            model_path=str(path),
            max_tokens=self.max_tokens,
            temperature=self.temperature,
            top_k=40,
        )
        self._llm = inference.LlmInference.create_from_options(opts)

    def _resolve_model_path(self, model_path: str) -> Optional[Path]:
        candidates = [model_path] if model_path else self.DEFAULT_MODEL_PATHS
        for c in candidates:
            if not c:
                continue
            p = Path(os.path.expanduser(c))
            if p.exists():
                return p
        return None

    # ---- public API ---------------------------------------------------------

    def health(self) -> dict:
        return {
            "loaded":   self._llm is not None,
            "error":    self._init_error or "",
            "fallback": self._fallback.name,
        }

    def propose(self, ctx: CoachContext) -> Optional[CoachingMessage]:
        # Gating mirrors RuleCoach: skip the inference when there's nothing
        # worth voicing. Saves TPU/CPU cycles when on a long straight.
        if not ctx.next_corner_name and ctx.bentley_concept in ("look_ahead", ""):
            return None
        if not (0 < ctx.meters_to_entry < 250 or ctx.bentley_concept in (
            "hustle", "entry_release", "exit_speed", "downhill_brake",
        )):
            return None

        # If runtime didn't load, behave exactly like RuleCoach
        if self._llm is None:
            return self._fallback.propose(ctx)

        try:
            text = self._infer(ctx)
        except Exception:
            return self._fallback.propose(ctx)

        text = (text or "").strip().strip('"').strip("'")
        if not text:
            return None
        text = text.splitlines()[0].strip()
        if not text:
            return None

        prio = 2 if ctx.bentley_concept == "entry_release" else 1
        return CoachingMessage(
            text=text,
            priority=prio,
            reason=f"litert:{ctx.bentley_concept}",
        )

    # ---- inference ----------------------------------------------------------

    def _infer(self, ctx: CoachContext) -> str:
        """One-shot generation via MediaPipe Genai. Combines the system and
        user prompts into the Gemma chat template the .task file expects.
        """
        system_prompt = build_system_prompt(self.driver_level, ctx.track_name)
        user_prompt = build_user_prompt(ctx)
        return self._generate(system_prompt, user_prompt)

    def _generate(self, system_prompt: str, user_prompt: str) -> str:
        """Lower-level: generate from already-built system+user prompts."""
        if self._llm is None:
            return ""
        full = (
            f"<start_of_turn>system\n{system_prompt}<end_of_turn>\n"
            f"<start_of_turn>user\n{user_prompt}<end_of_turn>\n"
            f"<start_of_turn>model\n"
        )
        return self._llm.generate_response(full) or ""

    # ---- multi-mode entry points (PRE_BRIEF + POST_SESSION) ----------------

    def brief(self, *, driver_id: str, today_iso: str, weather_phase: str,
              surface_state: str, markers_selected: list[str],
              weakest_recent_corner: Optional[str] = None,
              biggest_recent_improvement: Optional[dict] = None,
              danger_zones_today: Optional[list[str]] = None,
              goal: str = "personal best lap",
              driver_level: Optional[str] = None) -> tuple[str, list[str]]:
        """PRE_BRIEF mode. Returns (narrative_md, focus_list)."""
        level = driver_level or self.driver_level
        track = "Sonoma Raceway"
        sys_p = build_system_prompt(level, track, mode=CoachMode.PRE_BRIEF)
        usr_p = build_pre_brief_user_prompt(
            driver_id=driver_id, today_iso=today_iso,
            weather_phase=weather_phase, surface_state=surface_state,
            markers_selected=markers_selected,
            weakest_recent_corner=weakest_recent_corner,
            biggest_recent_improvement=biggest_recent_improvement,
            danger_zones_today=danger_zones_today or [],
            goal=goal,
        )
        if self._llm is None:
            # No LLM — fall back to a templated brief
            return _templated_pre_brief(
                driver_id=driver_id, weather_phase=weather_phase,
                surface_state=surface_state, markers_selected=markers_selected,
                weakest_recent_corner=weakest_recent_corner,
                danger_zones_today=danger_zones_today or [],
            )
        try:
            text = self._generate(sys_p, usr_p)
            return _split_brief_narrative_and_focus(text)
        except Exception:
            return _templated_pre_brief(
                driver_id=driver_id, weather_phase=weather_phase,
                surface_state=surface_state, markers_selected=markers_selected,
                weakest_recent_corner=weakest_recent_corner,
                danger_zones_today=danger_zones_today or [],
            )

    def debrief(self, bundle: dict,
                *, driver_level: Optional[str] = None) -> tuple[str, list[str]]:
        """POST_SESSION mode. Returns (narrative_md, next_focus_list)."""
        level = driver_level or self.driver_level
        track = bundle.get("track", "Sonoma Raceway")
        sys_p = build_system_prompt(level, track, mode=CoachMode.POST_SESSION)
        usr_p = build_post_session_user_prompt(bundle)
        if self._llm is None:
            return "", []   # caller falls through to templated narrative
        try:
            text = self._generate(sys_p, usr_p)
            return _split_debrief_narrative_and_focus(text)
        except Exception:
            return "", []


# ─── Templated fallbacks (used when no LLM is loaded) ────────────────────────


def _templated_pre_brief(*, driver_id: str, weather_phase: str,
                          surface_state: str, markers_selected: list[str],
                          weakest_recent_corner: Optional[str],
                          danger_zones_today: list[str]) -> tuple[str, list[str]]:
    parts = [
        f"# Pre-session brief — {driver_id} at Sonoma",
        f"_{weather_phase} — {surface_state}._",
    ]
    if markers_selected:
        parts.append(f"**Today's focus:** {', '.join(markers_selected)}.")
    if weakest_recent_corner:
        parts.append(f"Your weakest recent corner has been **{weakest_recent_corner}** — "
                     f"give it special attention.")
    if danger_zones_today:
        parts.append("**Danger zones today:**")
        for d in danger_zones_today:
            parts.append(f"- {d}")
    parts.append("Have a great session.")
    focus = list(markers_selected[:3]) if markers_selected else []
    return "\n\n".join(parts), focus


def _split_brief_narrative_and_focus(text: str) -> tuple[str, list[str]]:
    """Parse the <FOCUS>[...] tail off a PRE_BRIEF response."""
    if "<FOCUS>" in text:
        head, _, tail = text.partition("<FOCUS>")
        try:
            focus = json.loads(tail.strip())
            if isinstance(focus, list):
                return head.strip(), [str(x) for x in focus[:3]]
        except Exception:
            pass
    return text.strip(), []


def _split_debrief_narrative_and_focus(text: str) -> tuple[str, list[str]]:
    """Parse the <NEXT_FOCUS>[...] tail off a POST_SESSION response."""
    if "<NEXT_FOCUS>" in text:
        head, _, tail = text.partition("<NEXT_FOCUS>")
        try:
            focus = json.loads(tail.strip())
            if isinstance(focus, list):
                return head.strip(), [str(x) for x in focus[:3]]
        except Exception:
            pass
    return text.strip(), []


# Legacy alias — keeps any older callers working until full rename lands
TfliteCoach = LitertCoach


# ─── Helpers for callers ──────────────────────────────────────────────────────


def build_context(
    *,
    driver_level: str,
    track,                       # TrackMap
    frame,                       # TelemetryFrame
    next_corner,                 # CornerDef | None
    meters_to_entry: float,
    in_corner_obj,               # CornerDef | None
    past_apex: bool,
) -> CoachContext:
    """Pack the runtime state into a CoachContext + matched Bentley concept.

    Single helper used by both RuleCoach and LlamaCppCoach so the gating logic
    is identical regardless of which engine is active.
    """
    # Marker lookup — only meaningful when there's an upcoming corner
    next_brake_marker = ""
    next_apex_marker = ""
    next_tip = ""
    next_nick = ""
    if next_corner is not None:
        next_tip = getattr(next_corner, "coaching_tip", "") or ""
        nicks = getattr(next_corner, "nicknames", []) or []
        next_nick = nicks[0] if nicks else ""
        # Pick the closest brake_ref marker on this corner
        markers = getattr(next_corner, "markers", []) or []
        if markers:
            brake_marks = [m for m in markers if m.kind == "brake_ref"]
            apex_marks = [m for m in markers if m.kind == "apex_ref"]
            if brake_marks:
                next_brake_marker = brake_marks[0].label
            if apex_marks:
                next_apex_marker = apex_marks[0].label

    ctx = CoachContext(
        driver_level=driver_level,
        track_name=track.name,
        next_corner_name=next_corner.name if next_corner else "",
        next_corner_direction=(next_corner.direction if next_corner else "")[:1].upper(),
        next_corner_severity=int(next_corner.severity) if next_corner else 0,
        meters_to_entry=float(meters_to_entry) if next_corner else 999.0,
        next_brake_zone_m=float(next_corner.brake_distance) if next_corner else 0.0,
        next_brake_peak_bar=float(next_corner.brake_pressure) if next_corner else 0.0,
        next_apex_speed_kmh=float(next_corner.apex_speed) * 3.6 if next_corner else 0.0,
        next_elevation_change_m=float(next_corner.elevation_change) if next_corner else 0.0,
        next_brake_marker_label=next_brake_marker,
        next_apex_marker_label=next_apex_marker,
        next_corner_tip=next_tip,
        next_corner_nickname=next_nick,
        speed_kmh=frame.speed * 3.6,
        brake_pct=min(frame.brake_pressure, 100.0),
        throttle_pct=float(frame.throttle),
        g_lat=float(frame.g_lat),
        g_long=float(frame.g_long),
        in_corner=in_corner_obj is not None,
        past_apex=past_apex,
    )
    concept, tip = match_bentley_concept(ctx)
    ctx.bentley_concept = concept
    ctx.bentley_tip = tip
    return ctx


def make_coach(
    kind: str = "auto",
    *,
    driver_level: str = "intermediate",
    litert_model_path: str = "",
    # Back-compat alias; older callers may pass `tflite_model_path=`
    tflite_model_path: str = "",
) -> CoachEngine:
    """Factory.

    kind="auto"     : try litert; fall back to rule if the model doesn't load.
    kind="litert"   : force on-device LiteRT-LM via MediaPipe Genai —
                      LitertCoach internally falls back to RuleCoach output
                      if mediapipe isn't installed or the .task file is
                      missing, so calling code can always rely on getting
                      *something* back.
    kind="rule"     : force the zero-dep templated coach.
    """
    model_path = litert_model_path or tflite_model_path
    if kind == "rule":
        return RuleCoach(driver_level=driver_level)
    # "tflite" kept as a deprecated alias for one cycle
    if kind in ("litert", "tflite"):
        return LitertCoach(model_path=model_path, driver_level=driver_level)
    if kind == "auto":
        try:
            engine = LitertCoach(model_path=model_path, driver_level=driver_level)
            if engine._llm is not None:
                return engine
        except Exception:
            pass
        return RuleCoach(driver_level=driver_level)
    raise ValueError(f"unknown coach kind: {kind!r}")


# ─── Arbiter (lightweight: cooldown + corner suppression) ─────────────────────


class CoachArbiter:
    """Per Pitwall ADR-002: P3 immediate; P2 only on straights; P1 queued.
    Cooldown 3 s between non-safety messages. Stale 5 s expiry.
    """

    def __init__(self, cooldown_s: float = 3.0, stale_s: float = 5.0):
        self.cooldown_s = cooldown_s
        self.stale_s = stale_s
        self._last_emit_t = 0.0
        self._queued: Optional[tuple[float, CoachingMessage]] = None

    def submit(
        self,
        msg: Optional[CoachingMessage],
        *,
        now: float,
        on_straight: bool,
    ) -> Optional[CoachingMessage]:
        # Drop stale queued message
        if self._queued and now - self._queued[0] > self.stale_s:
            self._queued = None

        if msg is None:
            # Maybe deliver a queued one if we're now on a straight
            if self._queued and on_straight \
                    and now - self._last_emit_t >= self.cooldown_s:
                _, m = self._queued
                self._queued = None
                self._last_emit_t = now
                return m
            return None

        # P3 always immediate
        if msg.priority >= 3:
            self._last_emit_t = now
            return msg

        # P2 needs a straight; otherwise hold
        if msg.priority == 2 and not on_straight:
            self._queued = (now, msg)
            return None

        # Cooldown
        if now - self._last_emit_t < self.cooldown_s:
            self._queued = (now, msg)
            return None

        self._last_emit_t = now
        return msg
