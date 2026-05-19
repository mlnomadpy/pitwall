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
  - LitertCoach     : on-device Gemma 4 E2B inference via LiteRT-LM
                      (MediaPipe Genai). Runs in-process on the Pixel 10
                      Tensor G5 NPU. Falls back to RuleCoach when the
                      model is unreachable or inference times out.

The arbiter (P3 safety / P2 technique / P1 strategy with cooldown + corner
suppression) lives at the call site in pitwall_app.SessionManager so this
file stays a pure "given context, produce zero or one CoachingMessage".
"""

from __future__ import annotations

import enum
import json
import os
import re
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Optional


# ─── ADR-018: LLM friction sink ───────────────────────────────────────────────
#
# `_friction_logger` is a single optional callback the bridge installs at boot
# via `set_friction_logger`. Every LitertCoach call funnels through `_generate`
# (or its `_log_friction` helper for the no-LLM templated fallback path), so
# this hook captures latency, truncation, fallbacks, and errors with one site
# of instrumentation. Keeping it at module scope means tests, replay harnesses,
# and the bridge can all observe friction without owning a coach instance.

FrictionLogger = Callable[[dict], None]
_friction_logger: Optional[FrictionLogger] = None


def set_friction_logger(fn: Optional[FrictionLogger]) -> None:
    """Install (or clear) the friction-record callback. Must be cheap and
    non-blocking — `_generate` calls it on the inference hot path."""
    global _friction_logger
    _friction_logger = fn


def _emit_friction(record: dict) -> None:
    """Best-effort fan-out to the registered logger. Swallows everything so a
    misbehaving sink never breaks an inference call."""
    fn = _friction_logger
    if fn is None:
        return
    try:
        fn(record)
    except Exception:
        pass


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


# ─── ADR-015 Phase 4: capability-aware coach rule registry ──────────────────


@dataclass(frozen=True)
class CoachRule:
    """A coach rule's capability declaration.

    The decorated callable is not yet wired into RuleCoach — Phase 4 ships
    the registry + capability filter so the frontend's capabilities endpoint
    can advertise *which rules will fire on this session*. Phase 5 will
    consume the same registry to actually dispatch coaching at runtime.
    """
    id: str
    description: str
    requires: tuple        # signal names this rule reads
    min_rates: tuple       # ((signal_name, hz), ...)


COACH_RULES: list[CoachRule] = []


def coach_rule(*, id: str, description: str = "",
               requires=None, min_rates=None):
    """Decorator that registers a coach rule with its capability requirements.

    Example:
        @coach_rule(id="oil_temp_warning_t11", requires=["oil_temp_c"],
                    min_rates={"oil_temp_c": 1.0})
        def oil_warning(ctx, signals): ...
    """
    req_tuple = tuple(requires or [])
    rates_tuple = tuple(sorted((min_rates or {}).items()))

    def decorator(fn):
        # De-dupe by id (in-place) — a re-import of this module shouldn't
        # double-register. Mutate rather than rebind so other modules that
        # imported the list see the update.
        COACH_RULES[:] = [r for r in COACH_RULES if r.id != id]
        COACH_RULES.append(CoachRule(
            id=id, description=description or fn.__doc__ or "",
            requires=req_tuple, min_rates=rates_tuple,
        ))
        return fn
    return decorator


def evaluate_coach_gating(caps_by_name: dict) -> tuple[list, list]:
    """Split COACH_RULES into (available, disabled) given session capabilities.

    `caps_by_name` maps signal name → {"mean_hz": float, "useful": bool}.
    A rule is *available* iff every name in `requires` is present in caps
    AND every (name, min_hz) in `min_rates` is satisfied by caps[name].mean_hz.
    `disabled` items carry a human-readable reason.
    """
    available: list = []
    disabled: list = []
    for rule in COACH_RULES:
        missing = [n for n in rule.requires if n not in caps_by_name]
        if missing:
            disabled.append({
                "coach_id": rule.id,
                "reason":   f"missing signal: {missing[0]}",
            })
            continue
        rate_fail = None
        for nm, min_hz in rule.min_rates:
            actual = float(caps_by_name.get(nm, {}).get("mean_hz", 0.0))
            if actual < float(min_hz):
                rate_fail = (nm, actual, min_hz)
                break
        if rate_fail:
            nm, actual, min_hz = rate_fail
            disabled.append({
                "coach_id": rule.id,
                "reason":   f"{nm} rate ({actual:.1f} Hz) below min_useful_hz ({min_hz:.1f})",
            })
            continue
        available.append(rule.id)
    return available, disabled


# ─── Built-in coach rules (capability declarations only — Phase 5 wires them) ─


@coach_rule(
    id="base_pace_note",
    description="Default pace-note coaching from sonic_model cues.",
    requires=["speed_ms", "distance_m"],
)
def _rule_base_pace_note(ctx, signals):  # pragma: no cover
    pass


@coach_rule(
    id="trail_brake_score",
    description="Score trail-brake quality at corner entry.",
    requires=["brake_bar", "throttle_pct", "g_lat", "speed_ms"],
)
def _rule_trail_brake_score(ctx, signals):  # pragma: no cover
    pass


@coach_rule(
    id="oil_temp_warning_t11",
    description="Warn driver if oil temp > 105°C approaching T11.",
    requires=["oil_temp_c", "distance_m"],
    min_rates={"oil_temp_c": 1.0},
)
def _rule_oil_temp_warning(ctx, signals):  # pragma: no cover
    pass


@coach_rule(
    id="clutch_balance",
    description="Score clutch position smoothness during shifts.",
    requires=["clutch_pos_pct", "rpm"],
    min_rates={"clutch_pos_pct": 5.0},
)
def _rule_clutch_balance(ctx, signals):  # pragma: no cover
    pass


@coach_rule(
    id="tpms_drift",
    description="Alert on tire pressure drift across stints.",
    requires=["tpms_fl_kpa", "tpms_fr_kpa", "tpms_rl_kpa", "tpms_rr_kpa"],
    min_rates={"tpms_fl_kpa": 5.0},
)
def _rule_tpms_drift(ctx, signals):  # pragma: no cover
    pass


@coach_rule(
    id="slip_angle_oscillation",
    description=(
        "ADR-018 pedagogy: detect rapid swings between friction bands "
        "(approaching_peak ↔ over_driving) within a 3 s window — the "
        "intermediate-driver pattern of overshooting the limit and "
        "reining it back. Cue: 'Smooth is fast — dial it back to 90 percent.' "
        "Reads combo_g + g_lat at high rate so the band assignment is "
        "stable enough to count crossings."
    ),
    requires=["combo_g", "g_lat"],
    min_rates={"combo_g": 20.0, "g_lat": 20.0},
)
def _rule_slip_angle_oscillation(ctx, signals):  # pragma: no cover
    pass


@coach_rule(
    id="mid_corner_coasting",
    description=(
        "ADR-018 pedagogy: penalise the intermediate-driver pattern of "
        "coasting between brake release and throttle-on. Fires when "
        "brake_bar < 0.5 AND throttle_pct < 5 for more than ~0.4 s while "
        "still under lateral load (corner). The cue ('Get back to throttle' "
        "or 'Stop coasting — pick a pedal') closes the dead-pedal gap that "
        "dominates time loss for HPDE drivers."
    ),
    requires=["brake_bar", "throttle_pct", "g_lat", "speed_ms"],
    min_rates={"brake_bar": 10.0, "throttle_pct": 10.0},
)
def _rule_mid_corner_coasting(ctx, signals):  # pragma: no cover
    pass


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
    "PEDAGOGY (intermediate / Time-Trial drivers — ADR-018):\n"
    "- The driver already knows where to brake. Do NOT call out static "
    "brake markers when the driver is hitting them within 5 m. Coach "
    "the SHAPE of the brake release instead — 'roll the brake to the "
    "apex', 'taper, don't drop', 'longer trail to load the front'.\n"
    "- Treat trail-braking — modulating brake pressure as steering "
    "increases — as the central skill, not a finishing flourish. Bring "
    "it up before apex speed and exit throttle.\n"
    "- Penalise dead-pedal time (brake-off + throttle-off mid-corner). "
    "If the data shows a coast, say so — 'no nothing time, pick a pedal'.\n"
    "- Manage ego on understeery laps: if slip-angle is oscillating, "
    "calm the driver — 'smooth is fast, dial it back to 90 percent.'\n\n"
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
    "sign letters'). Use canonical T-Rod phrasings where they fit.\n\n"
    "PEDAGOGY FOCUS (ADR-018, intermediate / HPDE / Time-Trial driver):\n"
    "- Frame the brief around TRANSITIONS, not corner identification: brake "
    "release shape, throttle pickup timing, dead-pedal avoidance.\n"
    "- 'Roll the brake to the apex' is the headline cue — repeat it.\n"
    "- If the driver has been over-driving lately, lead with calm: "
    "'smooth is fast, dial it back to 90 percent first lap'.\n"
    "- Do NOT prescribe brake markers — say 'taper your release', not 'brake at the 100 board'.\n\n"
    "End with the literal token <FOCUS> followed by a JSON list of exactly "
    "3 short actionable focus items. Total ~150 words."
)


_POST_SESSION_SYSTEM_PROMPT = (
    "You are a professional race engineer giving a POST-SESSION DEBRIEF. "
    "The driver just finished a session at Sonoma Raceway in a BMW M3, "
    "intermediate level. The user message contains structured session data: "
    "scorecard with per-corner A-F grades, time-loss decomposition per "
    "corner, top highlight moments, stat cards, slip-angle band + slip "
    "oscillations, EoB summary, consistency, and incidents. Use this data — "
    "DO NOT invent events that aren't in the data. Speak in T-Rod's "
    "canonical voice where it fits ('Distance is king', 'Just go 100', "
    "'Wait, you're not at the apex yet', 'Roll the brake to the apex'). "
    "Reference named Sonoma landmarks.\n\n"
    "STRUCTURE (ADR-018 — lead with a positive maneuver before any "
    "critique; intermediate drivers buy in faster when they hear what "
    "worked first):\n"
    "1) HEADLINE — one-paragraph assessment that OPENS with a validated "
    "positive moment from the data: best sector, the highest-scoring "
    "corner, or a specific highlight tagged 'good_*'. Name it ('your "
    "T10 entry on lap 4 was an A — that's the gold lap signal'). Even on "
    "a rough session, find one thing that worked.\n"
    "2) HIGHLIGHTS — one paragraph per highlight (≤3 total, lap N, what "
    "happened, what to do next time). Mix wins and lessons, but lead "
    "with at least one win.\n"
    "3) ONE BIG LEVER — paragraph identifying the single biggest lap-time "
    "opportunity (typically T10 or T11). Frame as 'next session' work, "
    "not as failure. If the slip-oscillation count is high (>2), this is "
    "where to say 'smooth is fast — dial it back to 90 percent first stint'.\n"
    "4) FOCUS — three items for next session, formatted as a JSON list "
    "after the literal token <NEXT_FOCUS>. Each focus item must be a "
    "transition-level cue (brake release, throttle pickup, dead-pedal "
    "elimination), NOT a brake-marker prescription.\n\n"
    "Total ~300 words."
)


_SYSTEM_PROMPTS_BY_MODE = {
    CoachMode.DURING_DRIVE: _BASE_SYSTEM_PROMPT,
    CoachMode.PRE_BRIEF:    _PRE_BRIEF_SYSTEM_PROMPT,
    CoachMode.POST_SESSION: _POST_SESSION_SYSTEM_PROMPT,
}


VALID_EMOTIONS = frozenset({
    "neutral", "thinking", "analyzing", "encouraging", "proud",
    "excited", "serious", "concerned", "disappointed", "intense",
    "relaxed", "tired",
})

_EMOTION_TAG_INSTRUCTION = (
    "At the START of your reply, emit exactly one tag in the form "
    "[EMOTION: <name>] where <name> is one of: "
    + ", ".join(sorted(VALID_EMOTIONS))
    + ". Then a single newline, then your normal coaching response. "
    "Do not emit the tag anywhere else in the reply."
)


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
    # Emotion-tag contract — LLM declares its mood for the avatar.
    parts.append(_EMOTION_TAG_INSTRUCTION)
    return "\n\n".join(parts)


_EMOTION_TAG_RE = re.compile(r"^\s*\[EMOTION:\s*(\w+)\s*\]\s*\n?", re.IGNORECASE)


def _extract_emotion(text: str) -> tuple[str, str]:
    """Strip a leading `[EMOTION: ...]` tag and return (cleaned_text, emotion).

    Falls back to `neutral` if no tag is present, or the tag's value isn't
    in VALID_EMOTIONS. The Gemma 4 E2B model is small enough that occasional
    tag drift is expected; failing safely to neutral keeps the avatar
    rendering even when the LLM forgets.
    """
    if not text:
        return "", "neutral"
    m = _EMOTION_TAG_RE.match(text)
    if not m:
        return text, "neutral"
    emotion = m.group(1).lower()
    cleaned = text[m.end():]
    if emotion not in VALID_EMOTIONS:
        emotion = "neutral"
    return cleaned, emotion


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
    osc = bundle.get("slip_oscillations") or []

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

    # ADR-018: surface the highlight reel — best corner score, best
    # highlight, and best lap delta — so the LLM has a concrete positive
    # to open with. Pre-extracted here to avoid the model fishing for it.
    corners = sc.get("corners") or []
    best_corner = max(
        corners, key=lambda c: c.get("score_pct", 0.0), default=None,
    ) if corners else None
    positive_highlight = next(
        (h for h in hs if str(h.get("kind", "")).startswith("good")
         or h.get("severity") == "positive"
         or "good" in str(h.get("kind", ""))),
        None,
    )
    highlight_reel_lines: list[str] = ["HIGHLIGHT REEL (lead the debrief with one of these):"]
    if best_corner:
        highlight_reel_lines.append(
            f"  best corner: {best_corner.get('corner')} "
            f"grade {best_corner.get('grade')} "
            f"({best_corner.get('score_pct', 0):.1%})"
        )
    if positive_highlight:
        highlight_reel_lines.append(
            f"  positive highlight: {positive_highlight.get('title', '?')} "
            f"— {positive_highlight.get('narrative_seed', '')}"
        )
    if not best_corner and not positive_highlight:
        # Even on a rough session we want SOMETHING positive — lap-time
        # progression often is. Use best vs prior-best lap as the floor.
        highlight_reel_lines.append(
            "  no clear highlight — open with raw effort or pace progression"
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
        *highlight_reel_lines,
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
        # ADR-018: lead with oscillation count when it's high — that's the
        # 'over-driving / ego' signal the debrief must address head-on.
        f"SLIP OSCILLATIONS: {len(osc)} ego-swing windows"
        + (f" (peak {max(o['crossings'] for o in osc)} band crossings)" if osc else ""),
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
    """Backend-owned on-device LLM inference via Google's `litert-lm` package.
    Targets the Gemma 4 LiteRT-LM `.litertlm` artifact published at
    `litert-community/gemma-4-E2B-it-litert-lm` on Hugging Face.

    Why litert-lm (not mediapipe.tasks.python.genai.inference):
      - The `.litertlm` bundle format includes the model, tokenizer, KV-cache
        config, chat template, and platform-specific accelerators all in one
        file. Loaded via `litert_lm.Engine`.
      - The PyPI mediapipe wheel for desktop Python (macOS/Linux) does NOT
        ship the `genai.inference` submodule — that's Android/iOS only. The
        `litert-lm` package is Google's cross-platform replacement.
      - Same model file, same C++ runtime underneath; different Python wrapper.
      - Backends: CPU works on Apple Silicon and Linux; GPU on supported
        devices. We default to CPU and let the model metadata pick the
        accelerator backend.

    Install on a dev machine:
        pip install litert-lm
        litert-lm import --from-huggingface-repo \\
            litert-community/gemma-4-E2B-it-litert-lm \\
            gemma-4-E2B-it.litertlm gemma-4-e2b
        # Model now at ~/.litert-lm/models/gemma-4-e2b/model.litertlm

    On Termux (Pixel): same `litert-lm` package; same import command.

    Coaching scope (per the three-tier architecture):
      - `brief()` — pre-session paddock narrative. LLM-driven. 2-4 s OK.
      - `debrief()` — post-session paddock narrative. LLM-driven. 8-15 s OK.
      - `propose()` — DEPRECATED for LLM use. Returns None to defer to the
        canonical-phrase path (RuleCoach + pre-rendered audio). LLMs are
        too slow (>1 s) for sub-corner cues.

    If `litert-lm` isn't installed, the model file is missing, or any runtime
    error occurs, the LLM-driven methods (brief/debrief) fall back to
    templated narratives. Backend keeps a single source of truth for system
    instructions via build_system_prompt / build_user_prompt per ADR-013.
    """

    name = "litert"

    # Search order for the .litertlm model file. Adjusted to match the
    # `litert-lm import` CLI's storage layout + repo-local + Termux paths.
    DEFAULT_MODEL_PATHS = [
        # Default location used by `litert-lm import <ref>`
        "~/.litert-lm/models/gemma-4-e2b/model.litertlm",
        # Termux on Pixel — shared storage readable from any process
        "~/storage/shared/Pitwall/models/gemma-4-E2B-it.litertlm",
        # Repo-local fallback for dev machines
        "models/gemma-4-E2B-it.litertlm",
        # Legacy .task path — kept so a stale install doesn't 100% silently fail
        "~/storage/shared/Pitwall/models/gemma-4-E2B-it.task",
        "models/gemma-4-E2B-it.task",
    ]

    # Default LocalLLM endpoint — Apache-2.0 Android APK at
    # https://www.tahabouhsine.com/localllm/ exposing OpenAI-compatible
    # /v1/chat/completions. ADR-022 makes this the default transport for
    # both warm (brief/debrief) and paddock (ADK) LLM calls. Override with
    # the PITWALL_LITERT_URL env var; set to empty string to fall back to
    # in-process litert_lm.Engine on the same machine.
    DEFAULT_HTTP_URL = "http://localhost:8099/v1"
    DEFAULT_HTTP_MODEL = "gemma3n-e2b"

    def __init__(
        self,
        model_path: str = "",
        *,
        driver_level: str = "intermediate",
        max_tokens: int = 256,
        temperature: float = 0.4,
        backend: str = "cpu",
    ):
        self.driver_level = driver_level
        self.max_tokens = max_tokens
        self.temperature = temperature
        self.backend = backend
        self._fallback = RuleCoach(driver_level)
        self._llm = None
        self._engine = None
        self._engine_ctx = None       # context-manager handle (for clean close)
        self._init_error: Optional[str] = None

        # HTTP transport (LocalLLM) state. Truthy `_http_url` means HTTP
        # mode is selected — `_generate` will POST to chat.completions
        # instead of touching the in-process engine.
        self._http_url: str = ""
        self._http_model: str = ""
        self._http_api_key: str = ""
        self._http_timeout_s: float = float(
            os.getenv("PITWALL_LITERT_HTTP_TIMEOUT_S", "30")
        )

        # ADR-022: prefer HTTP-to-LocalLLM by default. Setting
        # PITWALL_LITERT_URL to an empty string opts out and falls back to
        # in-process litert-lm. Anything else (including the default LocalLLM
        # URL) selects HTTP and skips loading the engine in this process.
        http_url = os.getenv("PITWALL_LITERT_URL", self.DEFAULT_HTTP_URL).strip()
        if http_url:
            self._http_url = http_url.rstrip("/")
            self._http_model = os.getenv(
                "PITWALL_LITERT_MODEL", self.DEFAULT_HTTP_MODEL
            )
            self._http_api_key = os.getenv(
                "PITWALL_LITERT_API_KEY", "lit-serve-not-required"
            )
            # Truthy sentinel — brief()/debrief() gate on `self._llm is not None`.
            self._llm = "http"
            return

        # In-process engine fallback (opt-in via empty PITWALL_LITERT_URL).
        # All heavy imports are lazy + caught — LitertCoach must construct
        # cleanly on machines without litert-lm so make_coach("auto") can
        # probe + fall back without crashing the bridge.
        try:
            self._init_runtime(model_path)
        except Exception as e:
            self._init_error = f"{type(e).__name__}: {e}"

    # ---- runtime init -------------------------------------------------------

    def _init_runtime(self, model_path: str):
        try:
            import litert_lm  # type: ignore
        except ImportError as e:
            raise RuntimeError(
                f"litert-lm not installed ({e}). "
                f"Run: pip install litert-lm"
            )

        path = self._resolve_model_path(model_path)
        if path is None:
            raise FileNotFoundError(
                f"no Gemma .litertlm file found in {self.DEFAULT_MODEL_PATHS}"
            )

        backend_enum = (litert_lm.Backend.GPU
                        if self.backend.lower() == "gpu"
                        else litert_lm.Backend.CPU)

        # Engine is a context manager; entering it loads the model + native
        # libs. We keep the entered handle on `self` for the lifetime of the
        # coach and release it via close().
        engine_factory = litert_lm.Engine(
            model_path=str(path),
            backend=backend_enum,
            max_num_tokens=4096,
        )
        self._engine_ctx = engine_factory
        self._engine = engine_factory.__enter__()
        # Sentinel used by callers + tests to confirm the model loaded.
        self._llm = self._engine

    def close(self):
        """Release the engine's native resources. Safe to call repeatedly."""
        if self._engine_ctx is not None:
            try:
                self._engine_ctx.__exit__(None, None, None)
            except Exception:
                pass
            self._engine_ctx = None
            self._engine = None
            self._llm = None

    def __del__(self):
        try:
            self.close()
        except Exception:
            pass

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
            "loaded":    self._llm is not None,
            "transport": "http" if self._http_url else (
                "engine" if self._engine is not None else "none"
            ),
            "http_url":  self._http_url,
            "http_model": self._http_model,
            "error":     self._init_error or "",
            "fallback":  self._fallback.name,
        }

    def propose(self, ctx: CoachContext) -> Optional[CoachingMessage]:
        """In-drive coaching path is intentionally NOT LLM-driven.

        Three-tier coach architecture (set 2026-04-29):
          - pre-brief / post-session debrief → LLM (this class, brief/debrief)
          - in-drive sub-corner cues          → canonical-phrase library +
                                                 pre-rendered audio (RuleCoach)

        LLM latency on Apple Silicon CPU ≈ 3.5 s for ~30 tokens; on Pixel CPU
        2-4 s. Both are useless for an apex window. Forwarding to RuleCoach
        keeps the in-drive contract honest while preserving all the gating
        logic in one place.
        """
        return self._fallback.propose(ctx)

    # ---- inference (used by brief() + debrief() only) -----------------------

    def _generate(self, system_prompt: str, user_prompt: str,
                  *, session_id: Optional[str] = None,
                  role: str = "", mode: str = "") -> str:
        """One-shot generation.

        Two transports, picked at construction:

        - **HTTP (default, ADR-022)** — POST to LocalLLM's OpenAI-compatible
          `/chat/completions`. Stateless one-shot; system + user message in
          the `messages` array. Response parsed from `choices[0].message.content`.
        - **In-process (opt-in)** — `litert_lm.Engine.create_conversation()`
          + `Conversation.send_message()`. The `.litertlm` bundle ships its
          own chat template; the runtime applies Gemma's chat template
          internally — no manual `<start_of_turn>` tokens needed.

        Every call emits a friction record (success or failure) so the
        bridge's `/diagnostics/llm_friction` endpoint can surface degradation
        before it bites in a session.
        """
        prompt_chars = len(system_prompt) + len(user_prompt)
        if self._llm is None:
            _emit_friction({
                "session_id": session_id, "role": role, "mode": mode,
                "backend": self.backend,
                "prompt_chars": prompt_chars, "completion_chars": 0,
                "latency_ms": 0.0, "truncated": False, "fell_back": True,
                "error": "engine_not_loaded", "emotion": "",
            })
            return ""
        t0 = time.monotonic()
        err = ""
        text = ""
        try:
            if self._http_url:
                text = self._generate_http(system_prompt, user_prompt)
            else:
                conv = self._engine.create_conversation(
                    messages=[{"role": "system", "content": system_prompt}],
                )
                response = conv.send_message(
                    {"role": "user", "content": user_prompt},
                )
                text = _extract_assistant_text(response)
        except Exception as e:
            err = f"{type(e).__name__}: {e}"
            text = ""
        latency_ms = (time.monotonic() - t0) * 1000.0
        # Truncation heuristic: ran the full token budget AND output ended
        # without sentence-final punctuation (no terminal . ! ? }] etc.).
        # Keeps us honest about partial completions until litert-lm exposes
        # an explicit finish_reason in its response shape.
        truncated = bool(
            text
            and len(text) >= self.max_tokens * 3   # ~3 chars/token rough lower bound
            and text.rstrip()[-1:] not in ".!?\"')]}",
        )
        _emit_friction({
            "session_id": session_id, "role": role, "mode": mode,
            "backend": self.backend,
            "prompt_chars": prompt_chars, "completion_chars": len(text),
            "latency_ms": latency_ms, "truncated": truncated,
            "fell_back": bool(err) or not text,
            "error": err, "emotion": "",
        })
        return text

    # ---- HTTP transport (LocalLLM / any OpenAI-compatible local server) ----

    def _generate_http(self, system_prompt: str, user_prompt: str) -> str:
        """POST to LocalLLM's OpenAI-compatible chat.completions endpoint.

        Synchronous; uses urllib.request to avoid an extra HTTP dep. Returns
        the assistant text or raises — the caller's try/except converts that
        into a friction record and a templated fallback.
        """
        url = self._http_url + "/chat/completions"
        payload = {
            "model": self._http_model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user",   "content": user_prompt},
            ],
            "temperature": self.temperature,
            "max_tokens":  self.max_tokens,
            "stream": False,
        }
        body = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=body,
            method="POST",
            headers={
                "Content-Type":  "application/json",
                "Accept":        "application/json",
                "Authorization": f"Bearer {self._http_api_key}",
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=self._http_timeout_s) as resp:
                raw = resp.read()
        except urllib.error.HTTPError as e:
            detail = e.read()[:512].decode("utf-8", errors="replace")
            raise RuntimeError(f"localllm HTTP {e.code}: {detail}") from e
        except urllib.error.URLError as e:
            raise RuntimeError(f"localllm unreachable at {url}: {e.reason}") from e
        data = json.loads(raw.decode("utf-8"))
        choices = data.get("choices") or []
        if not choices:
            raise RuntimeError(f"localllm: empty choices in response: {data!r}")
        msg = choices[0].get("message") or {}
        content = msg.get("content")
        if isinstance(content, str):
            return content
        # Some servers wrap content as a list of parts; concatenate text parts.
        if isinstance(content, list):
            parts = [
                p.get("text", "") for p in content
                if isinstance(p, dict) and p.get("type") in ("text", None)
            ]
            return "\n".join(s for s in parts if s).strip()
        return ""

    # ---- multi-mode entry points (PRE_BRIEF + POST_SESSION) ----------------

    def brief(self, *, driver_id: str, today_iso: str, weather_phase: str,
              surface_state: str, markers_selected: list[str],
              weakest_recent_corner: Optional[str] = None,
              biggest_recent_improvement: Optional[dict] = None,
              danger_zones_today: Optional[list[str]] = None,
              goal: str = "personal best lap",
              driver_level: Optional[str] = None,
              session_id: Optional[str] = None,
              ) -> tuple[str, list[str], str]:
        """PRE_BRIEF mode. Returns (narrative_md, focus_list, emotion).

        `emotion` is one of `coach_engine.VALID_EMOTIONS`; defaults to
        'neutral' when no LLM, when the response lacks the [EMOTION:]
        tag, or when the tag's value is unknown. The PWA's coach
        sprite reads it to pick the matching animation.
        """
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
            _emit_friction({
                "session_id": session_id, "role": "brief",
                "mode": CoachMode.PRE_BRIEF.value, "backend": self.backend,
                "prompt_chars": len(sys_p) + len(usr_p),
                "completion_chars": 0, "latency_ms": 0.0,
                "truncated": False, "fell_back": True,
                "error": self._init_error or "engine_not_loaded",
                "emotion": "neutral",
            })
            narr, focus = _templated_pre_brief(
                driver_id=driver_id, weather_phase=weather_phase,
                surface_state=surface_state, markers_selected=markers_selected,
                weakest_recent_corner=weakest_recent_corner,
                danger_zones_today=danger_zones_today or [],
            )
            return narr, focus, "neutral"
        try:
            raw = self._generate(
                sys_p, usr_p, session_id=session_id,
                role="brief", mode=CoachMode.PRE_BRIEF.value,
            )
            cleaned, emotion = _extract_emotion(raw)
            narr, focus = _split_brief_narrative_and_focus(cleaned)
            return narr, focus, emotion
        except Exception:
            narr, focus = _templated_pre_brief(
                driver_id=driver_id, weather_phase=weather_phase,
                surface_state=surface_state, markers_selected=markers_selected,
                weakest_recent_corner=weakest_recent_corner,
                danger_zones_today=danger_zones_today or [],
            )
            return narr, focus, "neutral"

    def debrief(self, bundle: dict,
                *, driver_level: Optional[str] = None
                ) -> tuple[str, list[str], str]:
        """POST_SESSION mode. Returns (narrative_md, next_focus_list, emotion)."""
        level = driver_level or self.driver_level
        track = bundle.get("track", "Sonoma Raceway")
        sys_p = build_system_prompt(level, track, mode=CoachMode.POST_SESSION)
        usr_p = build_post_session_user_prompt(bundle)
        sid = (bundle.get("scorecard") or {}).get("session_id") \
            or bundle.get("session_id")
        if self._llm is None:
            _emit_friction({
                "session_id": sid, "role": "debrief",
                "mode": CoachMode.POST_SESSION.value, "backend": self.backend,
                "prompt_chars": len(sys_p) + len(usr_p),
                "completion_chars": 0, "latency_ms": 0.0,
                "truncated": False, "fell_back": True,
                "error": self._init_error or "engine_not_loaded",
                "emotion": "neutral",
            })
            return "", [], "neutral"
        try:
            raw = self._generate(
                sys_p, usr_p, session_id=sid,
                role="debrief", mode=CoachMode.POST_SESSION.value,
            )
            cleaned, emotion = _extract_emotion(raw)
            narr, focus = _split_debrief_narrative_and_focus(cleaned)
            return narr, focus, emotion
        except Exception:
            return "", [], "neutral"


# ─── litert-lm helpers (module scope so brief/debrief stay class methods) ───


def _extract_assistant_text(response) -> str:
    """Extract the concatenated text body from a litert-lm send_message reply.

    Response shape: `{'role': 'assistant', 'content': [{'text': '...',
    'type': 'text'}, ...]}`. The `content` is a list of typed parts; we
    concatenate every part with type=='text'.
    """
    if isinstance(response, str):
        return response
    if not isinstance(response, dict):
        return ""
    content = response.get("content")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for part in content:
            if isinstance(part, dict) and part.get("type") == "text":
                t = part.get("text")
                if isinstance(t, str):
                    parts.append(t)
            elif isinstance(part, str):
                parts.append(part)
        return "\n".join(parts).strip()
    return ""


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

    Single helper used by both RuleCoach and LitertCoach so the gating logic
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

    kind="auto"     : prefer LitertCoach. Per ADR-022 LitertCoach now
                      defaults to HTTP transport against LocalLLM
                      (`http://localhost:8099/v1`) — overridable via
                      PITWALL_LITERT_URL. Set the env var to an empty
                      string to fall back to in-process litert-lm; if
                      neither path produces a loaded LLM, falls through
                      to RuleCoach.
    kind="litert"   : force LitertCoach (same HTTP-default behaviour as
                      "auto"). Internally falls back to templated output
                      if LocalLLM is unreachable AND no .litertlm is found.
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
