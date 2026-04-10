"""
Sonic Model v2 — Delta-driven audio cues from LSTM v3 predictions.

Instead of hand-tuned if/else rules, the LSTM predicts what SHOULD happen
in the next 2 seconds. The gap between prediction and reality drives
the tone continuously.

Audio layers:
  1. Delta tone (continuous) — pitch tracks speed delta vs prediction
  2. Brake confidence (overlay) — how close braking matches predicted
  3. Grip tone (continuous) — friction circle utilization (kept from v1)
  4. Corner chime (event) — score after each corner pass

The model predicts from the driver's OWN best laps, so the delta is
personal: "you vs your best self at this exact track position."
"""

import numpy as np
import torch
from dataclasses import dataclass
from enum import Enum

from lstm_predictor_v3 import (
    LSTMPredictorV3, compute_frame_features, compute_context,
    compute_coarse_stats, compute_targets, get_corner_id,
    HISTORY_FINE, HISTORY_MED, HISTORY_COARSE, FRAME_FEAT, COARSE_FEAT,
    DEVICE,
)


class Pattern(Enum):
    SILENT = "silent"
    CONTINUOUS = "continuous"
    PULSE = "pulse"
    FAST_PULSE = "fast_pulse"
    BUZZ = "buzz"
    CHIME_UP = "chime_up"
    CHIME_DOWN = "chime_down"
    CHIME_NEUTRAL = "chime_neutral"


@dataclass
class AudioCue:
    layer: str
    frequency: float
    volume: float
    pattern: Pattern
    priority: int
    reason: str


class SonicModelV2:
    """
    LSTM-driven sonic model. Maintains a frame buffer and runs
    the LSTM predictor on each frame to generate delta-based audio cues.
    """

    def __init__(self, model_path, track):
        self.track = track
        self.frame_buffer = []     # raw TelemetryFrame objects
        self.feature_buffer = []   # computed feature vectors
        self.max_buffer = 60       # 6 seconds of history

        # Load LSTM v3
        checkpoint = torch.load(model_path, map_location=DEVICE)
        self.model = LSTMPredictorV3().to(DEVICE)
        self.model.load_state_dict(checkpoint["model_state"])
        self.model.eval()

        # Session calibration
        self.max_combo_g = 2.29
        self.max_brake = 100.0

        # Rolling prediction state
        self.last_prediction = None        # (20, 3) predicted future
        self.last_prediction_time = 0.0
        self.prediction_age_frames = 0     # how many frames since last prediction

        # Corner scoring
        self.corner_entry_speed = 0.0
        self.corner_min_speed = 999.0
        self.corner_exit_speed = 0.0
        self.in_corner_name = None

    def update(self, frame):
        """
        Process one telemetry frame. Returns list of AudioCues.
        Call this at 10Hz with each new frame.
        """
        self.frame_buffer.append(frame)
        if len(self.frame_buffer) > self.max_buffer:
            self.frame_buffer.pop(0)

        # Compute features for this frame
        prev = self.frame_buffer[-2] if len(self.frame_buffer) > 1 else frame
        feat = compute_frame_features(frame, prev)
        self.feature_buffer.append(feat)
        if len(self.feature_buffer) > self.max_buffer:
            self.feature_buffer.pop(0)

        cues = []

        # ─── Run LSTM prediction every 5 frames (0.5s) ──────────
        self.prediction_age_frames += 1

        if len(self.frame_buffer) >= 30 and self.prediction_age_frames >= 5:
            prediction = self._predict(frame)
            if prediction is not None:
                self.last_prediction = prediction
                self.last_prediction_time = frame.timestamp
                self.prediction_age_frames = 0

        # ─── Generate cues from prediction delta ─────────────────
        if self.last_prediction is not None:
            delta_cues = self._delta_cues(frame)
            cues.extend(delta_cues)

        # ─── Grip tone (kept from v1, always active) ─────────────
        grip_cue = self._grip_tone(frame)
        if grip_cue:
            cues.append(grip_cue)

        # ─── Corner scoring (chime on exit) ──────────────────────
        corner_cue = self._corner_tracking(frame)
        if corner_cue:
            cues.append(corner_cue)

        return cues

    def _predict(self, frame):
        """Run LSTM v3 inference on current buffer."""
        n = len(self.feature_buffer)
        if n < HISTORY_FINE + 5:
            return None

        feat_arr = np.array(self.feature_buffer, dtype=np.float32)

        # Fine: last 10 frames
        fine = feat_arr[-HISTORY_FINE:]  # (10, 22)

        # Medium: last 20 frames downsampled 2x
        med_start = max(0, n - 20)
        med_raw = feat_arr[med_start:]
        if len(med_raw) >= 2:
            med = med_raw[::2][:HISTORY_MED]
            if len(med) < HISTORY_MED:
                pad = np.zeros((HISTORY_MED - len(med), FRAME_FEAT), dtype=np.float32)
                med = np.concatenate([pad, med], axis=0)
        else:
            med = np.zeros((HISTORY_MED, FRAME_FEAT), dtype=np.float32)

        # Coarse: rolling stats from last 50 frames
        coarse_vecs = []
        frames = self.frame_buffer
        for w in range(5):
            w_end = len(frames) - w * 10
            w_start = w_end - 10
            coarse_vecs.append(compute_coarse_stats(frames, w_start, w_end))
        coarse = np.array(coarse_vecs, dtype=np.float32)  # (5, 8)

        # Context
        ctx = np.array(compute_context(frame, self.track), dtype=np.float32)

        # Corner ID
        corner_id = get_corner_id(frame, self.track)

        # Last targets (for residual)
        last_t = np.array(compute_targets(frame), dtype=np.float32)

        # Inference
        with torch.no_grad():
            fine_t = torch.tensor(fine).unsqueeze(0).to(DEVICE)
            med_t = torch.tensor(med).unsqueeze(0).to(DEVICE)
            coarse_t = torch.tensor(coarse).unsqueeze(0).to(DEVICE)
            ctx_t = torch.tensor(ctx).unsqueeze(0).to(DEVICE)
            cid_t = torch.tensor([corner_id], dtype=torch.long).to(DEVICE)
            last_t_t = torch.tensor(last_t).unsqueeze(0).to(DEVICE)

            pred = self.model(fine_t, med_t, coarse_t, ctx_t, cid_t, last_t_t)
            return pred[0].cpu().numpy()  # (20, 3)

    def _delta_cues(self, frame):
        """Generate audio cues from the gap between predicted and actual."""
        cues = []
        pred = self.last_prediction
        age = self.prediction_age_frames

        if pred is None or age >= 20:
            return cues

        # The prediction made N frames ago predicted what should be happening NOW
        # Look at prediction[age] vs actual frame
        if age < pred.shape[0]:
            pred_speed = pred[age, 0] * 60.0 * 3.6   # → km/h
            pred_brake = pred[age, 1] * 100.0          # → bar
            # pred_throttle_rel = pred[age, 2]         # relative

            actual_speed = frame.speed * 3.6           # → km/h
            actual_brake = frame.brake_pressure         # bar

            speed_delta = actual_speed - pred_speed     # positive = faster than predicted
            brake_delta = actual_brake - pred_brake     # positive = braking harder than predicted

            # ─── SPEED DELTA TONE ────────────────────────────
            # Neutral at 440Hz. Faster than predicted = higher pitch. Slower = lower.
            # Only fire when delta is meaningful (>2 km/h)
            if abs(speed_delta) > 2.0:
                # Map delta to frequency: ±30 km/h → ±300Hz around 440Hz
                freq = 440.0 + (speed_delta / 30.0) * 300.0
                freq = max(200, min(900, freq))

                # Volume scales with delta magnitude
                vol = min(0.6, abs(speed_delta) / 20.0 * 0.5 + 0.1)

                if speed_delta > 5:
                    # Too fast — rising pitch, warning
                    cues.append(AudioCue(
                        layer="speed_delta",
                        frequency=freq,
                        volume=vol,
                        pattern=Pattern.CONTINUOUS,
                        priority=2 if speed_delta > 10 else 1,
                        reason=f"Arriving {speed_delta:+.0f} km/h vs predicted ({pred_speed:.0f} km/h)"
                    ))
                elif speed_delta < -5:
                    # Too slow — low pitch, encouragement
                    cues.append(AudioCue(
                        layer="speed_delta",
                        frequency=freq,
                        volume=vol * 0.7,  # quieter for "too slow"
                        pattern=Pattern.PULSE,
                        priority=1,
                        reason=f"Pace delta {speed_delta:+.0f} km/h — you have more ({pred_speed:.0f} km/h predicted)"
                    ))

            # ─── BRAKE DELTA TONE ────────────────────────────
            # When the model predicts braking but the driver isn't
            if pred_brake > 10 and actual_brake < 3:
                # Model says brake, driver hasn't started
                urgency = min(1.0, pred_brake / 50.0)
                cues.append(AudioCue(
                    layer="brake_delta",
                    frequency=800 + urgency * 400,
                    volume=0.4 + urgency * 0.3,
                    pattern=Pattern.FAST_PULSE if urgency > 0.6 else Pattern.PULSE,
                    priority=2,
                    reason=f"Model predicts {pred_brake:.0f} bar brake — driver at {actual_brake:.0f} bar"
                ))

            # When driver brakes but model doesn't predict it
            elif actual_brake > 15 and pred_brake < 5:
                cues.append(AudioCue(
                    layer="brake_delta",
                    frequency=350,
                    volume=0.3,
                    pattern=Pattern.CONTINUOUS,
                    priority=1,
                    reason=f"Braking {actual_brake:.0f} bar — model didn't expect braking ({pred_brake:.0f} bar)"
                ))

        # ─── LOOKAHEAD: what does the model predict 1s from now? ─────
        look_idx = min(age + 10, pred.shape[0] - 1)  # 1 second ahead
        if look_idx < pred.shape[0]:
            future_speed = pred[look_idx, 0] * 60.0 * 3.6
            future_brake = pred[look_idx, 1] * 100.0
            current_speed = frame.speed * 3.6

            # If model predicts big speed drop in 1 second, preemptive warning
            speed_drop = current_speed - future_speed
            if speed_drop > 20 and frame.brake_pressure < 5:
                # Big decel predicted but driver isn't braking yet
                cues.append(AudioCue(
                    layer="lookahead",
                    frequency=600 + min(speed_drop, 60) * 10,
                    volume=min(0.7, speed_drop / 50.0 * 0.5),
                    pattern=Pattern.PULSE,
                    priority=2,
                    reason=f"Lookahead: {speed_drop:.0f} km/h speed drop predicted in 1s"
                ))

            # If model predicts braking in 1s
            if future_brake > 20 and frame.brake_pressure < 3:
                cues.append(AudioCue(
                    layer="lookahead_brake",
                    frequency=500,
                    volume=0.35,
                    pattern=Pattern.PULSE,
                    priority=1,
                    reason=f"Lookahead: {future_brake:.0f} bar brake predicted in 1s"
                ))

        return cues

    def _grip_tone(self, frame):
        """Friction circle utilization tone (kept from v1)."""
        grip = frame.combo_g / self.max_combo_g if self.max_combo_g > 0 else 0

        if grip > 1.05:
            return AudioCue("grip", 1600, 0.8, Pattern.BUZZ, 3,
                            f"Over limit: {frame.combo_g:.2f}G / {self.max_combo_g:.2f}G")
        elif grip > 0.85:
            freq = 1000 + (grip - 0.85) * 3000
            return AudioCue("grip", freq, 0.4, Pattern.CONTINUOUS, 0,
                            f"Near limit: {grip:.0%}")
        elif grip > 0.4:
            freq = 200 + grip * 800
            vol = 0.08 + grip * 0.15
            return AudioCue("grip", freq, vol, Pattern.CONTINUOUS, 0,
                            f"Grip: {grip:.0%}")
        return None

    def _corner_tracking(self, frame):
        """Track corner entry/exit and chime on exit with a score."""
        from track_loader import is_in_corner
        corner = is_in_corner(self.track, frame.distance)

        if corner and self.in_corner_name != corner.name:
            # Entered a new corner
            self.in_corner_name = corner.name
            self.corner_entry_speed = frame.speed * 3.6
            self.corner_min_speed = frame.speed * 3.6
            return None

        if corner and self.in_corner_name:
            # In corner — track min speed
            speed_kmh = frame.speed * 3.6
            if speed_kmh < self.corner_min_speed:
                self.corner_min_speed = speed_kmh
            return None

        if not corner and self.in_corner_name:
            # Just exited a corner — compute score and chime
            self.corner_exit_speed = frame.speed * 3.6

            # Simple score: compare to corner's typical speeds
            from track_loader import find_nearest_corner
            # Find the corner we just exited (look back)
            for c in self.track.corners:
                if c.name == self.in_corner_name:
                    typical_exit = c.exit_speed * 3.6
                    typical_min = c.apex_speed * 3.6

                    if typical_exit > 0 and typical_min > 0:
                        exit_ratio = min(1.0, self.corner_exit_speed / typical_exit)
                        min_ratio = min(1.0, self.corner_min_speed / typical_min)
                        score = (exit_ratio * 0.6 + min_ratio * 0.4) * 100

                        corner_name = self.in_corner_name
                        self.in_corner_name = None

                        if score > 90:
                            return AudioCue("corner_score", 800, 0.5, Pattern.CHIME_UP, 0,
                                            f"{corner_name}: {score:.0f}/100 — excellent exit {self.corner_exit_speed:.0f} km/h")
                        elif score > 70:
                            return AudioCue("corner_score", 600, 0.3, Pattern.CHIME_NEUTRAL, 0,
                                            f"{corner_name}: {score:.0f}/100 — exit {self.corner_exit_speed:.0f} km/h (typical {typical_exit:.0f})")
                        else:
                            return AudioCue("corner_score", 400, 0.4, Pattern.CHIME_DOWN, 0,
                                            f"{corner_name}: {score:.0f}/100 — exit {self.corner_exit_speed:.0f} km/h vs {typical_exit:.0f} typical")
                    break

            self.in_corner_name = None

        return None
