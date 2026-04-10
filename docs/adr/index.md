# Architecture Decision Records

Decisions for the Pitwall Sprint, adapted from the [Pitwall open-source project](https://github.com/...) with modifications for the Google hardware stack and production requirements.

## Index

| ADR | Title | Status | Origin |
|-----|-------|--------|--------|
| [001](001-confidence-frame.md) | Confidence-Annotated Telemetry Frame | Accepted | From Pitwall, adapted for Racelogic + OBDLink |
| [002](002-split-brain-arbiter.md) | Split-Brain Architecture with Message Arbiter | Accepted | From Pitwall, adapted for Gemma 4 + Gemini 3 |
| [003](003-gemma-edge.md) | Gemma 4 as Edge LLM on Pixel 10 TPU | Accepted | New for sprint (replaces Pitwall's rules engine) |
| [004](004-antigravity-pipeline.md) | Antigravity Store-and-Forward Pipeline | Accepted | New for sprint (replaces Pitwall's SSE streaming) |
| [005](005-pedagogical-vectors.md) | Pedagogical Vector Retrieval from Ross Bentley Curriculum | Accepted | New for sprint (replaces Pitwall's hardcoded rules) |
| [006](006-sensor-fusion.md) | Sensor Fusion for Racelogic + OBDLink | Accepted | From Pitwall ADR-026, adapted for pro hardware |
| [007](007-event-sourced-profile.md) | Event-Sourced Driver Profile | Accepted | From Pitwall ADR-023, unchanged |
| [008](008-rule-testing.md) | Pedagogical Vector Regression Testing | Accepted | From Pitwall ADR-027, adapted for vectors |
| [009](009-graceful-degradation.md) | Graceful Degradation Protocol | Accepted | From Pitwall ADR-028, adapted for single-device |
