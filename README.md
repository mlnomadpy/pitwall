# Pitwall Sprint — Trustable AI Racing Coach

**Prove that a split-brain AI system can be trusted at 130 mph.**

Production deployment of the [Pitwall](../forza/) architecture for the Trustable AI sprint (April--May 2026). Coaches drivers in real time at Sonoma Raceway using Gemma 4 on Pixel 10 TPU (reflexive hot path) and Gemini 3.0 on Vertex AI (strategic warm path), connected by the Antigravity store-and-forward pipeline.

## Architecture

```
Racelogic Mini ──┐
                  ├── Sensor Fusion ── Gemma 4 (Pixel 10 TPU, <50ms) ──┐
OBDLink MX ──────┘        │                                             ├── Arbiter ── Pixel Earbuds
                           │                                             │
                      Antigravity ──5G──► Gemini 3.0 (Vertex AI, 2-5s) ─┘
                      (store & forward)
```

## What's Different from Pitwall Open Source

| Pitwall | Sprint |
|---------|--------|
| Hardcoded rules (hot path) | **Gemma 4 LLM on Pixel 10 TPU** |
| SSE + UDP streaming | **Antigravity store-and-forward** |
| Generic coaching rules | **Ross Bentley pedagogical vectors** |
| Commodity hardware ($40-230) | **Racelogic Mini + OBDLink MX + Pixel 10** |
| Laptop + phone + tablet | **Single device: Pixel 10** |
| Driver's personal best | **Gold Standard: AJ's pro reference lap** |

What we **keep from Pitwall** (improvements over V1):

- Confidence-annotated telemetry frame
- Message arbiter (priority, conflict, corner suppression)
- Sensor fusion engine (Kalman, Butterworth, complementary)
- Event-sourced driver profile
- Regression testing for coaching vectors
- Graceful degradation protocol

## Key Dates

- **April 8:** Kickoff
- **April 29:** Architecture gate (no code, no track)
- **May 23:** Field test at Sonoma Raceway
- **May 30:** Sprint wrap

## Docs

```bash
pip install mkdocs-material
cd pitwall-sprint
mkdocs serve -a 127.0.0.1:8889
```

## Team 2 (Intermediate, BMW M3)

| Role | Person |
|------|--------|
| Tech Lead | Hemanth HM |
| Edge / Telemetry | Simon Margolis |
| AGY Pipeline | Taha Bouhsine |
| Data Reasoning | Vijay Vivekanand |
| UX / Frontend | Aileen Villanueva |
