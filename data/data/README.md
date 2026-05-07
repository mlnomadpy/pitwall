# Telemetry Data

VBO telemetry recordings from Racelogic VBOX + OBDLink MX.

## Documentation

| Document | What It Covers |
|----------|---------------|
| [VBO_FORMAT.md](VBO_FORMAT.md) | The `.vbo` file format: sections, structure, how to parse it |
| [SIGNAL_REFERENCE.md](SIGNAL_REFERENCE.md) | Every signal: name, unit, range, description, coaching use, confidence |
| [DERIVED_FEATURES.md](DERIVED_FEATURES.md) | Features computed at runtime from raw signals (not in the VBO) |
| [DATASET_OVERVIEW.md](DATASET_OVERVIEW.md) | All 183 files: tracks, session types, top sessions, statistics |
| [DATA_QUALITY.md](DATA_QUALITY.md) | Known issues, broken signals, timestamp anomalies, ML recommendations |

## Quick Stats

- **183 VBO files** | 535,366 frames | 14.9 hours
- **52 hot lap sessions** (470K frames, 13.1h) — usable for ML training
- **8 tracks** — 3 primary (Sonoma, Track 2, Track 8)
- **11 coaching-grade signals** at 0.90-0.95 confidence
- **Key session:** VBOX0318 — fastest (198 km/h, 104 bar brake, 1.84G lateral)
