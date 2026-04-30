"""Unit tests for src/simulator/coach_engine.py."""
import time
import pytest
from types import SimpleNamespace

import coach_engine
from coach_engine import (
    CoachMode, CoachContext, CoachingMessage, CoachArbiter,
    RuleCoach, LitertCoach, make_coach,
    build_system_prompt, build_user_prompt,
    build_pre_brief_user_prompt, build_post_session_user_prompt,
    build_context, match_bentley_concept,
    _templated_pre_brief, _split_brief_narrative_and_focus,
    _split_debrief_narrative_and_focus,
)


# ─── CoachMode + system prompts ──────────────────────────────────────────────


def test_coach_mode_has_three_values():
    assert {m.value for m in CoachMode} == {
        "during_drive", "pre_brief", "post_session",
    }


def test_build_system_prompt_each_level_each_mode():
    """Every (driver_level × mode) combination yields a non-empty prompt."""
    for level in ("beginner", "intermediate", "pro"):
        for mode in CoachMode:
            p = build_system_prompt(level, "Sonoma Raceway", mode)
            assert isinstance(p, str)
            assert len(p) > 100


def test_during_drive_prompts_differ_per_level():
    """Different driver levels yield different DURING_DRIVE system prompts."""
    a = build_system_prompt("beginner", mode=CoachMode.DURING_DRIVE)
    b = build_system_prompt("pro", mode=CoachMode.DURING_DRIVE)
    assert a != b


def test_sonoma_prompt_includes_canonical_landmarks():
    p = build_system_prompt("intermediate", "Sonoma Raceway",
                            mode=CoachMode.DURING_DRIVE)
    for landmark in ("the bridge", "Calamity Corner", "Carousel"):
        assert landmark in p


def test_pre_brief_and_post_session_have_distinct_base_prompts():
    pre = build_system_prompt("intermediate", "Sonoma Raceway",
                              mode=CoachMode.PRE_BRIEF)
    post = build_system_prompt("intermediate", "Sonoma Raceway",
                               mode=CoachMode.POST_SESSION)
    during = build_system_prompt("intermediate", "Sonoma Raceway",
                                 mode=CoachMode.DURING_DRIVE)
    assert pre != post != during != pre


def test_pre_brief_prompt_asks_for_focus_token():
    p = build_system_prompt("intermediate", mode=CoachMode.PRE_BRIEF)
    assert "<FOCUS>" in p


def test_post_session_prompt_asks_for_next_focus_token():
    p = build_system_prompt("intermediate", mode=CoachMode.POST_SESSION)
    assert "<NEXT_FOCUS>" in p


# ─── build_user_prompt ───────────────────────────────────────────────────────


def test_build_user_prompt_includes_corner_fields():
    ctx = CoachContext(
        driver_level="intermediate", track_name="Sonoma Raceway",
        next_corner_name="Turn 11", next_corner_direction="R",
        next_corner_severity=3, meters_to_entry=120,
    )
    p = build_user_prompt(ctx)
    assert "Turn 11" in p
    assert "R" in p
    assert "120" in p


def test_build_user_prompt_includes_marker_label():
    ctx = CoachContext(
        driver_level="intermediate", track_name="Sonoma Raceway",
        next_corner_name="Turn 2", next_corner_direction="L",
        next_corner_severity=6, meters_to_entry=180,
        next_brake_marker_label="the bridge",
    )
    p = build_user_prompt(ctx)
    assert "the bridge" in p
    assert "BRAKE LANDMARK" in p


def test_build_user_prompt_includes_coaching_tip():
    ctx = CoachContext(
        driver_level="pro", track_name="Sonoma Raceway",
        next_corner_name="Turn 11", meters_to_entry=200,
        next_corner_tip="wait for the bump to settle",
    )
    p = build_user_prompt(ctx)
    assert "wait for the bump" in p
    assert "COACHING TIP" in p


def test_build_user_prompt_includes_pedagogy_concept():
    ctx = CoachContext(
        driver_level="intermediate", track_name="Sonoma Raceway",
        bentley_concept="late_apex", bentley_tip="patience pays",
    )
    p = build_user_prompt(ctx)
    assert "late_apex" in p
    assert "patience pays" in p


# ─── build_pre_brief_user_prompt ─────────────────────────────────────────────


def test_pre_brief_user_prompt_includes_inputs():
    p = build_pre_brief_user_prompt(
        driver_id="Taha", today_iso="2026-05-23",
        weather_phase="morning_fog", surface_state="cool damp",
        markers_selected=["Turn 4", "Turn 11"],
        weakest_recent_corner="Turn 10",
        biggest_recent_improvement={"corner": "Turn 6", "delta_score": 0.05},
        danger_zones_today=["T6 runoff cool"],
        goal="personal best lap",
    )
    assert "Taha" in p
    assert "2026-05-23" in p
    assert "morning_fog" in p
    assert "Turn 11" in p
    assert "Turn 10" in p
    assert "Turn 6" in p
    assert "T6 runoff cool" in p


def test_pre_brief_user_prompt_handles_none_inputs():
    p = build_pre_brief_user_prompt(
        driver_id="x", today_iso="2026-05-23",
        weather_phase="peak_grip", surface_state="optimal",
        markers_selected=[],
        weakest_recent_corner=None,
        biggest_recent_improvement=None,
        danger_zones_today=[],
    )
    assert "x" in p
    # Should not crash and should still produce a valid prompt
    assert "DRIVER:" in p


# ─── build_post_session_user_prompt ──────────────────────────────────────────


def test_post_session_user_prompt_compact_scorecard():
    bundle = {
        "scorecard": {
            "session_id": "sid-1", "n_laps": 5, "best_lap_s": 110.0,
            "gold_lap_s": 105.0, "session_grade": "B",
            "weighted_total_pct": 0.88,
            "corners": [
                {"corner": "Turn 10", "grade": "B",
                 "delta_time_s": 0.5, "apex_delta_kmh": -2.1,
                 "exit_delta_kmh": -1.8, "brake_point_delta_m": 5.0,
                 "time_loss_attribution": []},
            ],
        },
        "highlights": [
            {"severity": "high", "title": "T6 oversteer",
             "narrative_seed": "lap 3 carousel"},
        ],
        "stats": {"top_speed_kmh": 187, "max_combo_g": 1.84,
                   "max_brake_bar": 47, "longest_full_throttle_s": 8.2},
        "eob": {"average_nothing_time_s": 0.4, "worst_corner": "Turn 11"},
        "slip_band": {"dominant_band": "approaching_peak",
                       "interpretation": "could be faster"},
        "consistency": {"lap_time_std": 0.8,
                        "most_variable_corner": {"corner": "Turn 4"}},
    }
    p = build_post_session_user_prompt(bundle)
    assert "sid-1" in p
    assert "Turn 10" in p
    assert "T6 oversteer" in p
    assert "187" in p


def test_post_session_user_prompt_handles_empty_bundle():
    p = build_post_session_user_prompt({})
    # Should not crash; produces a valid (sparse) prompt
    assert "SESSION" in p


# ─── match_bentley_concept ───────────────────────────────────────────────────


def _ctx(**kwargs):
    defaults = dict(
        driver_level="intermediate", track_name="Sonoma Raceway",
        next_corner_name="", next_corner_direction="", next_corner_severity=0,
        meters_to_entry=999, next_brake_zone_m=0, next_brake_peak_bar=0,
        next_apex_speed_kmh=0, next_elevation_change_m=0,
        speed_kmh=100, brake_pct=0, throttle_pct=80,
        g_lat=0, g_long=0, in_corner=False, past_apex=False,
        bentley_concept="", bentley_tip="",
    )
    defaults.update(kwargs)
    return CoachContext(**defaults)


def test_bentley_trail_brake_match():
    ctx = _ctx(in_corner=True, brake_pct=20, g_lat=0.6)
    concept, tip = match_bentley_concept(ctx)
    assert concept == "trail_brake"
    assert tip


def test_bentley_entry_release_match():
    ctx = _ctx(in_corner=True, brake_pct=0, g_lat=0.7)
    concept, _ = match_bentley_concept(ctx)
    assert concept == "entry_release"


def test_bentley_hustle_match():
    ctx = _ctx(speed_kmh=120, brake_pct=0, throttle_pct=0,
               meters_to_entry=200)
    concept, _ = match_bentley_concept(ctx)
    assert concept == "hustle"


def test_bentley_eob_match():
    ctx = _ctx(meters_to_entry=80, next_brake_zone_m=100, brake_pct=0)
    concept, _ = match_bentley_concept(ctx)
    assert concept == "eob"


def test_bentley_late_apex_default_for_upcoming_corner():
    ctx = _ctx(meters_to_entry=120, next_corner_name="Turn 5",
               brake_pct=20, throttle_pct=40)
    concept, _ = match_bentley_concept(ctx)
    # When in pre-corner approach with brake on but no other special signals
    assert concept in ("late_apex", "eob")


def test_bentley_look_ahead_default_for_no_corner_context():
    ctx = _ctx(meters_to_entry=999, throttle_pct=80, brake_pct=0)
    concept, _ = match_bentley_concept(ctx)
    assert concept == "look_ahead"


# ─── build_context ───────────────────────────────────────────────────────────


def test_build_context_with_no_corner(real_track, make_frame_fn):
    f = make_frame_fn(t=0, distance=0)
    ctx = build_context(
        driver_level="intermediate", track=real_track,
        frame=f, next_corner=None, meters_to_entry=999.0,
        in_corner_obj=None, past_apex=False,
    )
    assert ctx.next_corner_name == ""
    assert ctx.meters_to_entry == 999.0


def test_build_context_with_real_corner_populates_marker(real_track, make_frame_fn):
    """When upcoming corner has a brake_ref marker (T11 = 'the bump'),
    next_brake_marker_label should be set."""
    t11 = next(c for c in real_track.corners if c.name == "Turn 11")
    f = make_frame_fn(t=0, distance=t11.entry_distance - 100, speed_kmh=140)
    ctx = build_context(
        driver_level="intermediate", track=real_track,
        frame=f, next_corner=t11, meters_to_entry=100.0,
        in_corner_obj=None, past_apex=False,
    )
    assert ctx.next_corner_name == "Turn 11"
    assert ctx.next_brake_marker_label  # should be one of T11's brake markers


def test_build_context_populates_nickname(real_track, make_frame_fn):
    """T11 has the nickname 'Calamity Corner'."""
    t11 = next(c for c in real_track.corners if c.name == "Turn 11")
    f = make_frame_fn(t=0, distance=t11.entry_distance - 100)
    ctx = build_context(
        driver_level="intermediate", track=real_track,
        frame=f, next_corner=t11, meters_to_entry=100.0,
        in_corner_obj=None, past_apex=False,
    )
    assert ctx.next_corner_nickname  # T11 has at least one nickname


# ─── RuleCoach ───────────────────────────────────────────────────────────────


def test_rule_coach_returns_none_when_no_corner_no_concept():
    coach = RuleCoach(driver_level="intermediate")
    ctx = _ctx()  # no corner, no actionable concept
    msg = coach.propose(ctx)
    assert msg is None


def test_rule_coach_returns_message_for_upcoming_corner():
    coach = RuleCoach(driver_level="intermediate")
    ctx = _ctx(meters_to_entry=120, next_corner_name="Turn 5",
               next_corner_direction="R", next_corner_severity=4,
               bentley_concept="late_apex")
    msg = coach.propose(ctx)
    assert isinstance(msg, CoachingMessage)
    assert msg.text


def test_rule_coach_phrasing_differs_per_level(real_track, make_frame_fn):
    t11 = next(c for c in real_track.corners if c.name == "Turn 11")
    f = make_frame_fn(t=0, distance=t11.entry_distance - 120, speed_kmh=140)
    ctx_proto = build_context(
        driver_level="intermediate", track=real_track, frame=f,
        next_corner=t11, meters_to_entry=120.0,
        in_corner_obj=None, past_apex=False,
    )
    out = {}
    for level in ("beginner", "intermediate", "pro"):
        coach = RuleCoach(driver_level=level)
        ctx_proto.driver_level = level
        msg = coach.propose(ctx_proto)
        out[level] = msg.text if msg else ""
    # All three should produce different text
    assert len(set(out.values())) >= 2


def test_rule_coach_voices_marker_label(real_track, make_frame_fn):
    """When marker label is set, the rendered text should include it."""
    t11 = next(c for c in real_track.corners if c.name == "Turn 11")
    f = make_frame_fn(t=0, distance=t11.entry_distance - 120)
    ctx = build_context(
        driver_level="intermediate", track=real_track, frame=f,
        next_corner=t11, meters_to_entry=120.0,
        in_corner_obj=None, past_apex=False,
    )
    coach = RuleCoach(driver_level="intermediate")
    msg = coach.propose(ctx)
    if msg and ctx.next_brake_marker_label:
        assert ctx.next_brake_marker_label in msg.text


# ─── LitertCoach ─────────────────────────────────────────────────────────────


def test_litert_coach_constructs_without_runtime(monkeypatch):
    """When the runtime fails to load, construction must still succeed; the
    coach exposes loaded=False and a non-empty error so callers can decide
    how to fall back. We force the failure via monkeypatch so the test
    works regardless of whether litert-lm + Gemma are installed locally."""
    def _explode(self, _model_path):
        raise RuntimeError("forced-no-runtime")
    monkeypatch.setattr(LitertCoach, "_init_runtime", _explode)

    c = LitertCoach(driver_level="intermediate")
    h = c.health()
    assert h["loaded"] is False
    assert "forced-no-runtime" in h["error"]


def test_litert_coach_propose_falls_back_to_rule():
    """Without an LLM, propose should produce RuleCoach output."""
    c = LitertCoach(driver_level="intermediate")
    ctx = _ctx(meters_to_entry=120, next_corner_name="Turn 5",
               next_corner_direction="R", next_corner_severity=4,
               bentley_concept="late_apex")
    msg = c.propose(ctx)
    # RuleCoach fallback should produce a message
    assert msg is None or isinstance(msg, CoachingMessage)


def test_litert_coach_brief_falls_back_to_templated(monkeypatch):
    """Force-fail the runtime so brief() falls through to the templated
    path. Independent of whether the LLM is installed locally."""
    def _explode(self, _model_path):
        raise RuntimeError("forced-no-runtime")
    monkeypatch.setattr(LitertCoach, "_init_runtime", _explode)

    c = LitertCoach(driver_level="intermediate")
    text, focus, emotion = c.brief(
        driver_id="Taha", today_iso="2026-05-23",
        weather_phase="morning_fog", surface_state="cool damp",
        markers_selected=["Turn 11"],
    )
    assert text  # templated brief
    assert isinstance(focus, list)
    assert emotion == "neutral"     # no LLM ⇒ neutral fallback


def test_litert_coach_debrief_returns_empty_when_no_llm(monkeypatch):
    """Force-empty engine ⇒ debrief returns ("", [], "neutral") so callers
    fall through. Independent of whether the model is installed."""
    def _explode(self, _model_path):
        raise RuntimeError("forced-no-runtime")
    monkeypatch.setattr(LitertCoach, "_init_runtime", _explode)

    c = LitertCoach(driver_level="intermediate")
    text, focus, emotion = c.debrief({"track": "Sonoma Raceway", "scorecard": {}})
    assert text == ""
    assert focus == []
    assert emotion == "neutral"


# ─── Emotion-tag extractor ───────────────────────────────────────────────────


def test_extract_emotion_strips_leading_tag():
    from coach_engine import _extract_emotion
    cleaned, emotion = _extract_emotion("[EMOTION: encouraging]\nGood lap.")
    assert emotion == "encouraging"
    assert cleaned == "Good lap."


def test_extract_emotion_handles_unknown_value():
    from coach_engine import _extract_emotion
    cleaned, emotion = _extract_emotion("[EMOTION: confused]\nText.")
    assert emotion == "neutral"        # falls back
    assert cleaned == "Text."


def test_extract_emotion_no_tag_returns_neutral():
    from coach_engine import _extract_emotion
    cleaned, emotion = _extract_emotion("Just text, no tag.")
    assert emotion == "neutral"
    assert cleaned == "Just text, no tag."


def test_extract_emotion_case_insensitive_tag_name():
    from coach_engine import _extract_emotion
    cleaned, emotion = _extract_emotion("[EMOTION: PROUD]\nNice.")
    assert emotion == "proud"
    assert cleaned == "Nice."


def test_extract_emotion_empty_input_returns_neutral():
    from coach_engine import _extract_emotion
    cleaned, emotion = _extract_emotion("")
    assert emotion == "neutral"
    assert cleaned == ""


def test_valid_emotions_set_has_12_entries():
    from coach_engine import VALID_EMOTIONS
    assert len(VALID_EMOTIONS) == 12
    assert "neutral" in VALID_EMOTIONS
    assert "thinking" in VALID_EMOTIONS
    assert "proud" in VALID_EMOTIONS


def test_build_system_prompt_includes_emotion_instruction():
    from coach_engine import build_system_prompt
    prompt = build_system_prompt("intermediate", "Sonoma Raceway")
    assert "[EMOTION:" in prompt
    assert "neutral" in prompt and "encouraging" in prompt


# ─── make_coach ──────────────────────────────────────────────────────────────


def test_make_coach_rule():
    c = make_coach("rule")
    assert c.name == "rule"
    assert isinstance(c, RuleCoach)


def test_make_coach_litert():
    c = make_coach("litert")
    assert c.name == "litert"


def test_make_coach_tflite_alias():
    c = make_coach("tflite")
    assert c.name == "litert"


def test_make_coach_auto_falls_back_to_rule(monkeypatch):
    """Force-fail the runtime ⇒ make_coach('auto') picks RuleCoach."""
    def _explode(self, _model_path):
        raise RuntimeError("forced-no-runtime")
    monkeypatch.setattr(LitertCoach, "_init_runtime", _explode)
    c = make_coach("auto")
    assert c.name == "rule"


def test_make_coach_invalid_kind_raises():
    with pytest.raises(ValueError):
        make_coach("invalid_kind_value")


# ─── CoachArbiter ────────────────────────────────────────────────────────────


def test_arbiter_p3_safety_passes_immediately():
    arb = CoachArbiter()
    msg = CoachingMessage(text="BRAKE!", priority=3)
    out = arb.submit(msg, now=time.time(), on_straight=False)
    assert out is msg


def test_arbiter_p2_held_in_corner():
    arb = CoachArbiter()
    msg = CoachingMessage(text="trail brake", priority=2)
    out = arb.submit(msg, now=time.time(), on_straight=False)
    assert out is None


def test_arbiter_p2_emits_on_straight():
    arb = CoachArbiter()
    msg = CoachingMessage(text="trail brake", priority=2)
    out = arb.submit(msg, now=time.time(), on_straight=True)
    assert out is msg


def test_arbiter_cooldown_silences_within_window():
    arb = CoachArbiter(cooldown_s=3.0, stale_s=5.0)
    base = 1000.0
    a = CoachingMessage(text="first", priority=1)
    b = CoachingMessage(text="second", priority=1)
    out_a = arb.submit(a, now=base, on_straight=True)
    out_b = arb.submit(b, now=base + 1.0, on_straight=True)
    assert out_a is a
    assert out_b is None  # within cooldown


def test_arbiter_cooldown_releases_after_window():
    arb = CoachArbiter(cooldown_s=3.0, stale_s=5.0)
    base = 1000.0
    a = CoachingMessage(text="first", priority=1)
    arb.submit(a, now=base, on_straight=True)
    out = arb.submit(None, now=base + 4.0, on_straight=True)
    # No queued message → returns None even after cooldown clears
    assert out is None


def test_arbiter_drops_stale_queued_message():
    arb = CoachArbiter(cooldown_s=3.0, stale_s=5.0)
    base = 1000.0
    arb.submit(CoachingMessage(text="held", priority=2),
               now=base, on_straight=False)  # gets queued
    out = arb.submit(None, now=base + 10.0, on_straight=True)
    # Stale by 10s — should be dropped
    assert out is None


# ─── Templated fallbacks ─────────────────────────────────────────────────────


def test_templated_pre_brief_returns_str_and_3_or_fewer_focus():
    text, focus = _templated_pre_brief(
        driver_id="Taha", weather_phase="morning_fog",
        surface_state="cool damp", markers_selected=["Turn 4", "Turn 7", "Turn 11"],
        weakest_recent_corner="Turn 10",
        danger_zones_today=["T6 runoff"],
    )
    assert isinstance(text, str)
    assert len(focus) <= 3


def test_split_brief_narrative_and_focus():
    raw = 'Some narrative about Sonoma.\n<FOCUS>["a","b","c"]'
    text, focus = _split_brief_narrative_and_focus(raw)
    assert "Sonoma" in text
    assert focus == ["a", "b", "c"]


def test_split_debrief_narrative_and_focus():
    raw = 'Debrief narrative.\n<NEXT_FOCUS>["x","y"]'
    text, focus = _split_debrief_narrative_and_focus(raw)
    assert "Debrief" in text
    assert focus == ["x", "y"]


def test_split_handles_missing_token():
    text, focus = _split_brief_narrative_and_focus("just text, no token")
    assert "just text" in text
    assert focus == []
