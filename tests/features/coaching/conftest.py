"""
Shared coaching-test setup.

Per ADR-022 the production default for both `LitertCoach` (warm path,
brief/debrief) and the ADK paddock backend is HTTP-to-LocalLLM. Most
existing coaching tests, however, validate the **in-process** behaviour
(engine load / failure / mock conversation). To keep them deterministic
without a LocalLLM instance running, this conftest opts them out of
HTTP mode at import time by clearing `PITWALL_LITERT_URL` and forcing
the ADK backend back to its legacy `litertlm` selector.

Setting these via `os.environ` here — rather than via a function-scoped
`monkeypatch` fixture — matters because module- and session-scoped
fixtures (e.g. the `litert_coach` integration fixture) construct their
coach *before* function-scoped fixtures run, and the env must already
be applied at that point.

Tests that want to validate the HTTP path should
`monkeypatch.setenv("PITWALL_LITERT_URL", ...)` themselves.
"""
from __future__ import annotations

import os

os.environ["PITWALL_LITERT_URL"] = ""
os.environ["PITWALL_ADK_BACKEND"] = "litertlm"

# Re-export helpers from the top-level conftest. Tests in this directory do
# `from conftest import _start_session, _frames_to_payload` and Python's
# conftest resolution would otherwise hide the parent module behind this one.
from tests.conftest import _start_session, _frames_to_payload  # noqa: E402, F401
