import os
import re

monolith = "tests/test_pitwall_backend.py"
with open(monolith, "r") as f:
    content = f.read()

# We want to extract the imports and fixtures to tests/conftest.py
# The fixtures go from line 17 (@pytest.fixture(autouse=True)) to line 68.

lines = content.splitlines()

conftest_lines = [
    "import os",
    "import sys",
    "from pathlib import Path",
    "import pytest",
    "ROOT = Path(__file__).resolve().parents[1]",
    "import pitwall as br",
    "from pitwall.features.track.track_loader import load_track",
    "from pitwall.features.session.session_analyzer import analyze_session",
    "import pitwall.features.track.sonoma as sonoma",
    "from pitwall.helpers import estimate_tts_ms, detect_laps, quantile",
    "from pitwall.db import log_llm_friction",
    "from pitwall.features.realtime.bp_realtime import cue_bus"
]

# Find the end of fixtures
fixture_end = 0
for i, line in enumerate(lines):
    if line.startswith("# ─── /health"):
        fixture_end = i
        break

conftest_lines.extend(lines[17:fixture_end])
conftest_content = "\n".join(conftest_lines) + "\n"

# Enhance isolated_bridge to clean up burst cache properly
conftest_content = conftest_content.replace(
    'monkeypatch.setattr(br.state, "session_bundles", {})',
    'monkeypatch.setattr(br.state, "session_bundles", {})\n    monkeypatch.setattr(br.state, "session_bursts", [])'
)

with open("tests/conftest.py", "w") as f:
    f.write(conftest_content)


# Now split the rest of the tests.
# /health, /analyze (before ADR-015) -> tests/features/test_api_core.py
# /session/* -> tests/features/session/test_api_session.py
# ADR-015 Phase 1-3 -> tests/features/telemetry/test_api_signals.py
# /track/* -> tests/features/track/test_api_track.py
# Quantile, Phase 6 lap detection -> tests/features/session/test_api_analytics.py
# LLM friction, coach gating -> tests/features/coaching/test_api_coaching.py

# To be completely safe and not lose tests, we will just keep test_pitwall_backend.py
# and rename it to test_api_integration.py but drop the fixtures, so it uses conftest.py.
# Wait, the plan explicitly says to slice it into FSD-aligned files.
# Let's do a simple regex split on `# ───` headers.

sections = []
current_section = []
current_title = "imports"

for line in lines[fixture_end:]:
    if line.startswith("# ───"):
        sections.append((current_title, current_section))
        current_title = line
        current_section = [line]
    else:
        current_section.append(line)
sections.append((current_title, current_section))

files = {
    "tests/features/test_api_core.py": [],
    "tests/features/session/test_api_session.py": [],
    "tests/features/telemetry/test_api_signals.py": [],
    "tests/features/track/test_api_track.py": [],
    "tests/features/coaching/test_api_coaching.py": [],
}

for title, sect in sections:
    text = "\n".join(sect)
    if not text.strip(): continue
    
    if "/health" in title or "/analyze" in title and "Phase 3" not in title:
        files["tests/features/test_api_core.py"].append(text)
    elif "/session/" in title or "Phase 6" in title or "Quantile" in title or "Helpers" in title:
        files["tests/features/session/test_api_session.py"].append(text)
    elif "ADR-015" in title and ("Phase 1" in title or "Phase 2" in title or "Phase 3" in title):
        files["tests/features/telemetry/test_api_signals.py"].append(text)
    elif "/track/" in title:
        files["tests/features/track/test_api_track.py"].append(text)
    elif "coach gating" in title or "LLM friction" in title:
        files["tests/features/coaching/test_api_coaching.py"].append(text)
    elif "Roadmap endpoints" in title:
        files["tests/features/test_api_core.py"].append(text)
    else:
        # Fallback
        files["tests/features/test_api_core.py"].append(text)

imports = "import pytest\nimport pitwall as br\nfrom pitwall.helpers import estimate_tts_ms, detect_laps, quantile\nfrom pitwall.db import log_llm_friction\nfrom pitwall.features.realtime.bp_realtime import cue_bus\n\n"

for fpath, contents in files.items():
    if not contents: continue
    os.makedirs(os.path.dirname(fpath), exist_ok=True)
    with open(fpath, "w") as f:
        f.write(imports + "\n\n".join(contents))

os.remove(monolith)
print("Split complete!")
