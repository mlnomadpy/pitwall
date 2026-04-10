"""
Sequence Predictor — predicts next 2 seconds of driving from last 3 seconds.

Trained on the best 20% of corner passes per track. The gap between
predicted and actual IS the coaching signal.

Usage:
    # Train
    python sequence_predictor.py train /path/to/data/ --track sonoma.json --output models/

    # Evaluate
    python sequence_predictor.py eval /path/to/data/ --track sonoma.json --model models/seq_predictor.pkl
"""

import argparse
import math
import os
import pickle
import sys
import time
from collections import defaultdict
from pathlib import Path

import numpy as np

from vbo_parser import parse_vbo, TelemetryFrame
from track_builder import compute_distances, filter_hot_laps, haversine
from track_loader import load_track, is_in_corner, find_nearest_corner, distance_to_corner


# ─── Config ──────────────────────────────────────────────────────────────────

HISTORY_FRAMES = 30     # 3 seconds at 10Hz
PREDICT_FRAMES = 20     # 2 seconds at 10Hz
FRAME_FEATURES = 8      # per-timestep features
CONTEXT_FEATURES = 5    # track context (constant across window)
OUTPUT_FEATURES = 3     # speed, brake, throttle

# Held-out split (same as train_models.py)
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

VAL_FILES = [
    "VBOX0240.vbo", "VBOX0245.vbo", "VBOX0248.vbo",
    "VBOX0253.vbo", "VBOX0260.vbo",
]

TEST_FILES = [
    "VBOX0266.vbo", "VBOX0268.vbo", "VBOX0269.vbo", "VBOX0271.vbo",
    "VBOX0275.vbo", "VBOX0276.vbo", "VBOX0279.vbo", "VBOX0288.vbo",
    "VBOX0290.vbo", "VBOX0293.vbo",
]


# ─── Feature Extraction ─────────────────────────────────────────────────────

def extract_frame_features(f, prev_f=None):
    """Extract 8 features from one telemetry frame."""
    # Heading rate: degrees/second
    heading_rate = 0.0
    if prev_f is not None:
        dh = f.heading - prev_f.heading
        if dh > 180: dh -= 360
        elif dh < -180: dh += 360
        heading_rate = dh / 0.1  # 10Hz → dt = 0.1s

    return [
        f.speed / 60.0,                         # normalized (~0-1 for 0-216 km/h)
        f.g_lat / 2.0,                          # normalized (~-1 to 1)
        f.g_long / 2.0,                         # normalized (~-1 to 1)
        min(f.brake_pressure / 100.0, 1.0),     # normalized 0-1
        f.throttle / 100.0,                     # already 0-100 → 0-1
        min(abs(f.steering) / 400.0, 1.0),      # normalized, clipped
        min(f.combo_g / 2.5, 1.0),              # normalized
        heading_rate / 50.0,                     # normalized (~-1 to 1)
    ]


def extract_track_context(f, track):
    """Extract 5 track context features for a frame."""
    next_c = find_nearest_corner(track, f.distance)
    corner = is_in_corner(track, f.distance)

    dist_to_next = distance_to_corner(track, f.distance, next_c) / track.track_length if next_c else 1.0
    severity = next_c.severity / 6.0 if next_c else 0.0
    direction = 0.0
    if next_c:
        direction = 1.0 if next_c.direction == "right" else -1.0

    # Elevation gradient (approximate from altitude change)
    elev_grad = 0.0  # would need adjacent frames

    # Distance in corner (0-1, -1 if not in corner)
    dist_in_corner = -1.0
    if corner:
        total = corner.exit_distance - corner.entry_distance
        if total > 0:
            pos = (f.distance % track.track_length) - corner.entry_distance
            dist_in_corner = max(0, min(1, pos / total))

    return [dist_to_next, severity, direction, elev_grad, dist_in_corner]


def extract_targets(f):
    """Extract 3 prediction targets from one frame."""
    return [
        f.speed / 60.0,
        min(f.brake_pressure / 100.0, 1.0),
        f.throttle / 100.0,
    ]


# ─── Corner Pass Extraction ─────────────────────────────────────────────────

def extract_corner_passes(frames, track):
    """
    Extract all corner passes: approach (3s before entry) through exit (+1s).
    Returns list of (corner_name, frames_list) tuples.
    """
    passes = []
    i = 0
    while i < len(frames):
        corner = is_in_corner(track, frames[i].distance)
        if corner:
            # Found corner entry — look back for approach
            approach_start = max(0, i - 30)  # 3 seconds before

            # Find corner exit
            j = i
            while j < len(frames) and is_in_corner(track, frames[j].distance) == corner:
                j += 1

            # Include 1 second after exit
            exit_end = min(len(frames), j + 10)

            pass_frames = frames[approach_start:exit_end]
            if len(pass_frames) > HISTORY_FRAMES + PREDICT_FRAMES:
                # Compute corner time (entry to exit)
                entry_time = frames[i].timestamp
                exit_time = frames[min(j, len(frames)-1)].timestamp
                corner_time = exit_time - entry_time

                passes.append({
                    "corner": corner.name,
                    "frames": pass_frames,
                    "corner_time": corner_time,
                    "entry_speed": frames[i].speed,
                    "min_speed": min(f.speed for f in frames[i:j]),
                    "exit_speed": frames[min(j, len(frames)-1)].speed,
                })

            i = j + 1  # skip past this corner
        else:
            i += 1

    return passes


def select_best_passes(passes, top_pct=0.20):
    """Select the fastest passes per corner (top 20% by corner time)."""
    by_corner = defaultdict(list)
    for p in passes:
        by_corner[p["corner"]].append(p)

    best = []
    for corner_name, corner_passes in by_corner.items():
        # Sort by corner time (fastest first), filter out outliers
        valid = [p for p in corner_passes if p["corner_time"] > 1.0]
        valid.sort(key=lambda p: p["corner_time"])
        n_best = max(1, int(len(valid) * top_pct))
        best.extend(valid[:n_best])

    return best


# ─── Sequence Dataset ────────────────────────────────────────────────────────

def build_sequences(passes, track):
    """
    Build training sequences from corner passes.
    Each sequence: (X_history, X_context, Y_future)
    """
    X_hist = []
    X_ctx = []
    Y_fut = []

    for p in passes:
        frames = p["frames"]
        n = len(frames)

        for i in range(HISTORY_FRAMES, n - PREDICT_FRAMES):
            # History: last 30 frames
            hist = []
            for j in range(i - HISTORY_FRAMES, i):
                prev = frames[j - 1] if j > 0 else None
                hist.append(extract_frame_features(frames[j], prev))

            # Context: track features at current frame
            ctx = extract_track_context(frames[i], track)

            # Future: next 20 frames (targets)
            fut = []
            for j in range(i, i + PREDICT_FRAMES):
                fut.append(extract_targets(frames[j]))

            X_hist.append(hist)
            X_ctx.append(ctx)
            Y_fut.append(fut)

    return (
        np.array(X_hist, dtype=np.float32),
        np.array(X_ctx, dtype=np.float32),
        np.array(Y_fut, dtype=np.float32),
    )


# ─── Model ───────────────────────────────────────────────────────────────────

class SequencePredictor:
    """
    MLP-based sequence predictor.
    Flattens history + context → predicts future.

    Using MLP instead of LSTM for compatibility (no PyTorch/TF dependency).
    sklearn MLPRegressor handles the multi-output regression.
    """

    def __init__(self):
        self.model = None
        self.train_mean = None
        self.train_std = None

    def _flatten_input(self, X_hist, X_ctx):
        """Flatten (batch, 30, 8) + (batch, 5) → (batch, 245)."""
        batch = X_hist.shape[0]
        hist_flat = X_hist.reshape(batch, -1)  # (batch, 240)
        return np.concatenate([hist_flat, X_ctx], axis=1)  # (batch, 245)

    def _flatten_output(self, Y):
        """Flatten (batch, 20, 3) → (batch, 60)."""
        return Y.reshape(Y.shape[0], -1)

    def _unflatten_output(self, Y_flat):
        """Unflatten (batch, 60) → (batch, 20, 3)."""
        return Y_flat.reshape(-1, PREDICT_FRAMES, OUTPUT_FEATURES)

    def train(self, X_hist, X_ctx, Y_fut):
        """Train the model."""
        from sklearn.neural_network import MLPRegressor

        X = self._flatten_input(X_hist, X_ctx)
        Y = self._flatten_output(Y_fut)

        # Normalize inputs
        self.train_mean = X.mean(axis=0)
        self.train_std = X.std(axis=0) + 1e-8
        X_norm = (X - self.train_mean) / self.train_std

        print(f"  Training MLPRegressor on {X_norm.shape[0]} sequences...")
        print(f"  Input shape: {X_norm.shape}, Output shape: {Y.shape}")

        self.model = MLPRegressor(
            hidden_layer_sizes=(128, 64, 64),
            activation="relu",
            solver="adam",
            learning_rate_init=0.001,
            max_iter=100,
            batch_size=256,
            early_stopping=True,
            validation_fraction=0.1,
            n_iter_no_change=10,
            verbose=True,
        )

        t0 = time.time()
        self.model.fit(X_norm, Y)
        dt = time.time() - t0
        print(f"  Training time: {dt:.1f}s, iterations: {self.model.n_iter_}")

    def predict(self, X_hist, X_ctx):
        """Predict future frames."""
        X = self._flatten_input(X_hist, X_ctx)
        X_norm = (X - self.train_mean) / self.train_std
        Y_flat = self.model.predict(X_norm)
        return self._unflatten_output(Y_flat)

    def predict_single(self, history_frames, context):
        """Predict from a single sequence (not batched)."""
        X_h = np.array([history_frames], dtype=np.float32)
        X_c = np.array([context], dtype=np.float32)
        pred = self.predict(X_h, X_c)
        return pred[0]  # (20, 3)

    def save(self, path):
        with open(path, "wb") as f:
            pickle.dump({
                "model": self.model,
                "train_mean": self.train_mean,
                "train_std": self.train_std,
            }, f)
        print(f"  Model saved to {path}")

    def load(self, path):
        with open(path, "rb") as f:
            data = pickle.load(f)
        self.model = data["model"]
        self.train_mean = data["train_mean"]
        self.train_std = data["train_std"]
        print(f"  Model loaded from {path}")


# ─── Evaluation ──────────────────────────────────────────────────────────────

def evaluate(predictor, X_hist, X_ctx, Y_true, name=""):
    """Evaluate predictions vs ground truth."""
    Y_pred = predictor.predict(X_hist, X_ctx)

    # Denormalize for interpretable metrics
    # speed: × 60 → m/s, × 3.6 → km/h
    # brake: × 100 → bar
    # throttle: × 100 → %

    targets = ["speed (km/h)", "brake (bar)", "throttle (%)"]
    scales = [60.0 * 3.6, 100.0, 100.0]

    print(f"\n  {name} — {Y_true.shape[0]} sequences")
    print(f"  {'':20} {'MAE':>10} {'RMSE':>10} {'at 0.5s':>10} {'at 1.0s':>10} {'at 2.0s':>10}")

    for t_idx, (tname, scale) in enumerate(zip(targets, scales)):
        true = Y_true[:, :, t_idx] * scale
        pred = Y_pred[:, :, t_idx] * scale

        mae = np.mean(np.abs(true - pred))
        rmse = np.sqrt(np.mean((true - pred) ** 2))

        # MAE at specific horizons
        mae_05 = np.mean(np.abs(true[:, 4] - pred[:, 4]))     # 0.5s (frame 5)
        mae_10 = np.mean(np.abs(true[:, 9] - pred[:, 9]))     # 1.0s (frame 10)
        mae_20 = np.mean(np.abs(true[:, 19] - pred[:, 19]))   # 2.0s (frame 20)

        print(f"  {tname:<20} {mae:>10.2f} {rmse:>10.2f} {mae_05:>10.2f} {mae_10:>10.2f} {mae_20:>10.2f}")

    return Y_pred


def analyze_coaching_potential(Y_true, Y_pred):
    """Analyze how the prediction delta maps to coaching moments."""
    speed_scale = 60.0 * 3.6  # → km/h
    brake_scale = 100.0       # → bar
    throttle_scale = 100.0    # → %

    speed_delta = (Y_pred[:, :, 0] - Y_true[:, :, 0]) * speed_scale
    brake_delta = (Y_pred[:, :, 1] - Y_true[:, :, 1]) * brake_scale
    throttle_delta = (Y_pred[:, :, 2] - Y_true[:, :, 2]) * throttle_scale

    # At 1 second horizon
    sd = speed_delta[:, 9]
    bd = brake_delta[:, 9]
    td = throttle_delta[:, 9]

    print(f"\n  Coaching potential (delta at 1.0s horizon):")
    print(f"  Speed: mean delta {np.mean(sd):+.1f} km/h, std {np.std(sd):.1f}")
    print(f"    > 5 km/h too fast:  {np.sum(sd < -5):>5} sequences ({np.sum(sd < -5)/len(sd)*100:.1f}%) → 'arriving hot'")
    print(f"    > 5 km/h too slow:  {np.sum(sd > 5):>5} sequences ({np.sum(sd > 5)/len(sd)*100:.1f}%) → 'you have more pace'")
    print(f"  Brake: mean delta {np.mean(bd):+.1f} bar, std {np.std(bd):.1f}")
    print(f"    > 10 bar under-braking: {np.sum(bd < -10):>5} sequences → 'brake harder'")
    print(f"    > 10 bar over-braking:  {np.sum(bd > 10):>5} sequences → 'less brake'")
    print(f"  Throttle: mean delta {np.mean(td):+.1f}%, std {np.std(td):.1f}")
    print(f"    > 20% under-throttle: {np.sum(td < -20):>5} sequences → 'more throttle'")
    print(f"    > 20% over-throttle:  {np.sum(td > 20):>5} sequences → 'ease off'")


# ─── Data Loading ────────────────────────────────────────────────────────────

def load_passes(data_dir, file_list, track):
    """Load frames and extract corner passes."""
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
    print("Sequence Predictor — Training")
    print("=" * 60)

    track = load_track(args.track)
    print(f"Track: {track.name} ({track.track_length:.0f}m, {len(track.corners)} corners)")

    # Load corner passes
    print(f"\nLoading training data...")
    train_passes = load_passes(args.data_dir, TRAIN_FILES, track)
    print(f"  {len(train_passes)} corner passes from training files")

    print(f"Loading validation data...")
    val_passes = load_passes(args.data_dir, VAL_FILES, track)
    print(f"  {len(val_passes)} corner passes from val files")

    print(f"Loading test data...")
    test_passes = load_passes(args.data_dir, TEST_FILES, track)
    print(f"  {len(test_passes)} corner passes from test files")

    # Select best passes for training (top 20%)
    best_train = select_best_passes(train_passes, top_pct=0.20)
    print(f"\nBest training passes (top 20%): {len(best_train)}")

    # Also build sequences from ALL passes for comparison
    all_train = train_passes  # use all passes, not just best

    # Show corner distribution
    from collections import Counter
    corner_counts = Counter(p["corner"] for p in best_train)
    print("  Per corner:")
    for c, n in sorted(corner_counts.items()):
        print(f"    {c:<12} {n:>4} passes")

    # Build sequences
    print(f"\nBuilding training sequences from best passes...")
    X_h_train, X_c_train, Y_train = build_sequences(best_train, track)
    print(f"  Train: {X_h_train.shape[0]} sequences")

    print(f"Building validation sequences from ALL val passes...")
    X_h_val, X_c_val, Y_val = build_sequences(val_passes, track)
    print(f"  Val: {X_h_val.shape[0]} sequences")

    if test_passes:
        print(f"Building test sequences from ALL test passes...")
        X_h_test, X_c_test, Y_test = build_sequences(test_passes, track)
        print(f"  Test: {X_h_test.shape[0]} sequences")
    else:
        X_h_test = X_c_test = Y_test = None

    # Train
    print(f"\n{'=' * 60}")
    predictor = SequencePredictor()
    predictor.train(X_h_train, X_c_train, Y_train)

    # Evaluate
    print(f"\n{'=' * 60}")
    print("EVALUATION")
    print("=" * 60)

    evaluate(predictor, X_h_train, X_c_train, Y_train, "Train (best passes)")
    evaluate(predictor, X_h_val, X_c_val, Y_val, "Val (Sonoma held-out, ALL passes)")

    if X_h_test is not None and len(X_h_test) > 0:
        Y_pred_test = evaluate(predictor, X_h_test, X_c_test, Y_test, "Test (Track 8 unseen, ALL passes)")
        analyze_coaching_potential(Y_test, Y_pred_test)

    # Save
    os.makedirs(args.output, exist_ok=True)
    predictor.save(os.path.join(args.output, "seq_predictor.pkl"))

    # Also save metadata
    meta = {
        "history_frames": HISTORY_FRAMES,
        "predict_frames": PREDICT_FRAMES,
        "frame_features": FRAME_FEATURES,
        "context_features": CONTEXT_FEATURES,
        "output_features": OUTPUT_FEATURES,
        "train_sequences": int(X_h_train.shape[0]),
        "val_sequences": int(X_h_val.shape[0]),
        "test_sequences": int(X_h_test.shape[0]) if X_h_test is not None else 0,
        "train_passes": len(best_train),
        "track": track.name,
    }
    import json
    with open(os.path.join(args.output, "seq_predictor_meta.json"), "w") as f:
        json.dump(meta, f, indent=2)


def cmd_eval(args):
    print("Sequence Predictor — Evaluation")
    print("=" * 60)

    track = load_track(args.track)
    predictor = SequencePredictor()
    predictor.load(args.model)

    # Load test data
    test_passes = load_passes(args.data_dir, TEST_FILES, track)
    X_h, X_c, Y = build_sequences(test_passes, track)

    Y_pred = evaluate(predictor, X_h, X_c, Y, "Test (Track 8)")
    analyze_coaching_potential(Y, Y_pred)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Sequence Predictor")
    sub = parser.add_subparsers(dest="cmd")

    train_p = sub.add_parser("train")
    train_p.add_argument("data_dir")
    train_p.add_argument("--track", required=True)
    train_p.add_argument("--output", default="models/")

    eval_p = sub.add_parser("eval")
    eval_p.add_argument("data_dir")
    eval_p.add_argument("--track", required=True)
    eval_p.add_argument("--model", required=True)

    args = parser.parse_args()
    if args.cmd == "train":
        cmd_train(args)
    elif args.cmd == "eval":
        cmd_eval(args)
    else:
        parser.print_help()
