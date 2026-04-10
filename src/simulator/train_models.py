"""
Train all ML models on VBO telemetry data.

Split strategy (held-out, not random):
  Train: Sonoma (80% of sessions) + Track 2  — 301K frames
  Val:   Sonoma (20% of sessions)            — 52K frames
  Test:  Track 8 (entirely unseen track)     — 96K frames

Models:
  1. Phase Classifier (XGBoost, 6 classes)
  2. Brake Point Predictor (Linear Regression)
  3. Lap Time Predictor (Linear Regression per sector)
  4. Corner Performance Scorer (Weighted formula)
  5. Driving Style Fingerprint (K-Means)

Usage:
    python train_models.py /path/to/data/
"""

import argparse
import json
import math
import os
import pickle
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

from vbo_parser import parse_vbo, TelemetryFrame
from track_builder import compute_distances, filter_hot_laps, haversine


# ─── Config ──────────────────────────────────────────────────────────────────

TRAIN_FILES_SONOMA = [
    "Sonoma Intermediate - 1_47.5.vbo", "VBOX0167.vbo", "VBOX0173.vbo",
    "VBOX0181.vbo", "VBOX0188.vbo", "VBOX0196.vbo", "VBOX0202.vbo",
    "VBOX0208.vbo", "VBOX0212.vbo", "VBOX0217.vbo", "VBOX0218.vbo",
    "VBOX0220.vbo", "VBOX0226.vbo", "VBOX0229.vbo", "VBOX0230.vbo",
    "VBOX0233.vbo", "VBOX0237.vbo",
]

TRAIN_FILES_TRACK2 = [
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

def label_phase(f: TelemetryFrame) -> str:
    """Label a frame's driving phase from telemetry."""
    brake = f.brake_pressure
    glat = abs(f.g_lat)
    glong = f.g_long
    throttle = f.throttle
    speed_kmh = f.speed * 3.6

    if speed_kmh < 20:
        return "pit"
    if brake > 3 and glat > 0.4:
        return "trail_brake"
    if brake > 5:
        return "braking"
    if glat > 0.4 and throttle > 20:
        return "cornering_power"
    if glat > 0.4:
        return "cornering_coast"
    if throttle > 50 and glat < 0.3:
        return "straight"
    if throttle < 10 and brake < 2 and speed_kmh > 30:
        return "coasting"
    return "transition"


def extract_frame_features(f: TelemetryFrame) -> list[float]:
    """Extract features for phase classifier."""
    return [
        f.speed,                    # m/s
        f.g_lat,                    # G
        f.g_long,                   # G
        f.combo_g,                  # G
        f.brake_pressure,           # bar
        f.throttle,                 # %
        abs(f.steering) / 500.0,    # normalized steering (clip wraps)
        f.rpm / 9000.0,             # normalized RPM
    ]


def load_frames(data_dir: str, file_list: list[str]) -> list[TelemetryFrame]:
    """Load and filter hot lap frames from a list of VBO files."""
    all_frames = []
    for vbo in file_list:
        path = os.path.join(data_dir, vbo)
        if not os.path.exists(path):
            continue
        try:
            meta, frames = parse_vbo(path)
            hot = filter_hot_laps(frames)
            if hot:
                compute_distances(hot)
                all_frames.extend(hot)
        except Exception as e:
            print(f"  Warning: {vbo}: {e}")
    return all_frames


# ─── Model 1: Phase Classifier ──────────────────────────────────────────────

def train_phase_classifier(train_frames, val_frames, test_frames):
    """Train XGBoost phase classifier."""
    print("\n" + "=" * 60)
    print("MODEL 1: Phase Classifier (XGBoost)")
    print("=" * 60)

    # Build feature matrices
    X_train = np.array([extract_frame_features(f) for f in train_frames])
    y_train = np.array([label_phase(f) for f in train_frames])

    X_val = np.array([extract_frame_features(f) for f in val_frames])
    y_val = np.array([label_phase(f) for f in val_frames])

    X_test = np.array([extract_frame_features(f) for f in test_frames])
    y_test = np.array([label_phase(f) for f in test_frames])

    # Label distribution
    print(f"\nTrain: {len(X_train)} frames")
    for phase, count in sorted(Counter(y_train).items(), key=lambda x: -x[1]):
        print(f"  {phase:<20} {count:>7} ({count / len(y_train) * 100:5.1f}%)")

    # Encode labels
    classes = sorted(set(y_train) | set(y_val) | set(y_test))
    class_to_idx = {c: i for i, c in enumerate(classes)}
    idx_to_class = {i: c for c, i in class_to_idx.items()}

    y_train_enc = np.array([class_to_idx[y] for y in y_train])
    y_val_enc = np.array([class_to_idx[y] for y in y_val])
    y_test_enc = np.array([class_to_idx[y] for y in y_test])

    try:
        from sklearn.ensemble import GradientBoostingClassifier
        from sklearn.metrics import accuracy_score, classification_report

        print(f"\nTraining GradientBoosting (n_estimators=200, max_depth=5)...")
        t0 = time.time()
        model = GradientBoostingClassifier(
            n_estimators=200, max_depth=5, learning_rate=0.1,
            subsample=0.8, min_samples_leaf=50,
        )
        model.fit(X_train, y_train_enc)
        train_time = time.time() - t0
        print(f"  Training time: {train_time:.1f}s")

        # Evaluate
        for name, X, y_enc, y_labels in [
            ("Val (Sonoma held-out)", X_val, y_val_enc, y_val),
            ("Test (Track 8 unseen)", X_test, y_test_enc, y_test),
        ]:
            y_pred = model.predict(X)
            acc = accuracy_score(y_enc, y_pred)
            print(f"\n{name}: accuracy = {acc:.4f}")

            # Per-class accuracy
            pred_labels = np.array([idx_to_class[p] for p in y_pred])
            for cls in classes:
                mask = y_labels == cls
                if mask.sum() > 0:
                    cls_acc = (pred_labels[mask] == y_labels[mask]).mean()
                    print(f"  {cls:<20} {cls_acc:.3f}  (n={mask.sum()})")

        # Feature importance
        feature_names = ["speed", "g_lat", "g_long", "combo_g", "brake", "throttle", "steering", "rpm"]
        importances = model.feature_importances_
        print(f"\nFeature importance:")
        for name, imp in sorted(zip(feature_names, importances), key=lambda x: -x[1]):
            print(f"  {name:<12} {imp:.4f} {'█' * int(imp * 50)}")

        return model, classes, class_to_idx

    except ImportError:
        print("  scikit-learn not available. Using simple rule-based fallback.")
        return None, classes, class_to_idx


# ─── Model 2: Brake Point Predictor ─────────────────────────────────────────

def extract_brake_events(frames: list[TelemetryFrame]) -> list[dict]:
    """Find braking events: approach speed → brake initiation distance."""
    events = []
    in_brake = False
    brake_start_idx = 0

    for i, f in enumerate(frames):
        if f.brake_pressure > 5 and not in_brake:
            in_brake = True
            brake_start_idx = i
            # Look back for approach speed (speed at 200m before)
            approach_speed = f.speed
            for j in range(i - 1, max(0, i - 200), -1):
                if frames[j].brake_pressure < 2 and frames[j].speed > approach_speed:
                    approach_speed = frames[j].speed
                    break

            events.append({
                "approach_speed": approach_speed,
                "brake_speed": f.speed,
                "peak_pressure": f.brake_pressure,
                "g_long_at_brake": f.g_long,
                "distance": f.distance,
            })
        elif f.brake_pressure < 2 and in_brake:
            in_brake = False
            # Update peak pressure
            if events:
                peak = max(frames[j].brake_pressure for j in range(brake_start_idx, i))
                events[-1]["peak_pressure"] = peak
                events[-1]["brake_duration_m"] = f.distance - events[-1]["distance"]

    return events


def train_brake_predictor(train_frames, val_frames, test_frames):
    """Train brake point predictor: approach_speed → brake_distance."""
    print("\n" + "=" * 60)
    print("MODEL 2: Brake Point Predictor (Linear Regression)")
    print("=" * 60)

    train_events = extract_brake_events(train_frames)
    val_events = extract_brake_events(val_frames)
    test_events = extract_brake_events(test_frames)

    # Filter to meaningful braking events (>20 bar peak, >20m duration)
    def filter_events(events):
        return [e for e in events if e.get("peak_pressure", 0) > 20 and e.get("brake_duration_m", 0) > 20]

    train_events = filter_events(train_events)
    val_events = filter_events(val_events)
    test_events = filter_events(test_events)

    print(f"  Braking events: train={len(train_events)}, val={len(val_events)}, test={len(test_events)}")

    if len(train_events) < 10:
        print("  Not enough braking events for training.")
        return None

    # Features: approach speed, peak pressure
    X_train = np.array([[e["approach_speed"], e["peak_pressure"]] for e in train_events])
    y_train = np.array([e.get("brake_duration_m", 50) for e in train_events])

    X_val = np.array([[e["approach_speed"], e["peak_pressure"]] for e in val_events])
    y_val = np.array([e.get("brake_duration_m", 50) for e in val_events])

    X_test = np.array([[e["approach_speed"], e["peak_pressure"]] for e in test_events])
    y_test = np.array([e.get("brake_duration_m", 50) for e in test_events])

    try:
        from sklearn.linear_model import LinearRegression
        from sklearn.metrics import mean_absolute_error, r2_score

        model = LinearRegression()
        model.fit(X_train, y_train)

        for name, X, y in [("Val", X_val, y_val), ("Test", X_test, y_test)]:
            if len(X) == 0:
                continue
            pred = model.predict(X)
            mae = mean_absolute_error(y, pred)
            r2 = r2_score(y, pred)
            print(f"\n  {name}: MAE = {mae:.1f}m, R² = {r2:.3f}")
            # Show some predictions
            for i in range(min(5, len(X))):
                print(f"    speed={X[i][0]*3.6:.0f}km/h, peak={X[i][1]:.0f}bar → "
                      f"actual={y[i]:.0f}m, predicted={pred[i]:.0f}m")

        print(f"\n  Coefficients: speed={model.coef_[0]:.4f}, pressure={model.coef_[1]:.4f}, intercept={model.intercept_:.1f}")
        print(f"  Interpretation: each m/s faster adds {model.coef_[0]:.2f}m to brake zone")

        return model

    except ImportError:
        print("  scikit-learn not available.")
        return None


# ─── Model 3: Driving Style Fingerprint ──────────────────────────────────────

def extract_style_features(frames: list[TelemetryFrame], window_size=100) -> list[list[float]]:
    """Extract driving style features from rolling windows."""
    features = []
    for i in range(0, len(frames) - window_size, window_size // 2):
        window = frames[i:i + window_size]

        speeds = [f.speed for f in window]
        glats = [abs(f.g_lat) for f in window]
        glongs = [f.g_long for f in window]
        brakes = [f.brake_pressure for f in window]
        throttles = [f.throttle for f in window]
        steerings = [abs(f.steering) for f in window]
        combos = [f.combo_g for f in window]

        # Skip low-speed windows (pit/transit)
        if max(speeds) * 3.6 < 60:
            continue

        features.append([
            np.mean(speeds),            # avg speed
            np.std(speeds),             # speed variation
            np.max(glats),              # peak lateral G
            np.mean(glats),             # avg lateral G
            np.max([-g for g in glongs]),  # peak braking G
            np.mean(brakes),            # avg brake pressure
            np.max(brakes),             # peak brake pressure
            np.mean(throttles),         # avg throttle
            np.std(throttles),          # throttle variation
            np.mean(steerings),         # avg steering magnitude
            np.std(steerings),          # steering variation (smoothness)
            np.mean(combos),            # avg grip usage
            # Trail brake indicator: frames with brake > 3 AND glat > 0.4
            sum(1 for j in range(len(window))
                if window[j].brake_pressure > 3 and abs(window[j].g_lat) > 0.4) / len(window),
            # Coasting indicator: frames with throttle < 10 AND brake < 2
            sum(1 for j in range(len(window))
                if window[j].throttle < 10 and window[j].brake_pressure < 2
                and window[j].speed * 3.6 > 30) / len(window),
        ])

    return features


def train_style_fingerprint(train_frames, val_frames, test_frames):
    """Train K-Means clustering for driving style fingerprinting."""
    print("\n" + "=" * 60)
    print("MODEL 5: Driving Style Fingerprint (K-Means)")
    print("=" * 60)

    train_feat = extract_style_features(train_frames)
    val_feat = extract_style_features(val_frames)
    test_feat = extract_style_features(test_frames)

    print(f"  Feature windows: train={len(train_feat)}, val={len(val_feat)}, test={len(test_feat)}")

    if len(train_feat) < 20:
        print("  Not enough data.")
        return None

    X_train = np.array(train_feat)
    X_val = np.array(val_feat) if val_feat else np.array([])
    X_test = np.array(test_feat) if test_feat else np.array([])

    try:
        from sklearn.cluster import KMeans
        from sklearn.preprocessing import StandardScaler

        scaler = StandardScaler()
        X_train_scaled = scaler.fit_transform(X_train)

        # Try k=4 archetypes
        km = KMeans(n_clusters=4, random_state=42, n_init=10)
        km.fit(X_train_scaled)

        train_labels = km.labels_

        # Characterize each cluster
        feature_names = [
            "avg_speed", "speed_std", "peak_glat", "avg_glat",
            "peak_brake_g", "avg_brake", "peak_brake", "avg_throttle",
            "throttle_std", "avg_steer", "steer_std", "avg_grip",
            "trail_brake_pct", "coast_pct",
        ]

        print(f"\n  Cluster profiles (4 archetypes):")
        for c in range(4):
            mask = train_labels == c
            n = mask.sum()
            means = X_train[mask].mean(axis=0)
            print(f"\n  Cluster {c} ({n} windows, {n / len(train_labels) * 100:.0f}%):")
            for fname, val in zip(feature_names, means):
                print(f"    {fname:<18} {val:.3f}")

            # Name the archetype
            avg_brake = means[5]
            peak_glat = means[2]
            coast_pct = means[13]
            trail_pct = means[12]

            if avg_brake > np.median(X_train[:, 5]) and peak_glat > np.median(X_train[:, 2]):
                archetype = "Aggressive"
            elif trail_pct > np.median(X_train[:, 12]):
                archetype = "Trail Braker"
            elif coast_pct > np.median(X_train[:, 13]):
                archetype = "Cautious / Coaster"
            else:
                archetype = "Balanced"
            print(f"    → Archetype: {archetype}")

        # Predict on val and test
        if len(X_val) > 0:
            X_val_scaled = scaler.transform(X_val)
            val_labels = km.predict(X_val_scaled)
            print(f"\n  Val cluster distribution: {dict(Counter(val_labels))}")

        if len(X_test) > 0:
            X_test_scaled = scaler.transform(X_test)
            test_labels = km.predict(X_test_scaled)
            print(f"  Test cluster distribution: {dict(Counter(test_labels))}")

        return km, scaler

    except ImportError:
        print("  scikit-learn not available.")
        return None


# ─── Model 4: Corner Scorer ─────────────────────────────────────────────────

def compute_corner_scores(frames: list[TelemetryFrame], track_json_path: str = None):
    """Score corner performance — no ML needed, weighted formula against session best."""
    print("\n" + "=" * 60)
    print("MODEL 4: Corner Performance Scorer (Formula-Based)")
    print("=" * 60)

    # Without track JSON, use a simpler approach:
    # Find high-gLat segments (corners) and score by min speed and smoothness

    # Detect corner passes: contiguous regions where |gLat| > 0.4G
    corner_passes = []
    in_corner = False
    corner_frames = []

    for f in frames:
        if abs(f.g_lat) > 0.4:
            if not in_corner:
                in_corner = True
                corner_frames = []
            corner_frames.append(f)
        elif in_corner:
            in_corner = False
            if len(corner_frames) > 10:  # at least 1 second
                speeds = [cf.speed for cf in corner_frames]
                glats = [abs(cf.g_lat) for cf in corner_frames]
                brakes = [cf.brake_pressure for cf in corner_frames]
                throttles = [cf.throttle for cf in corner_frames]
                steerings = [abs(cf.steering) for cf in corner_frames]

                corner_passes.append({
                    "entry_speed": corner_frames[0].speed * 3.6,
                    "min_speed": min(speeds) * 3.6,
                    "exit_speed": corner_frames[-1].speed * 3.6,
                    "max_glat": max(glats),
                    "duration": len(corner_frames) * 0.1,
                    "steering_std": float(np.std(steerings)) if steerings else 0,
                    "trail_brake_frames": sum(1 for i in range(len(corner_frames))
                                              if corner_frames[i].brake_pressure > 3),
                })

    if not corner_passes:
        print("  No corners detected.")
        return

    print(f"  Detected {len(corner_passes)} corner passes")

    # Score each corner against the session's best values
    best_entry = max(cp["entry_speed"] for cp in corner_passes)
    best_min = max(cp["min_speed"] for cp in corner_passes)
    best_exit = max(cp["exit_speed"] for cp in corner_passes)
    best_glat = max(cp["max_glat"] for cp in corner_passes)
    min_duration = min(cp["duration"] for cp in corner_passes)
    min_steer_std = min(cp["steering_std"] for cp in corner_passes) if any(cp["steering_std"] > 0 for cp in corner_passes) else 1

    scores = []
    for cp in corner_passes:
        # Weighted scoring (0-100)
        entry_score = min(100, cp["entry_speed"] / best_entry * 100) * 0.15
        min_score = min(100, cp["min_speed"] / best_min * 100) * 0.20
        exit_score = min(100, cp["exit_speed"] / best_exit * 100) * 0.25
        glat_score = min(100, cp["max_glat"] / best_glat * 100) * 0.15
        time_score = min(100, min_duration / cp["duration"] * 100) * 0.15
        smooth_score = min(100, min_steer_std / max(cp["steering_std"], 0.1) * 100) * 0.10
        total = entry_score + min_score + exit_score + glat_score + time_score + smooth_score
        scores.append(total)

    scores_arr = np.array(scores)
    print(f"  Corner scores: mean={scores_arr.mean():.1f}, std={scores_arr.std():.1f}, "
          f"min={scores_arr.min():.1f}, max={scores_arr.max():.1f}")
    print(f"  Distribution: <50={sum(s < 50 for s in scores)}, "
          f"50-70={sum(50 <= s < 70 for s in scores)}, "
          f"70-85={sum(70 <= s < 85 for s in scores)}, "
          f"85+={sum(s >= 85 for s in scores)}")

    return scores


# ─── Main ────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Train ML models on VBO data")
    parser.add_argument("data_dir", help="Directory containing .vbo files")
    parser.add_argument("--output", default="models/", help="Output directory for models")
    args = parser.parse_args()

    os.makedirs(args.output, exist_ok=True)

    print("Pitwall ML Model Training")
    print("=" * 60)
    print(f"Data directory: {args.data_dir}")
    print(f"Output: {args.output}")

    # Load data
    print(f"\nLoading training data (Sonoma + Track 2)...")
    train_frames = load_frames(args.data_dir, TRAIN_FILES_SONOMA + TRAIN_FILES_TRACK2)
    print(f"  {len(train_frames)} train frames")

    print(f"Loading validation data (Sonoma held-out)...")
    val_frames = load_frames(args.data_dir, VAL_FILES)
    print(f"  {len(val_frames)} val frames")

    print(f"Loading test data (Track 8 — unseen track)...")
    test_frames = load_frames(args.data_dir, TEST_FILES)
    print(f"  {len(test_frames)} test frames")

    total = len(train_frames) + len(val_frames) + len(test_frames)
    print(f"\nTotal: {total} frames ({total / 10 / 60:.0f} min)")
    print(f"Split: train {len(train_frames)/total*100:.0f}% / val {len(val_frames)/total*100:.0f}% / test {len(test_frames)/total*100:.0f}%")

    # Train models
    results = {}

    # Model 1: Phase Classifier
    phase_result = train_phase_classifier(train_frames, val_frames, test_frames)
    if phase_result[0]:
        model, classes, class_map = phase_result
        with open(os.path.join(args.output, "phase_classifier.pkl"), "wb") as f:
            pickle.dump({"model": model, "classes": classes, "class_map": class_map}, f)
        results["phase_classifier"] = "trained"

    # Model 2: Brake Point Predictor
    brake_model = train_brake_predictor(train_frames, val_frames, test_frames)
    if brake_model:
        with open(os.path.join(args.output, "brake_predictor.pkl"), "wb") as f:
            pickle.dump(brake_model, f)
        results["brake_predictor"] = "trained"

    # Model 4: Corner Scorer (no ML, but computes on all splits)
    print("\nScoring corners per split:")
    for name, frames in [("Train", train_frames), ("Val", val_frames), ("Test", test_frames)]:
        print(f"\n  --- {name} ---")
        compute_corner_scores(frames)

    # Model 5: Style Fingerprint
    style_result = train_style_fingerprint(train_frames, val_frames, test_frames)
    if style_result:
        km, scaler = style_result
        with open(os.path.join(args.output, "style_fingerprint.pkl"), "wb") as f:
            pickle.dump({"kmeans": km, "scaler": scaler}, f)
        results["style_fingerprint"] = "trained"

    # Summary
    print("\n" + "=" * 60)
    print("TRAINING COMPLETE")
    print("=" * 60)
    for name, status in results.items():
        print(f"  {name:<25} {status}")
    print(f"\nModels saved to {args.output}/")


if __name__ == "__main__":
    main()
