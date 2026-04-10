"""
LSTM Sequence Predictor — predicts next 2 seconds from last 3 seconds.

Trained on best 20% of corner passes. The delta between prediction
and actual IS the coaching signal.

Features:
  - 16 per-frame features (raw + engineered)
  - 8 track context features
  - Bidirectional LSTM with attention

Usage:
    python lstm_predictor.py train /path/to/data/ --track sonoma.json
    python lstm_predictor.py eval /path/to/data/ --track sonoma.json --model models/lstm_predictor.pt
"""

import argparse
import json
import math
import os
import pickle
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

HISTORY = 30            # 3 seconds at 10Hz
HORIZON = 20            # 2 seconds prediction
FRAME_FEAT = 16         # per-timestep features
CONTEXT_FEAT = 8        # track context
OUTPUT_FEAT = 3         # speed, brake, throttle

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

def extract_features(frames, track):
    """
    Extract 16 per-frame features + 8 context features for a sequence of frames.
    Returns (N, 16) frame features and (N, 8) context features.
    """
    n = len(frames)
    feat = np.zeros((n, FRAME_FEAT), dtype=np.float32)
    ctx = np.zeros((n, CONTEXT_FEAT), dtype=np.float32)

    for i, f in enumerate(frames):
        prev = frames[i - 1] if i > 0 else f
        dt = 0.1  # 10Hz

        # ─── Raw signals (8) ─────────────────────────────────
        speed_norm = f.speed / 60.0                          # 0: ~0-1 for 0-216 km/h
        g_lat_norm = f.g_lat / 2.0                           # 1: ~-1 to 1
        g_long_norm = f.g_long / 2.0                         # 2: ~-1 to 1
        brake_norm = min(f.brake_pressure / 100.0, 1.0)      # 3: 0-1
        throttle_norm = f.throttle / 100.0                   # 4: 0-1
        steering_norm = max(-1, min(1, f.steering / 400.0))  # 5: -1 to 1 (clipped)
        combo_norm = min(f.combo_g / 2.5, 1.0)               # 6: 0-1
        rpm_norm = f.rpm / 9000.0                             # 7: 0-1

        # ─── Engineered features (8) ─────────────────────────

        # 8: heading rate (yaw rate proxy) — degrees/second normalized
        dh = f.heading - prev.heading
        if dh > 180: dh -= 360
        elif dh < -180: dh += 360
        heading_rate = (dh / dt) / 50.0  # ~-1 to 1

        # 9: speed derivative (acceleration) — m/s² normalized
        speed_dot = (f.speed - prev.speed) / dt / 15.0  # ~-1 to 1

        # 10: brake derivative (brake application rate) — bar/s normalized
        brake_dot = (f.brake_pressure - prev.brake_pressure) / dt / 200.0

        # 11: throttle derivative — %/s normalized
        throttle_dot = (f.throttle - prev.throttle) / dt / 300.0

        # 12: steering rate — deg/s normalized (smoothness indicator)
        steer_dot = (f.steering - prev.steering) / dt / 500.0

        # 13: friction circle utilization — combo_g / max observed
        friction_pct = min(f.combo_g / 2.29, 1.5)

        # 14: brake-steer overlap — trail brake indicator (continuous)
        trail_indicator = 0.0
        if f.brake_pressure > 3 and abs(f.g_lat) > 0.3:
            trail_indicator = min(f.brake_pressure / 50.0, 1.0) * min(abs(f.g_lat) / 1.5, 1.0)

        # 15: coast indicator — neither braking nor on throttle
        coast_indicator = 0.0
        if f.throttle < 10 and f.brake_pressure < 2 and f.speed > 10:
            coast_indicator = 1.0 - max(f.throttle / 10.0, f.brake_pressure / 2.0)

        feat[i] = [
            speed_norm, g_lat_norm, g_long_norm, brake_norm,
            throttle_norm, steering_norm, combo_norm, rpm_norm,
            heading_rate, speed_dot, brake_dot, throttle_dot,
            steer_dot, friction_pct, trail_indicator, coast_indicator,
        ]

        # ─── Track context (8) ───────────────────────────────
        next_c = find_nearest_corner(track, f.distance)
        corner = is_in_corner(track, f.distance)
        past = is_past_apex(track, f.distance)

        # 0: distance to next corner entry (normalized by track length)
        d2c = distance_to_corner(track, f.distance, next_c) / track.track_length if next_c else 1.0

        # 1: corner severity (0-1)
        severity = next_c.severity / 6.0 if next_c else 0.0

        # 2: corner direction (-1 left, 0 none, +1 right)
        direction = 0.0
        if next_c:
            direction = 1.0 if next_c.direction == "right" else -1.0

        # 3: distance within current corner (0=entry, 1=exit, -1=not in corner)
        dist_in_corner = -1.0
        if corner:
            total = corner.exit_distance - corner.entry_distance
            if total > 0:
                pos = (f.distance % track.track_length) - corner.entry_distance
                dist_in_corner = max(0, min(1, pos / total))

        # 4: past apex flag (0 or 1)
        past_apex = 1.0 if past else 0.0

        # 5: elevation at current position (normalized)
        elev = get_elevation(track, f.distance)
        elev_norm = (elev - 20.0) / 40.0  # rough normalization for Sonoma (8-56m)

        # 6: expected brake zone flag — are we in the typical brake zone for next corner?
        in_brake_zone = 0.0
        if next_c and next_c.brake_distance > 0:
            d = distance_to_corner(track, f.distance, next_c)
            if 0 < d < next_c.brake_distance:
                in_brake_zone = 1.0 - (d / next_c.brake_distance)

        # 7: track position (0-1 around the lap)
        track_pos = (f.distance % track.track_length) / track.track_length

        ctx[i] = [d2c, severity, direction, dist_in_corner,
                  past_apex, elev_norm, in_brake_zone, track_pos]

    return feat, ctx


def extract_targets(frames):
    """Extract 3 prediction targets per frame."""
    n = len(frames)
    targets = np.zeros((n, OUTPUT_FEAT), dtype=np.float32)
    for i, f in enumerate(frames):
        targets[i] = [
            f.speed / 60.0,
            min(f.brake_pressure / 100.0, 1.0),
            f.throttle / 100.0,
        ]
    return targets


# ─── Corner Pass Extraction ──────────────────────────────────────────────────

def extract_corner_passes(frames, track):
    """Extract approach-through-exit sequences for each corner."""
    passes = []
    i = 0
    while i < len(frames):
        corner = is_in_corner(track, frames[i].distance)
        if corner:
            approach_start = max(0, i - HISTORY)
            j = i
            while j < len(frames) and is_in_corner(track, frames[j].distance) == corner:
                j += 1
            exit_end = min(len(frames), j + 10)

            pass_frames = frames[approach_start:exit_end]
            if len(pass_frames) > HISTORY + HORIZON:
                entry_time = frames[i].timestamp
                exit_time = frames[min(j, len(frames) - 1)].timestamp
                corner_time = exit_time - entry_time
                passes.append({
                    "corner": corner.name,
                    "frames": pass_frames,
                    "corner_time": corner_time,
                })
            i = j + 1
        else:
            i += 1
    return passes


def select_best_passes(passes, top_pct=0.20):
    """Top 20% by corner time per corner."""
    by_corner = defaultdict(list)
    for p in passes:
        by_corner[p["corner"]].append(p)

    best = []
    for name, cps in by_corner.items():
        valid = [p for p in cps if p["corner_time"] > 1.0]
        valid.sort(key=lambda p: p["corner_time"])
        n = max(1, int(len(valid) * top_pct))
        best.extend(valid[:n])
    return best


# ─── Dataset ─────────────────────────────────────────────────────────────────

class CornerDataset(Dataset):
    def __init__(self, passes, track):
        self.X_hist = []
        self.X_ctx = []
        self.Y = []

        for p in passes:
            frames = p["frames"]
            feat, ctx = extract_features(frames, track)
            targets = extract_targets(frames)
            n = len(frames)

            for i in range(HISTORY, n - HORIZON):
                self.X_hist.append(feat[i - HISTORY:i])         # (30, 16)
                self.X_ctx.append(ctx[i])                       # (8,)
                self.Y.append(targets[i:i + HORIZON])           # (20, 3)

        self.X_hist = torch.tensor(np.array(self.X_hist), dtype=torch.float32)
        self.X_ctx = torch.tensor(np.array(self.X_ctx), dtype=torch.float32)
        self.Y = torch.tensor(np.array(self.Y), dtype=torch.float32)

    def __len__(self):
        return len(self.X_hist)

    def __getitem__(self, idx):
        return self.X_hist[idx], self.X_ctx[idx], self.Y[idx]


# ─── Model ───────────────────────────────────────────────────────────────────

class LSTMPredictor(nn.Module):
    """
    Bidirectional LSTM with context injection, temporal attention,
    and residual connection (predicts DELTA from last frame, not absolute values).

    Input:  history (batch, 30, 16) + context (batch, 8)
    Output: future  (batch, 20, 3)  — predicted as last_frame + cumulative_delta
    """

    def __init__(self, frame_feat=FRAME_FEAT, ctx_feat=CONTEXT_FEAT,
                 hidden=96, num_layers=2, output_feat=OUTPUT_FEAT, horizon=HORIZON):
        super().__init__()

        self.hidden = hidden
        self.horizon = horizon
        self.output_feat = output_feat

        # Bidirectional LSTM over history
        self.lstm = nn.LSTM(
            input_size=frame_feat,
            hidden_size=hidden,
            num_layers=num_layers,
            batch_first=True,
            bidirectional=True,
            dropout=0.2,
        )

        # Temporal attention over LSTM outputs
        self.attention = nn.Sequential(
            nn.Linear(hidden * 2, 64),
            nn.Tanh(),
            nn.Linear(64, 1),
        )

        # Context encoder
        self.ctx_encoder = nn.Sequential(
            nn.Linear(ctx_feat, 48),
            nn.ReLU(),
            nn.Linear(48, 48),
        )

        # Decoder: attended LSTM output + context → future DELTAS
        self.decoder = nn.Sequential(
            nn.Linear(hidden * 2 + 48, 160),
            nn.ReLU(),
            nn.Dropout(0.15),
            nn.Linear(160, 128),
            nn.ReLU(),
            nn.Dropout(0.1),
            nn.Linear(128, horizon * output_feat),
        )

    def forward(self, x_hist, x_ctx):
        # x_hist: (batch, 30, 16)
        # x_ctx: (batch, 8)

        # Extract last frame's target values for residual connection
        # speed=feat[0], brake=feat[3], throttle=feat[4] from the last history frame
        last_frame = x_hist[:, -1, :]  # (batch, 16)
        last_targets = torch.stack([
            last_frame[:, 0],  # speed (normalized)
            last_frame[:, 3],  # brake (normalized)
            last_frame[:, 4],  # throttle (normalized)
        ], dim=1)  # (batch, 3)

        # LSTM
        lstm_out, _ = self.lstm(x_hist)  # (batch, 30, hidden*2)

        # Attention weights
        attn_scores = self.attention(lstm_out)  # (batch, 30, 1)
        attn_weights = torch.softmax(attn_scores, dim=1)  # (batch, 30, 1)

        # Weighted sum of LSTM outputs
        context_vec = (lstm_out * attn_weights).sum(dim=1)  # (batch, hidden*2)

        # Encode track context
        ctx_vec = self.ctx_encoder(x_ctx)  # (batch, 48)

        # Concatenate and decode → predict DELTAS from last frame
        combined = torch.cat([context_vec, ctx_vec], dim=1)  # (batch, hidden*2 + 48)
        deltas = self.decoder(combined)  # (batch, horizon * output_feat)
        deltas = deltas.view(-1, self.horizon, self.output_feat)  # (batch, 20, 3)

        # Residual: prediction = last_frame_values + cumulative_delta
        # This anchors predictions to the current state — model only needs to learn changes
        output = last_targets.unsqueeze(1) + torch.cumsum(deltas, dim=1)

        return output


# ─── Training ────────────────────────────────────────────────────────────────

def train_model(train_dataset, val_dataset, epochs=80, lr=0.001, batch_size=128):
    model = LSTMPredictor().to(DEVICE)
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)

    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True, drop_last=True)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)

    # Weighted loss: penalize near-future more (coaching needs accurate short-term)
    horizon_weights = torch.linspace(1.5, 0.5, HORIZON).to(DEVICE)  # (20,) — near > far
    target_weights = torch.tensor([2.0, 1.5, 1.0]).to(DEVICE)  # speed > brake > throttle
    huber = nn.SmoothL1Loss(reduction='none')  # Huber loss — robust to outliers

    best_val_loss = float("inf")
    best_state = None
    patience = 15
    no_improve = 0

    print(f"\n  Model: BiLSTM(16→96×2 layers) + Attention + Context(8→32) + Decoder(224→128→96→60)")
    print(f"  Parameters: {sum(p.numel() for p in model.parameters()):,}")
    print(f"  Device: {DEVICE}")
    print(f"  Train: {len(train_dataset)} sequences, Val: {len(val_dataset)} sequences")
    print(f"  Batch size: {batch_size}, Epochs: {epochs}, LR: {lr}")
    print()

    for epoch in range(epochs):
        # Train
        model.train()
        train_loss = 0
        for X_h, X_c, Y in train_loader:
            X_h, X_c, Y = X_h.to(DEVICE), X_c.to(DEVICE), Y.to(DEVICE)

            pred = model(X_h, X_c)  # (batch, 20, 3)

            # Weighted Huber loss
            diff = huber(pred, Y)  # (batch, 20, 3)
            diff = diff * target_weights.unsqueeze(0).unsqueeze(0)
            diff = diff.mean(dim=2)  # (batch, 20)
            diff = diff * horizon_weights.unsqueeze(0)
            loss = diff.mean()

            optimizer.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            train_loss += loss.item()

        train_loss /= len(train_loader)
        scheduler.step()

        # Validate
        model.eval()
        val_loss = 0
        with torch.no_grad():
            for X_h, X_c, Y in val_loader:
                X_h, X_c, Y = X_h.to(DEVICE), X_c.to(DEVICE), Y.to(DEVICE)
                pred = model(X_h, X_c)
                diff = huber(pred, Y)
                diff = diff * target_weights.unsqueeze(0).unsqueeze(0)
                diff = diff.mean(dim=2) * horizon_weights.unsqueeze(0)
                val_loss += diff.mean().item()
        val_loss /= len(val_loader)

        # Early stopping
        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_state = {k: v.cpu().clone() for k, v in model.state_dict().items()}
            no_improve = 0
            marker = " ★"
        else:
            no_improve += 1
            marker = ""

        if epoch % 5 == 0 or marker:
            lr_now = scheduler.get_last_lr()[0]
            print(f"  Epoch {epoch:>3}/{epochs}  train={train_loss:.6f}  val={val_loss:.6f}  lr={lr_now:.6f}{marker}")

        if no_improve >= patience:
            print(f"  Early stopping at epoch {epoch}")
            break

    # Restore best
    model.load_state_dict(best_state)
    model.to(DEVICE)
    return model


# ─── Evaluation ──────────────────────────────────────────────────────────────

def evaluate(model, dataset, name=""):
    loader = DataLoader(dataset, batch_size=256, shuffle=False)
    all_pred = []
    all_true = []

    model.eval()
    with torch.no_grad():
        for X_h, X_c, Y in loader:
            X_h, X_c = X_h.to(DEVICE), X_c.to(DEVICE)
            pred = model(X_h, X_c).cpu().numpy()
            all_pred.append(pred)
            all_true.append(Y.numpy())

    Y_pred = np.concatenate(all_pred, axis=0)
    Y_true = np.concatenate(all_true, axis=0)

    targets = ["speed (km/h)", "brake (bar)", "throttle (%)"]
    scales = [60.0 * 3.6, 100.0, 100.0]

    print(f"\n  {name} — {Y_true.shape[0]} sequences")
    print(f"  {'':20} {'MAE':>8} {'RMSE':>8} {'0.5s':>8} {'1.0s':>8} {'1.5s':>8} {'2.0s':>8}")

    for t_idx, (tname, scale) in enumerate(zip(targets, scales)):
        true = Y_true[:, :, t_idx] * scale
        pred = Y_pred[:, :, t_idx] * scale

        mae = np.mean(np.abs(true - pred))
        rmse = np.sqrt(np.mean((true - pred) ** 2))
        mae_05 = np.mean(np.abs(true[:, 4] - pred[:, 4]))
        mae_10 = np.mean(np.abs(true[:, 9] - pred[:, 9]))
        mae_15 = np.mean(np.abs(true[:, 14] - pred[:, 14]))
        mae_20 = np.mean(np.abs(true[:, 19] - pred[:, 19]))

        print(f"  {tname:<20} {mae:>8.2f} {rmse:>8.2f} {mae_05:>8.2f} {mae_10:>8.2f} {mae_15:>8.2f} {mae_20:>8.2f}")

    # Coaching analysis
    speed_delta = (Y_pred[:, 9, 0] - Y_true[:, 9, 0]) * 60.0 * 3.6
    brake_delta = (Y_pred[:, 9, 1] - Y_true[:, 9, 1]) * 100.0
    throttle_delta = (Y_pred[:, 9, 2] - Y_true[:, 9, 2]) * 100.0

    print(f"\n  Coaching signals (1.0s horizon):")
    print(f"    Speed: {np.mean(speed_delta):+.1f} ± {np.std(speed_delta):.1f} km/h")
    print(f"      too fast (>5km/h):  {np.sum(speed_delta < -5)} ({np.sum(speed_delta < -5)/len(speed_delta)*100:.1f}%)")
    print(f"      too slow (>5km/h):  {np.sum(speed_delta > 5)} ({np.sum(speed_delta > 5)/len(speed_delta)*100:.1f}%)")
    print(f"    Brake: {np.mean(brake_delta):+.1f} ± {np.std(brake_delta):.1f} bar")
    print(f"    Throttle: {np.mean(throttle_delta):+.1f} ± {np.std(throttle_delta):.1f}%")

    return Y_pred, Y_true


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
            print(f"  Warning: {vbo}: {e}")
    return all_passes


# ─── Main ────────────────────────────────────────────────────────────────────

def cmd_train(args):
    print("LSTM Sequence Predictor — Training")
    print("=" * 70)

    track = load_track(args.track)
    print(f"Track: {track.name} ({track.track_length:.0f}m, {len(track.corners)} corners)")

    # Load passes
    print(f"\nLoading data...")
    train_passes = load_passes(args.data_dir, TRAIN_FILES, track)
    val_passes = load_passes(args.data_dir, VAL_FILES, track)
    test_passes = load_passes(args.data_dir, TEST_FILES, track)
    print(f"  Train: {len(train_passes)} passes, Val: {len(val_passes)}, Test: {len(test_passes)}")

    # Select best for training
    best_train = select_best_passes(train_passes, top_pct=0.20)
    print(f"  Best train passes (top 20%): {len(best_train)}")

    # Per-corner counts
    cc = Counter(p["corner"] for p in best_train)
    for c, n in sorted(cc.items()):
        print(f"    {c:<12} {n:>3} passes")

    # Build datasets
    print(f"\nBuilding datasets...")
    t0 = time.time()
    train_ds = CornerDataset(best_train, track)
    val_ds = CornerDataset(val_passes, track)
    test_ds = CornerDataset(test_passes, track) if test_passes else None
    print(f"  Train: {len(train_ds)}, Val: {len(val_ds)}, Test: {len(test_ds) if test_ds else 0}")
    print(f"  Dataset build time: {time.time() - t0:.1f}s")

    # Train
    print(f"\n{'=' * 70}")
    print("TRAINING")
    print("=" * 70)
    model = train_model(train_ds, val_ds, epochs=args.epochs, lr=args.lr, batch_size=args.batch)

    # Evaluate
    print(f"\n{'=' * 70}")
    print("EVALUATION")
    print("=" * 70)

    evaluate(model, train_ds, "Train (best passes)")
    evaluate(model, val_ds, "Val (Sonoma held-out, ALL passes)")
    if test_ds and len(test_ds) > 0:
        evaluate(model, test_ds, "Test (Track 8 unseen, ALL passes)")

    # Save
    os.makedirs(args.output, exist_ok=True)
    model_path = os.path.join(args.output, "lstm_predictor.pt")
    torch.save({
        "model_state": model.state_dict(),
        "config": {
            "frame_feat": FRAME_FEAT,
            "ctx_feat": CONTEXT_FEAT,
            "hidden": 96,
            "num_layers": 2,
            "output_feat": OUTPUT_FEAT,
            "horizon": HORIZON,
            "history": HISTORY,
        },
    }, model_path)
    print(f"\nModel saved to {model_path}")
    print(f"Size: {os.path.getsize(model_path) / 1024:.0f} KB")


def cmd_eval(args):
    track = load_track(args.track)
    checkpoint = torch.load(args.model, map_location=DEVICE)
    cfg = checkpoint["config"]

    model = LSTMPredictor(
        frame_feat=cfg["frame_feat"], ctx_feat=cfg["ctx_feat"],
        hidden=cfg["hidden"], num_layers=cfg["num_layers"],
    ).to(DEVICE)
    model.load_state_dict(checkpoint["model_state"])

    test_passes = load_passes(args.data_dir, TEST_FILES, track)
    test_ds = CornerDataset(test_passes, track)
    evaluate(model, test_ds, "Test (Track 8 unseen)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd")

    tp = sub.add_parser("train")
    tp.add_argument("data_dir")
    tp.add_argument("--track", required=True)
    tp.add_argument("--output", default="models/")
    tp.add_argument("--epochs", type=int, default=80)
    tp.add_argument("--lr", type=float, default=0.001)
    tp.add_argument("--batch", type=int, default=128)

    ep = sub.add_parser("eval")
    ep.add_argument("data_dir")
    ep.add_argument("--track", required=True)
    ep.add_argument("--model", required=True)

    args = parser.parse_args()
    if args.cmd == "train":
        cmd_train(args)
    elif args.cmd == "eval":
        cmd_eval(args)
    else:
        parser.print_help()
