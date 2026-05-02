# Hardware

Single-device architecture. No laptop in the footwell.

---

## The Kit

| Component | Role | Connection | Cost |
|-----------|------|-----------|------|
| **Pixel 10** | Edge compute (TPU), 5G modem, HUD display, audio hub | Central device | -- |
| **Racelogic Mini** | 20Hz GPS + 3-axis IMU | Bluetooth to Pixel 10 | ~$500 |
| **USB-CAN Adapter** | CAN bus extraction (throttle, brake, RPM, steering, wheel speeds) | USB to Pixel 10 (via Termux) | ~$30–80 |
| **Pixel Earbuds** | Audio coaching delivery | Bluetooth to Pixel 10 | ~$200 |

**Total in-car wiring: one USB cable.** The USB-CAN adapter connects to the OBD-II port and to the Pixel 10 via USB. Racelogic and Earbuds connect via Bluetooth.

```
┌─────────────────────────────────────────────┐
│                   CAR                        │
│                                              │
│   ┌──────────┐          ┌──────────────┐     │
│   │Racelogic │──BT──┐   │  USB-CAN     │     │
│   │  Mini    │      │   │  Adapter     │     │
│   │(windshield)     │   │  (in OBD-II  │     │
│   └──────────┘      │   │   port)      │     │
│                     │   └──────┬───────┘     │
│                     │          │ USB          │
│                     ▼          ▼              │
│              ┌─────────────────────┐          │
│              │     Pixel 10        │          │
│              │  ┌───────────────┐  │          │
│              │  │ Signal Light  │  │          │
│              │  │ HUD (screen)  │  │          │
│              │  └───────────────┘  │          │
│              │  ┌───────────────┐  │          │
│              │  │ Gemma 4 (TPU) │  │          │
│              │  └───────────────┘  │          │
│              └──────────┬──────────┘          │
│                         │ BT                  │
│              ┌──────────┴──────────┐          │
│              │   Pixel Earbuds     │          │
│              │   (in driver's ears)│          │
│              └─────────────────────┘          │
└─────────────────────────────────────────────┘
```

---

## Pixel 10

The Pixel 10 is the entire compute stack:

| Function | What It Does |
|----------|-------------|
| **Edge TPU** | Runs Gemma 4 inference at <50ms per frame |
| **Display** | Shows Signal Light HUD (red/green grip bars) |
| **Bluetooth** | Connects to Racelogic and Earbuds |
| **USB** | Connects to USB-CAN adapter for CAN bus |
| **Audio** | Routes TTS to Pixel Earbuds |
| **Storage** | Persists session data and telemetry to DuckDB |
| **GPS** | Backup GPS if Racelogic disconnects (lower quality) |

### Mounting

Mount the Pixel 10 on the dashboard or center console using a rigid mount. The screen should be visible in peripheral vision but **never the focus of attention**.

The display shows:
- Large red/green bars indicating grip potential (Signal Light)
- Session status (lap count, connection indicators)
- Nothing else during driving. No graphs. No numbers. No text.

### Power

USB-C connected to car 12V via cigarette lighter adapter. The Pixel 10 must remain powered throughout the session. Enable "Stay awake while charging" in developer options.

---

## Racelogic Mini

Professional GPS with integrated 3-axis IMU. Device: VBVDHD2-V5 2cam (serial 005358).

| Spec | Value | Measured from Data |
|------|-------|-------------------|
| VBO output rate | **10Hz** (sample period 0.100s) | Confirmed across all 183 files |
| GPS accuracy | <1m (open sky) | 8 tracks identified by GPS clustering |
| IMU axes | 3-axis accelerometer | gLat: -1.95 to +1.95G, gLong: -1.66 to +0.21G |
| Combined G | Computed | Max 2.86G, P95 1.20G, mean 0.63G |
| Altitude | GPS-derived | 8–93m range (48m delta at Sonoma, 30m at Track 2, 4m at Track 8) |
| Connection | Bluetooth (to Pixel 10) | — |
| Power | Internal battery (~8 hours) | — |
| Mounting | Windshield suction cup | — |

!!! note "10Hz Not 20Hz"
    The Racelogic hardware captures at 20Hz internally, but the VBO file output is 10Hz. All LSTM models, sonic cues, and latency budgets are designed for 100ms frame intervals. The next frame arrives in 100ms, not 50ms.

### Signal Confidence

| Condition | GPS Confidence | IMU Confidence |
|-----------|---------------|----------------|
| Open sky, sats > 60 (quality flag) | 0.95 | 0.95 |
| Partial cover, sats 20-60 | 0.80 | 0.95 |
| Heavy cover, sats < 20 | 0.50 | 0.95 |
| Tunnel / no GPS | 0.00 (stale) | 0.95 |

IMU confidence is always 0.95 because it's self-contained. The `sats` field encodes quality flags beyond literal satellite count — values like 100, 140 appear in the data. Use sats > 60 as the "good GPS" threshold.

---

## USB-CAN Adapter

CAN bus adapter. The deployment target uses a **CANable Pro** or **Macchina M2** connected via USB to the Pixel 10 in Termux. The original 183 VBO sessions were recorded with an OBDLink MX via Bluetooth, but the as-shipped architecture uses `python-can` + `cantools` over USB for lower latency and higher reliability.

| Spec | Value |
|------|-------|
| Protocol | CAN bus (ISO 15765-4) at native bus speed |
| Effective rate in VBO | **10Hz** (synced with Racelogic sample period) |
| Connection | USB to Pixel 10 (via OTG / Termux) |
| **Working signals (11)** | Brake pressure (0-104 bar), brake position (0-1), throttle (0-99%), steering (-1024 to +372°), RPM (843-8582), coolant temp (68-99°C), oil temp (84-121°C), oil pressure (0.6-5.85 bar), fuel level (20-46%), battery voltage (13.1-13.6V), coolant pressure (7.3-34.8 PSI) |
| **Broken signals (7)** | Gear (255 constant), clutch (255), AFR (500), EGT (-50), OBD speed (500), head temp (mislabeled), Brake_Press_Calc (inconsistent) |
| Power | OBD-II port pin 16 (always-on 12V) |

!!! warning "7 Unmapped CAN Signals"
    Gear, clutch, air-fuel ratio, exhaust temp, OBD vehicle speed, head temperature, and computed brake pressure are constant across all 183 files. These CAN IDs are not mapped in the DBC configuration for this car. **Gear must be derived from RPM/speed ratio** using the BMW S54 gear ratios. Individual wheel speeds are also not available — the ABS CAN IDs are not mapped.

### Why USB-CAN, Not OBDLink MX

The original prototype used OBDLink MX via Bluetooth. The shipped architecture switched to direct USB-CAN adapters for several reasons:

| Feature | OBDLink MX (Bluetooth) | USB-CAN (CANable Pro) |
|---------|--------|-----------|
| Connection | Bluetooth SPP (pairing, reconnects) | USB (plug and go) |
| Latency | ~20 ms BT overhead | <1 ms |
| python-can support | Limited (BT serial wrapper) | Native (slcan) |
| DBC decoding | Separate layer | `cantools` built-in |
| Cost | ~$100 | ~$30–80 |

**This matters for coaching latency.** With USB-CAN, brake pressure arrives with <1 ms transport overhead vs ~20 ms over Bluetooth, keeping the hot-path budget under 50 ms.

### Per-Car CAN Configuration

CAN signal IDs vary by manufacturer. The system needs a configuration file per car:

```json
{
  "car": "BMW E46 M3",
  "can_speed": 500000,
  "signals": {
    "throttle": {"can_id": "0x1B4", "byte_offset": 2, "bit_length": 8, "scale": 0.392, "offset": 0},
    "brake_pressure": {"can_id": "0x1A4", "byte_offset": 0, "bit_length": 16, "scale": 0.1, "offset": 0},
    "rpm": {"can_id": "0x0A8", "byte_offset": 4, "bit_length": 16, "scale": 0.25, "offset": 0},
    "steering_angle": {"can_id": "0x0C2", "byte_offset": 0, "bit_length": 16, "scale": 0.1, "offset": -800},
    "wheel_speed_fl": {"can_id": "0x1A0", "byte_offset": 0, "bit_length": 16, "scale": 0.01, "offset": 0}
  }
}
```

**Pod configurations:**
- Team 1 (Beginner): Rental car — TBD, may use standard OBD PIDs instead of CAN pass-through
- Team 2 (Intermediate): BMW M3 — E46 CAN database available
- Team 3 (Advanced): Race car — custom CAN configuration

---

## Pixel Earbuds

The primary driver interface. All coaching is delivered via audio.

| Spec | Value |
|------|-------|
| Connection | Bluetooth LE to Pixel 10 |
| Latency | <50ms (aptX Adaptive) |
| Noise isolation | Passive (not active NC — driver must hear the car) |
| Battery | ~5 hours continuous audio |

### Audio Design Principles

1. **Short messages.** Reflexive cues: 1-5 words. Strategic coaching: 10-25 words maximum.
2. **No continuous audio.** The system is mostly silent. Coaching fires only on detected moments.
3. **Natural voice.** TTS using Gemini or on-device TTS. No robotic voice — driver must trust the coach.
4. **One message at a time.** The arbiter prevents overlapping messages.
5. **Driver can hear the car.** Passive isolation only. Engine sound is critical information (RPM, tire squeal = slip).

---

## Pre-Session Hardware Checklist

- [ ] Pixel 10 charged and mounted
- [ ] "Stay awake while charging" enabled
- [ ] Racelogic Mini mounted on windshield, powered on, GPS fix acquired
- [ ] USB-CAN adapter plugged into OBD-II port and USB to Pixel 10
- [ ] Bluetooth paired: Racelogic + Earbuds connected
- [ ] App launched, telemetry flowing (check all signal indicators green)
- [ ] Signal Light HUD visible in peripheral vision
- [ ] Pixel Earbuds in, coaching audio audible
- [ ] On-device coaching verified (warm path does not require network)
- [ ] CAN configuration (DBC) loaded for this car
