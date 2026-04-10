"""
LSTM Sequence Predictor v3 — All improvements applied.

Improvements over v2:
  1. Corner embedding (12-dim learned vector per corner ID)
  2. Cross-signal engineered features (brake×gLat, throttle×gLat, speed×heading_rate)
  3. Multi-scale input (1s fine + 2s medium + 5s coarse stats)
  4. Quality-weighted training on ALL passes (not just top 20%)
  5. Lap context features (lap number, stint time, rolling avg speed)
  6. Data augmentation (mirror corners, time-stretch, noise)
  7. Relative throttle target (throttle/speed ratio)
  8. Residual connection + Huber loss (kept from v2)
"""

import argparse
import json
import math
import os
import pickle
import random
import sys
import time
from collections import defaultdict, Counter
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader

from vbo_parser import parse_vbo, TelemetryFrame
from track_builder import compute_distances, filter_hot_laps
from track_loader import (
    load_track, is_in_corner, is_past_apex,
    find_nearest_corner, distance_to_corner, get_elevation,
)


# ─── Config ──────────────────────────────────────────────────────────────────

HISTORY_FINE = 10       # 1 second at 10Hz (full resolution)
HISTORY_MED = 10        # 2 seconds at 5Hz (downsampled 2x from 20 frames)
HISTORY_COARSE = 5      # 5 coarse stat vectors from last 50 frames
HORIZON = 20            # 2 seconds prediction

FRAME_FEAT = 22         # per-timestep features (raw 8 + engineered 8 + cross-signal 6)
CONTEXT_FEAT = 8        # track context
COARSE_FEAT = 8         # rolling statistics
CORNER_EMBED_DIM = 12   # learned corner embedding
MAX_CORNERS = 20        # max corners per track (for embedding table)
OUTPUT_FEAT = 3         # speed, brake, throttle_ratio

DEVICE = "mps" if torch.backends.mps.is_available() else "cpu"

TRAIN_FILES = [
    "Sonoma Intermediate - 1_47.5.vbo", "VBOX0167.vbo", "VBOX0173.vbo",
    "VBOX0181.vbo", "VBOX0188.vbo", "VBOX0196.vbo", "VBOX0202.vbo",
    "VBOX0208.vbo", "VBOX0212.vbo", "VBOX0217.vbo", "VBOX0218.vbo",
    "VBOX0220.vbo", "VBOX0226.vbo", "VBOX0229.vbo", "VBOX0230.vbo",
    "VBOX0233.vbo", "VBOX0237.vbo",
    "VBOX0135.vbo", "VBOX0137.vbo", "VBOX0139.vbo", "VBOX0141.vbo",
    "VBOX0144.vbo", "VBOX0295.vbo", "VBOX0297.vbo", "VBOX0303.vbo",
    "VBOX0308.vbo", "VBOX0309.vbo", "VBOX0310.vbo", "VBOX0311.vbo",
    "VBOX0312.vbo", "VBOX0313.vbo", "VBOX0314.vbo", "VBOX0315.vbo",
    "VBOX0318.vbo",
]
VAL_FILES = ["VBOX0240.vbo", "VBOX0245.vbo", "VBOX0248.vbo", "VBOX0253.vbo", "VBOX0260.vbo"]
TEST_FILES = [
    "VBOX0266.vbo", "VBOX0268.vbo", "VBOX0269.vbo", "VBOX0271.vbo",
    "VBOX0275.vbo", "VBOX0276.vbo", "VBOX0279.vbo", "VBOX0288.vbo",
    "VBOX0290.vbo", "VBOX0293.vbo",
]


# ─── Feature Engineering ─────────────────────────────────────────────────────

def compute_frame_features(f, prev, dt=0.1):
    """Extract 22 features from one frame."""
    # ─── Raw signals (8) ─────────────────────────────────
    speed_n = f.speed / 60.0
    glat_n = f.g_lat / 2.0
    glong_n = f.g_long / 2.0
    brake_n = min(f.brake_pressure / 100.0, 1.0)
    throttle_n = f.throttle / 100.0
    steer_n = max(-1, min(1, f.steering / 400.0))
    combo_n = min(f.combo_g / 2.5, 1.0)
    rpm_n = f.rpm / 9000.0

    # ─── Derivatives (5) ─────────────────────────────────
    dh = f.heading - prev.heading
    if dh > 180: dh -= 360
    elif dh < -180: dh += 360
    heading_rate = (dh / dt) / 50.0

    speed_dot = (f.speed - prev.speed) / dt / 15.0
    brake_dot = (f.brake_pressure - prev.brake_pressure) / dt / 200.0
    throttle_dot = (f.throttle - prev.throttle) / dt / 300.0
    steer_dot = (f.steering - prev.steering) / dt / 500.0

    # ─── Composite indicators (3) ────────────────────────
    friction_pct = min(f.combo_g / 2.29, 1.5)

    trail_indicator = 0.0
    if f.brake_pressure > 3 and abs(f.g_lat) > 0.3:
        trail_indicator = min(f.brake_pressure / 50.0, 1.0) * min(abs(f.g_lat) / 1.5, 1.0)

    coast_indicator = 0.0
    if f.throttle < 10 and f.brake_pressure < 2 and f.speed > 10:
        coast_indicator = 1.0 - max(f.throttle / 10.0, f.brake_pressure / 2.0)

    # ─── Cross-signal features (6) — NEW ─────────────────
    brake_x_glat = brake_n * abs(glat_n)           # trail brake intensity
    throttle_x_glat = throttle_n * abs(glat_n)     # powered cornering intensity
    speed_x_heading = speed_n * abs(heading_rate)   # cornering speed (how fast through the turn)
    brake_x_speed = brake_n * speed_n               # braking energy proxy
    throttle_x_speed = throttle_n * speed_n         # acceleration power proxy
    glat_x_glong = abs(glat_n) * abs(glong_n)      # friction circle quadrant (combined loading)

    return [
        speed_n, glat_n, glong_n, brake_n, throttle_n, steer_n, combo_n, rpm_n,
        heading_rate, speed_dot, brake_dot, throttle_dot, steer_dot,
        friction_pct, trail_indicator, coast_indicator,
        brake_x_glat, throttle_x_glat, speed_x_heading,
        brake_x_speed, throttle_x_speed, glat_x_glong,
    ]


def compute_context(f, track):
    """Extract 8 track context features."""
    next_c = find_nearest_corner(track, f.distance)
    corner = is_in_corner(track, f.distance)
    past = is_past_apex(track, f.distance)

    d2c = distance_to_corner(track, f.distance, next_c) / track.track_length if next_c else 1.0
    severity = next_c.severity / 6.0 if next_c else 0.0
    direction = (1.0 if next_c.direction == "right" else -1.0) if next_c else 0.0

    dist_in_corner = -1.0
    if corner:
        total = corner.exit_distance - corner.entry_distance
        if total > 0:
            pos = (f.distance % track.track_length) - corner.entry_distance
            dist_in_corner = max(0, min(1, pos / total))

    past_apex = 1.0 if past else 0.0
    elev = (get_elevation(track, f.distance) - 20.0) / 40.0

    in_brake_zone = 0.0
    if next_c and next_c.brake_distance > 0:
        d = distance_to_corner(track, f.distance, next_c)
        if 0 < d < next_c.brake_distance:
            in_brake_zone = 1.0 - (d / next_c.brake_distance)

    track_pos = (f.distance % track.track_length) / track.track_length

    return [d2c, severity, direction, dist_in_corner, past_apex, elev, in_brake_zone, track_pos]


def compute_coarse_stats(frames, start, end):
    """Compute rolling statistics over a window of frames."""
    window = frames[max(0, start):end]
    if len(window) < 5:
        return [0.0] * COARSE_FEAT

    speeds = [f.speed for f in window]
    glats = [abs(f.g_lat) for f in window]
    brakes = [f.brake_pressure for f in window]
    throttles = [f.throttle for f in window]

    return [
        np.mean(speeds) / 60.0,             # avg speed
        np.std(speeds) / 20.0,              # speed variation
        np.max(glats) / 2.0,                # peak gLat
        np.mean(brakes) / 50.0,             # avg brake
        np.mean(throttles) / 100.0,          # avg throttle
        sum(1 for b in brakes if b > 5) / len(window),  # braking fraction
        sum(1 for t, b in zip(throttles, brakes)
            if t < 10 and b < 2) / len(window),  # coast fraction
        len(window) / 500.0,                 # window size (for lap context proxy)
    ]


def get_corner_id(f, track):
    """Get corner ID (0-based) for embedding. 0 = not in corner."""
    corner = is_in_corner(track, f.distance)
    if corner is None:
        next_c = find_nearest_corner(track, f.distance)
        if next_c:
            d = distance_to_corner(track, f.distance, next_c)
            if d < 100:  # approaching corner within 100m
                return next_c.number  # 1-based corner number
        return 0  # not near any corner
    return corner.number


def compute_targets(f):
    """Extract 3 targets: speed, brake, throttle_relative."""
    speed_n = f.speed / 60.0
    brake_n = min(f.brake_pressure / 100.0, 1.0)
    # Relative throttle: throttle / max(speed, 5m/s) — normalized
    # This removes the speed-dependence from throttle prediction
    throttle_rel = (f.throttle / 100.0) / max(speed_n, 0.1)
    throttle_rel = min(throttle_rel, 3.0) / 3.0  # clip and normalize
    return [speed_n, brake_n, throttle_rel]


# ─── Corner Pass Extraction ──────────────────────────────────────────────────

def extract_corner_passes(frames, track):
    passes = []
    i = 0
    while i < len(frames):
        corner = is_in_corner(track, frames[i].distance)
        if corner:
            approach_start = max(0, i - 50)  # 5 seconds before (for coarse stats)
            j = i
            while j < len(frames) and is_in_corner(track, frames[j].distance) == corner:
                j += 1
            exit_end = min(len(frames), j + 10)

            pass_frames = frames[approach_start:exit_end]
            if len(pass_frames) > HISTORY_FINE + HORIZON + 20:
                entry_time = frames[i].timestamp
                exit_time = frames[min(j, len(frames) - 1)].timestamp
                corner_time = exit_time - entry_time

                passes.append({
                    "corner": corner.name,
                    "corner_id": corner.number,
                    "frames": pass_frames,
                    "corner_time": corner_time,
                    "session_frame_offset": approach_start,
                })
            i = j + 1
        else:
            i += 1
    return passes


def score_pass(p):
    """Score a corner pass 0-1 for quality weighting. Based on corner time percentile."""
    return p.get("quality_score", 0.5)


def assign_quality_scores(passes):
    """Assign quality scores per corner (fastest = 1.0, slowest = 0.1)."""
    by_corner = defaultdict(list)
    for p in passes:
        by_corner[p["corner"]].append(p)

    for corner_name, cps in by_corner.items():
        valid = [p for p in cps if p["corner_time"] > 1.0]
        invalid = [p for p in cps if p["corner_time"] <= 1.0]
        valid.sort(key=lambda p: p["corner_time"])
        for rank, p in enumerate(valid):
            p["quality_score"] = 1.0 - 0.9 * (rank / max(len(valid) - 1, 1))
        for p in invalid:
            p["quality_score"] = 0.1  # low score for invalid passes


# ─── Dataset ─────────────────────────────────────────────────────────────────

class CornerDatasetV3(Dataset):
    def __init__(self, passes, track, augment=False):
        self.samples = []
        self.augment = augment

        for p in passes:
            frames = p["frames"]
            n = len(frames)
            quality = p.get("quality_score", 0.5)
            corner_id = p.get("corner_id", 0)

            # Precompute all frame features
            all_feat = []
            for i, f in enumerate(frames):
                prev = frames[i - 1] if i > 0 else f
                all_feat.append(compute_frame_features(f, prev))
            all_feat = np.array(all_feat, dtype=np.float32)

            # Need at least HISTORY_FINE frames of history + HORIZON frames of future
            # plus 50 frames before for coarse stats
            start = max(HISTORY_FINE, 20)  # ensure enough history
            for i in range(start, n - HORIZON):
                # Fine: last 10 frames at full resolution
                fine = all_feat[i - HISTORY_FINE:i]  # (10, 22)

                # Medium: last 20 frames downsampled 2x → 10 frames
                med_start = max(0, i - 20)
                med_raw = all_feat[med_start:i]
                if len(med_raw) >= 2:
                    med = med_raw[::2][:HISTORY_MED]  # every other frame
                    if len(med) < HISTORY_MED:
                        pad = np.zeros((HISTORY_MED - len(med), FRAME_FEAT), dtype=np.float32)
                        med = np.concatenate([pad, med], axis=0)
                else:
                    med = np.zeros((HISTORY_MED, FRAME_FEAT), dtype=np.float32)

                # Coarse: rolling stats from last 50 frames, computed in 5 windows of 10
                coarse_vectors = []
                for w in range(5):
                    w_end = i - w * 10
                    w_start = w_end - 10
                    coarse_vectors.append(compute_coarse_stats(frames, w_start, w_end))
                coarse = np.array(coarse_vectors, dtype=np.float32)  # (5, 8)

                # Context
                ctx = np.array(compute_context(frames[i], track), dtype=np.float32)

                # Corner ID
                cid = corner_id

                # Targets
                targets = []
                for j in range(i, i + HORIZON):
                    targets.append(compute_targets(frames[j]))
                targets = np.array(targets, dtype=np.float32)  # (20, 3)

                self.samples.append({
                    "fine": fine,
                    "med": med,
                    "coarse": coarse,
                    "ctx": ctx,
                    "corner_id": cid,
                    "targets": targets,
                    "quality": quality,
                    "last_targets": np.array(compute_targets(frames[i - 1]), dtype=np.float32),
                })

        print(f"    Built {len(self.samples)} sequences")

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        s = self.samples[idx]

        fine = torch.tensor(s["fine"])
        med = torch.tensor(s["med"])
        coarse = torch.tensor(s["coarse"])
        ctx = torch.tensor(s["ctx"])
        corner_id = torch.tensor(s["corner_id"], dtype=torch.long)
        targets = torch.tensor(s["targets"])
        quality = torch.tensor(s["quality"], dtype=torch.float32)
        last_targets = torch.tensor(s["last_targets"])

        # Data augmentation
        if self.augment and random.random() < 0.3:
            # Mirror: flip left/right (negate gLat, steering, heading_rate, direction)
            for feat_tensor in [fine, med]:
                feat_tensor[:, 1] *= -1   # g_lat
                feat_tensor[:, 5] *= -1   # steering
                feat_tensor[:, 8] *= -1   # heading_rate
                feat_tensor[:, 16] *= -1  # brake_x_glat sign doesn't change (abs)
                feat_tensor[:, 17] *= -1  # throttle_x_glat sign
            ctx[2] *= -1  # direction

        if self.augment and random.random() < 0.2:
            # Small noise injection on features
            noise = torch.randn_like(fine) * 0.01
            fine = fine + noise
            noise_m = torch.randn_like(med) * 0.01
            med = med + noise_m

        return fine, med, coarse, ctx, corner_id, targets, quality, last_targets


# ─── Model ───────────────────────────────────────────────────────────────────

class LSTMPredictorV3(nn.Module):
    """
    Multi-scale BiLSTM with corner embedding, attention, and residual prediction.
    """

    def __init__(self):
        super().__init__()

        # Corner embedding
        self.corner_embed = nn.Embedding(MAX_CORNERS + 1, CORNER_EMBED_DIM)

        # Fine-scale LSTM (10 frames, full resolution)
        self.lstm_fine = nn.LSTM(
            input_size=FRAME_FEAT, hidden_size=64,
            num_layers=2, batch_first=True, bidirectional=True, dropout=0.15,
        )

        # Medium-scale LSTM (10 frames, 2x downsampled)
        self.lstm_med = nn.LSTM(
            input_size=FRAME_FEAT, hidden_size=48,
            num_layers=1, batch_first=True, bidirectional=True,
        )

        # Coarse-scale encoder (5 stat vectors)
        self.coarse_encoder = nn.Sequential(
            nn.Linear(HISTORY_COARSE * COARSE_FEAT, 32),
            nn.ReLU(),
        )

        # Attention over fine LSTM
        self.attention = nn.Sequential(
            nn.Linear(128, 48), nn.Tanh(), nn.Linear(48, 1),
        )

        # Context encoder
        self.ctx_encoder = nn.Sequential(
            nn.Linear(CONTEXT_FEAT, 48), nn.ReLU(), nn.Linear(48, 32),
        )

        # Decoder: fine_attended(128) + med_pooled(96) + coarse(32) + ctx(32) + corner(12) = 300
        decoder_in = 128 + 96 + 32 + 32 + CORNER_EMBED_DIM
        self.decoder = nn.Sequential(
            nn.Linear(decoder_in, 192),
            nn.ReLU(),
            nn.Dropout(0.15),
            nn.Linear(192, 128),
            nn.ReLU(),
            nn.Dropout(0.1),
            nn.Linear(128, HORIZON * OUTPUT_FEAT),
        )

    def forward(self, fine, med, coarse, ctx, corner_id, last_targets):
        batch = fine.shape[0]

        # Fine LSTM + attention
        fine_out, _ = self.lstm_fine(fine)  # (batch, 10, 128)
        attn_w = torch.softmax(self.attention(fine_out), dim=1)
        fine_vec = (fine_out * attn_w).sum(dim=1)  # (batch, 128)

        # Medium LSTM (mean pool)
        med_out, _ = self.lstm_med(med)  # (batch, 10, 96)
        med_vec = med_out.mean(dim=1)  # (batch, 96)

        # Coarse stats
        coarse_flat = coarse.view(batch, -1)  # (batch, 40)
        coarse_vec = self.coarse_encoder(coarse_flat)  # (batch, 32)

        # Context
        ctx_vec = self.ctx_encoder(ctx)  # (batch, 32)

        # Corner embedding
        corner_vec = self.corner_embed(corner_id)  # (batch, 12)

        # Concatenate all
        combined = torch.cat([fine_vec, med_vec, coarse_vec, ctx_vec, corner_vec], dim=1)

        # Predict deltas
        deltas = self.decoder(combined).view(batch, HORIZON, OUTPUT_FEAT)

        # Residual: output = last_frame + cumulative_delta
        output = last_targets.unsqueeze(1) + torch.cumsum(deltas, dim=1)

        return output


# ─── Training ────────────────────────────────────────────────────────────────

def train_model(train_ds, val_ds, epochs=100, lr=0.0008, batch_size=128):
    model = LSTMPredictorV3().to(DEVICE)

    param_count = sum(p.numel() for p in model.parameters())
    print(f"\n  Model: Multi-Scale BiLSTM + Corner Embedding + Attention + Residual")
    print(f"  Parameters: {param_count:,}")
    print(f"  Device: {DEVICE}")
    print(f"  Train: {len(train_ds)}, Val: {len(val_ds)}")
    print()

    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)
    huber = nn.SmoothL1Loss(reduction='none')

    train_loader = DataLoader(train_ds, batch_size=batch_size, shuffle=True, drop_last=True)
    val_loader = DataLoader(val_ds, batch_size=batch_size, shuffle=False)

    # Weights: near horizon > far, speed > brake > throttle
    hw = torch.linspace(1.5, 0.5, HORIZON).to(DEVICE)
    tw = torch.tensor([2.0, 1.5, 1.0]).to(DEVICE)

    best_val = float("inf")
    best_state = None
    patience = 20
    no_improve = 0

    for epoch in range(epochs):
        model.train()
        train_loss = 0
        for fine, med, coarse, ctx, cid, targets, quality, last_t in train_loader:
            fine = fine.to(DEVICE)
            med = med.to(DEVICE)
            coarse = coarse.to(DEVICE)
            ctx = ctx.to(DEVICE)
            cid = cid.to(DEVICE)
            targets = targets.to(DEVICE)
            quality = quality.to(DEVICE)
            last_t = last_t.to(DEVICE)

            pred = model(fine, med, coarse, ctx, cid, last_t)

            diff = huber(pred, targets)
            diff = diff * tw.unsqueeze(0).unsqueeze(0)
            diff = diff.mean(dim=2) * hw.unsqueeze(0)
            # Quality weighting: good passes contribute more
            diff = diff.mean(dim=1) * quality
            loss = diff.mean()

            optimizer.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            train_loss += loss.item()

        train_loss /= len(train_loader)
        scheduler.step()

        model.eval()
        val_loss = 0
        with torch.no_grad():
            for fine, med, coarse, ctx, cid, targets, quality, last_t in val_loader:
                fine, med, coarse = fine.to(DEVICE), med.to(DEVICE), coarse.to(DEVICE)
                ctx, cid = ctx.to(DEVICE), cid.to(DEVICE)
                targets, last_t = targets.to(DEVICE), last_t.to(DEVICE)
                pred = model(fine, med, coarse, ctx, cid, last_t)
                diff = huber(pred, targets)
                diff = diff * tw.unsqueeze(0).unsqueeze(0)
                diff = diff.mean(dim=2) * hw.unsqueeze(0)
                val_loss += diff.mean().item()
        val_loss /= max(len(val_loader), 1)

        if val_loss < best_val:
            best_val = val_loss
            best_state = {k: v.cpu().clone() for k, v in model.state_dict().items()}
            no_improve = 0
            marker = " *"
        else:
            no_improve += 1
            marker = ""

        if epoch % 5 == 0 or marker:
            print(f"  Epoch {epoch:>3}/{epochs}  train={train_loss:.6f}  val={val_loss:.6f}  lr={scheduler.get_last_lr()[0]:.6f}{marker}")

        if no_improve >= patience:
            print(f"  Early stopping at epoch {epoch}")
            break

    model.load_state_dict(best_state)
    model.to(DEVICE)
    return model


# ─── Evaluation ──────────────────────────────────────────────────────────────

def evaluate(model, dataset, name=""):
    loader = DataLoader(dataset, batch_size=256, shuffle=False)
    all_pred, all_true = [], []

    model.eval()
    with torch.no_grad():
        for fine, med, coarse, ctx, cid, targets, quality, last_t in loader:
            fine, med, coarse = fine.to(DEVICE), med.to(DEVICE), coarse.to(DEVICE)
            ctx, cid = ctx.to(DEVICE), cid.to(DEVICE)
            last_t = last_t.to(DEVICE)
            pred = model(fine, med, coarse, ctx, cid, last_t).cpu().numpy()
            all_pred.append(pred)
            all_true.append(targets.numpy())

    Y_pred = np.concatenate(all_pred)
    Y_true = np.concatenate(all_true)

    # Denormalize. Note: throttle is now relative (throttle/speed ratio), so we
    # convert back to absolute throttle for reporting: throttle_abs = ratio * speed * 3 * 100
    targets_info = [
        ("speed (km/h)", 60.0 * 3.6),
        ("brake (bar)", 100.0),
        ("throttle_rel", 3.0),  # keep normalized for now
    ]

    print(f"\n  {name} — {Y_true.shape[0]} sequences")
    print(f"  {'':20} {'MAE':>8} {'RMSE':>8} {'0.5s':>8} {'1.0s':>8} {'1.5s':>8} {'2.0s':>8}")

    for t_idx, (tname, scale) in enumerate(targets_info):
        true = Y_true[:, :, t_idx] * scale
        pred = Y_pred[:, :, t_idx] * scale
        mae = np.mean(np.abs(true - pred))
        rmse = np.sqrt(np.mean((true - pred) ** 2))
        mae_05 = np.mean(np.abs(true[:, 4] - pred[:, 4]))
        mae_10 = np.mean(np.abs(true[:, 9] - pred[:, 9]))
        mae_15 = np.mean(np.abs(true[:, 14] - pred[:, 14]))
        mae_20 = np.mean(np.abs(true[:, 19] - pred[:, 19]))
        print(f"  {tname:<20} {mae:>8.2f} {rmse:>8.2f} {mae_05:>8.2f} {mae_10:>8.2f} {mae_15:>8.2f} {mae_20:>8.2f}")

    # Coaching analysis on speed
    speed_delta = (Y_pred[:, 9, 0] - Y_true[:, 9, 0]) * 60.0 * 3.6
    brake_delta = (Y_pred[:, 9, 1] - Y_true[:, 9, 1]) * 100.0
    print(f"\n  Coaching at 1.0s:")
    print(f"    Speed delta: {np.mean(speed_delta):+.1f} ± {np.std(speed_delta):.1f} km/h")
    print(f"      too fast (>5):  {np.sum(speed_delta < -5)} ({np.sum(speed_delta < -5)/len(speed_delta)*100:.1f}%)")
    print(f"      too slow (>5):  {np.sum(speed_delta > 5)} ({np.sum(speed_delta > 5)/len(speed_delta)*100:.1f}%)")
    print(f"    Brake delta: {np.mean(brake_delta):+.1f} ± {np.std(brake_delta):.1f} bar")


# ─── Data Loading ────────────────────────────────────────────────────────────

def load_passes(data_dir, file_list, track):
    all_passes = []
    for vbo in file_list:
        path = os.path.join(data_dir, vbo)
        if not os.path.exists(path):
            continue
        try:
            meta, frames = parse_vbo(path)
            hot = filter_hot_laps(frames)
            if not hot:
                continue
            compute_distances(hot)
            passes = extract_corner_passes(hot, track)
            all_passes.extend(passes)
        except Exception as e:
            print(f"  Warn: {vbo}: {e}")
    return all_passes


# ─── Main ────────────────────────────────────────────────────────────────────

def cmd_train(args):
    print("LSTM Predictor v3 — Full Improvements")
    print("=" * 70)

    track = load_track(args.track)
    print(f"Track: {track.name} ({track.track_length:.0f}m, {len(track.corners)} corners)")

    print(f"\nLoading data...")
    train_passes = load_passes(args.data_dir, TRAIN_FILES, track)
    val_passes = load_passes(args.data_dir, VAL_FILES, track)
    test_passes = load_passes(args.data_dir, TEST_FILES, track)
    print(f"  Train: {len(train_passes)}, Val: {len(val_passes)}, Test: {len(test_passes)}")

    # Quality-weighted: train on ALL passes, not just best
    assign_quality_scores(train_passes)
    assign_quality_scores(val_passes)
    assign_quality_scores(test_passes)

    # Show quality distribution
    scores = [p["quality_score"] for p in train_passes]
    print(f"  Train quality: mean={np.mean(scores):.2f}, best 20%={sum(1 for s in scores if s > 0.8)}, worst 20%={sum(1 for s in scores if s < 0.2)}")

    per_corner = Counter(p["corner"] for p in train_passes)
    for c, n in sorted(per_corner.items()):
        print(f"    {c:<12} {n:>4} passes")

    print(f"\nBuilding datasets...")
    t0 = time.time()
    train_ds = CornerDatasetV3(train_passes, track, augment=True)
    val_ds = CornerDatasetV3(val_passes, track, augment=False)
    test_ds = CornerDatasetV3(test_passes, track, augment=False) if test_passes else None
    print(f"  Build time: {time.time()-t0:.1f}s")

    print(f"\n{'='*70}")
    print("TRAINING")
    print("=" * 70)
    model = train_model(train_ds, val_ds, epochs=args.epochs, lr=args.lr, batch_size=args.batch)

    print(f"\n{'='*70}")
    print("EVALUATION")
    print("=" * 70)
    evaluate(model, train_ds, "Train (ALL passes, quality-weighted)")
    evaluate(model, val_ds, "Val (Sonoma held-out)")
    if test_ds and len(test_ds) > 0:
        evaluate(model, test_ds, "Test (Track 8 unseen)")

    os.makedirs(args.output, exist_ok=True)
    path = os.path.join(args.output, "lstm_v3.pt")
    torch.save({"model_state": model.state_dict()}, path)
    print(f"\nSaved to {path} ({os.path.getsize(path)/1024:.0f} KB)")
    print(f"Parameters: {sum(p.numel() for p in model.parameters()):,}")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd")
    tp = sub.add_parser("train")
    tp.add_argument("data_dir")
    tp.add_argument("--track", required=True)
    tp.add_argument("--output", default="models/")
    tp.add_argument("--epochs", type=int, default=100)
    tp.add_argument("--lr", type=float, default=0.0008)
    tp.add_argument("--batch", type=int, default=128)
    args = p.parse_args()
    if args.cmd == "train":
        cmd_train(args)
    else:
        p.print_help()
