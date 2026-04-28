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

import json
import os
import time
from dataclasses import dataclass, field
from typing import Optional


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

        if self.driver_level == "beginner":
            if 0 < m_in < 250 and c.next_corner_name:
                if c.bentley_concept == "downhill_brake":
                    return f"{c.next_corner_name} ahead, brake early, it goes down"
                if c.bentley_concept == "eob":
                    return f"{c.next_corner_name} coming up, look into the corner"
                return f"{c.next_corner_name} in {m_in} meters, brake smoothly"
            if c.bentley_concept == "hustle":
                return "full throttle here"
            if c.bentley_concept == "entry_release":
                return "keep a little brake on"
            return ""

        if self.driver_level == "pro":
            if 0 < m_in < 250 and c.next_corner_name:
                return f"{c.next_corner_name} {m_in}m, BP {bz}m, apex {apex}"
            if c.bentley_concept == "hustle":
                return "hustle"
            return ""

        # intermediate (default)
        if 0 < m_in < 250 and c.next_corner_name:
            base = f"{m_in}, {d} {sev}".strip(", ")
            if bz > 0:
                base += f", brake {bz}"
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


# ─── llama.cpp / OpenAI-compatible HTTP coach ────────────────────────────────


class LlamaCppCoach(CoachEngine):
    """Talks to an OpenAI-compatible chat completions endpoint.

    Default base URL is the llama.cpp llama-server default
    (`http://127.0.0.1:8080`). Same client works against Ollama on 11434,
    OpenAI api.openai.com, Together, Groq, etc. — anything that speaks
    OpenAI's /v1/chat/completions schema.

    On any error (server not up, timeout, malformed response) the engine
    returns None so the caller can fall back to RuleCoach.
    """

    name = "llamacpp"

    def __init__(
        self,
        base_url: str = "http://127.0.0.1:8080",
        model: str = "local",
        driver_level: str = "intermediate",
        timeout_s: float = 1.0,
    ):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.driver_level = driver_level
        self.timeout_s = timeout_s
        self._fallback = RuleCoach(driver_level)
        # Lazy import so the module loads on phones without `requests`
        try:
            import requests  # noqa: F401
            self._has_requests = True
        except ImportError:
            self._has_requests = False

    def health(self) -> bool:
        if not self._has_requests:
            return False
        import requests
        try:
            r = requests.get(self.base_url + "/v1/models", timeout=self.timeout_s)
            return r.status_code < 500
        except Exception:
            return False

    def propose(self, ctx: CoachContext) -> Optional[CoachingMessage]:
        # Same gating as RuleCoach: don't even prompt the LLM if there's
        # nothing within range and no concept worth voicing.
        if not ctx.next_corner_name and ctx.bentley_concept in ("look_ahead", ""):
            return None
        if not (0 < ctx.meters_to_entry < 250 or ctx.bentley_concept in (
            "hustle", "entry_release", "exit_speed", "downhill_brake",
        )):
            return None

        if not self._has_requests:
            return self._fallback.propose(ctx)

        import requests

        prompt = self._build_prompt(ctx)
        try:
            r = requests.post(
                self.base_url + "/v1/chat/completions",
                json={
                    "model": self.model,
                    "messages": [
                        {"role": "system", "content": _SYSTEM_PROMPT},
                        {"role": "user", "content": prompt},
                    ],
                    "max_tokens": 32,
                    "temperature": 0.4,
                    "stream": False,
                },
                timeout=self.timeout_s,
            )
            if r.status_code != 200:
                return self._fallback.propose(ctx)
            data = r.json()
            text = data["choices"][0]["message"]["content"].strip().strip('"').strip("'")
            if not text:
                return None
            # Trim multi-line responses to the first line
            text = text.splitlines()[0].strip()
            if not text:
                return None
            prio = 2 if ctx.bentley_concept == "entry_release" else 1
            return CoachingMessage(
                text=text,
                priority=prio,
                reason=f"llamacpp:{ctx.bentley_concept}",
            )
        except Exception:
            return self._fallback.propose(ctx)

    def _build_prompt(self, ctx: CoachContext) -> str:
        return _USER_PROMPT_TEMPLATE.format(
            level=self.driver_level,
            track=ctx.track_name,
            corner=ctx.next_corner_name or "none",
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
        )


_SYSTEM_PROMPT = (
    "You are a rally co-driver. Reply with ONE short line in pace-note "
    "shorthand, no more than 14 words, no emoji, no quotes. Reply with an "
    "EMPTY string when nothing useful needs to be said."
)


_USER_PROMPT_TEMPLATE = (
    "DRIVER: {level} on {track}.\n"
    "UPCOMING: {corner} {direction} sev{severity} in {m_in} m, "
    "brake_zone {bz} m peaking {bar} bar, apex {apex} km/h, elev {elev} m.\n"
    "NOW: {speed} km/h, brake {brake}%, throttle {thr}%, gLat {glat:+.2f}.\n"
    "PEDAGOGY: {concept} — {tip}\n"
    "Speak the pace note now."
)


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
    base_url: str = "http://127.0.0.1:8080",
    model: str = "local",
    driver_level: str = "intermediate",
) -> CoachEngine:
    """Factory.

    kind="auto"     : try llama.cpp; if /v1/models doesn't respond, RuleCoach.
    kind="llamacpp" : force llama.cpp (will still fall back per-call on errors).
    kind="rule"     : force the templated coach.
    """
    if kind == "rule":
        return RuleCoach(driver_level=driver_level)
    if kind in ("llamacpp", "ollama", "openai"):
        return LlamaCppCoach(base_url=base_url, model=model, driver_level=driver_level)
    if kind == "auto":
        candidate = LlamaCppCoach(base_url=base_url, model=model, driver_level=driver_level)
        if candidate.health():
            return candidate
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
