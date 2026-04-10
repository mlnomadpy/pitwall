# ADR-009: Graceful Degradation Protocol

## Status
Accepted

## Origin
Adapted from Pitwall ADR-028. Simplified for single-device (Pixel 10) architecture.

## Context
Hardware fails mid-session: Bluetooth drops (OBDLink disconnects), Racelogic battery dies, 5G goes away, Pixel 10 thermals throttle TPU performance. The system must degrade gracefully, never crash, and always inform the driver.

## Decision

### Detection (< 2 seconds)

| Failure | Detection | Time |
|---------|----------|------|
| OBDLink Bluetooth drops | No CAN frames for 200ms | < 500ms |
| Racelogic Bluetooth drops | No GPS/IMU frames for 200ms | < 500ms |
| 5G connectivity lost | Antigravity burst send fails | < 5s |
| Pixel 10 TPU throttled | Gemma 4 inference > 100ms | Immediate |

### Degradation Levels

```mermaid
graph TD
    FULL[Full System<br/>Racelogic + OBDLink + Gemma 4 + Gemini 3.0] -->|OBDLink drops| NO_CAN[No CAN<br/>Lose: throttle, brake, RPM<br/>Keep: GPS, IMU, G-forces<br/>Coaching: G-force rules only]
    
    FULL -->|Racelogic drops| NO_RL[No Racelogic<br/>Lose: GPS, IMU, G-forces<br/>Keep: CAN inputs<br/>Coaching: throttle/brake only<br/>no G-force rules]
    
    FULL -->|5G drops| NO_5G[No Cloud<br/>Lose: Gemini 3.0 warm path<br/>Keep: everything local<br/>Coaching: Gemma 4 hot path only<br/>Antigravity buffers to disk]
    
    FULL -->|TPU throttled| NO_TPU[TPU Degraded<br/>Gemma 4 too slow<br/>Fallback: hardcoded rules<br/>Keep: all sensors + warm path]
    
    NO_CAN -->|Racelogic also drops| OFFLINE[Offline<br/>No sensors<br/>All coaching disabled<br/>Audio: System offline]
```

### Driver Notification

| Event | Audio Message | HUD |
|-------|-------------|-----|
| OBDLink disconnects | "CAN disconnected. Brake coaching off." | Orange indicator |
| Racelogic disconnects | "GPS lost. G-force coaching off." | Orange indicator |
| 5G drops | (silent — hot path unaffected) | Small 5G-off icon |
| TPU throttled | (silent — falls back to rules) | - |
| Everything fails | "System offline." | Red indicator |

### Recovery

When a disconnected sensor starts sending data again:
1. Collect 2 seconds of data
2. Validate: GPS has fix, CAN signals are in range, IMU gravity ≈ 9.81
3. If valid: restore, recalculate confidence, re-enable coaching
4. If invalid: keep disabled, warn driver

## Consequences
Positive: System never crashes. Driver always knows current capability. Hot path (Gemma 4) works without cloud. Warm path (Gemini 3.0) works without CAN. Negative: Degraded modes provide less coaching. Frequent Bluetooth reconnects cause notification churn (mitigated: suppress if same sensor reconnects within 10 seconds).
