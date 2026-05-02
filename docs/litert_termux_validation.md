# LiteRT-LM on Termux — Validation Runbook

This runbook is the **on-device smoke test** that confirms the LiteRT-LM coaching stack actually loads and runs on the target hardware (a Pixel running Termux). The architectural decisions in [ADR-012](adr/012-coach-engine-adapter.md) ("commit to LiteRT-LM via MediaPipe Genai") and [ADR-013](adr/013-frontend-backend-boundary.md) ("backend owns LLM inference") rest on this stack working in Termux. A `PASS` from this runbook is the gate for treating those decisions as load-bearing.

## Pre-requisites

- Pixel device (any 8/9/10 generation; 12+ GB RAM preferred so the `.task` file fits comfortably).
- Termux installed from F-Droid (the Play Store version is outdated — do not use it).
- `Termux:API` companion app installed if you also want voice output during the test (optional).
- USB cable + a desktop with `adb` if you want to push the model from disk rather than `wget` it directly on the device.
- ~5 GB free space.

## Step 1 — Install Python + MediaPipe in Termux

On the Pixel, in Termux:

```bash
# Termux base packages
pkg update
pkg install python git

# MediaPipe (this includes mediapipe.tasks.python.genai.inference)
pip install --upgrade pip
pip install mediapipe
```

If `pip install mediapipe` fails — log the exact error and stop. The most common failure modes are:

- **Architecture mismatch**: pip should pull an `aarch64` wheel; if it tries to compile from source you're missing a wheel for your Python version. Try `python3.11` or check `pip install --only-binary=:all: mediapipe`.
- **Bionic libc symbol mismatches**: some MediaPipe builds expect glibc symbols that Termux's bionic doesn't have. Workaround: try `pip install --upgrade mediapipe` (newer builds have better Termux support) or open an issue on the MediaPipe repo.

## Step 2 — Push the Gemma 4 LiteRT-LM model

Download `gemma-4-E2B-it.task` from the official LiteRT community repo: <https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm>.

You can either `wget` it directly on the device (slow on metered cellular) or `adb push` it from a desktop:

```bash
# On the Pixel, in Termux:
mkdir -p ~/storage/shared/Pitwall/models/

# Option A — download on device:
cd ~/storage/shared/Pitwall/models/
wget https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.task

# Option B — push from desktop (faster on Wi-Fi or USB):
# (on desktop with adb)
adb push gemma-4-E2B-it.task /sdcard/Pitwall/models/
```

Either path lands the file at `~/storage/shared/Pitwall/models/gemma-4-E2B-it.task` from Termux's point of view (Termux's `~/storage/shared` is symlinked to `/sdcard`).

## Step 3 — Get the validation script

```bash
# In Termux, from your home dir
git clone https://github.com/<your-fork>/pitwall.git
cd pitwall
```

Or if you don't want the whole repo, copy just the script:

```bash
curl -O https://raw.githubusercontent.com/<your-fork>/pitwall/master/tools/validate_litert.py
```

## Step 4 — Run it

```bash
python3 tools/validate_litert.py
```

The script runs five stages and prints pass/fail for each:

1. `mediapipe` import — confirms the package is installed.
2. `mediapipe.tasks.python.genai.inference` import — confirms the Genai task is in this build.
3. Locate the `.task` file at the expected paths.
4. `LlmInference.create_from_options()` — loads the model. Records load time.
5. `generate_response(prompt)` — runs a single inference. Records latency.

Expected output on success looks like:

```
============================================================
  STEP 1 — mediapipe import
============================================================
  ✓  mediapipe imported (version 0.10.x)
============================================================
  STEP 2 — mediapipe.tasks.python.genai
============================================================
  ✓  mediapipe.tasks.python.genai.inference imported
============================================================
  STEP 3 — locate Gemma 4 .task file
============================================================
  ✓  found ~/storage/shared/Pitwall/models/gemma-4-E2B-it.task  (1850.0 MB)
============================================================
  STEP 4 — construct LlmInference engine
============================================================
  ✓  engine constructed in 4.31s
============================================================
  STEP 5 — run a single inference
============================================================
  ✓  response in 0.42s (78 chars)

  >>> Calamity Corner — wait for the bump, third tire stack apex.

============================================================
  PASS
============================================================
  mediapipe :  installed
  Genai     :  importable
  model     :  gemma-4-E2B-it.task (1850.0 MB)
  load time :  4.31s
  gen time  :  0.42s
  hot-path budget (50 ms): OVER — review --max-tokens or model size
```

## Step 5 — Decide based on the result

### `PASS` with gen time < 50 ms

The architecture in ADR-012 + ADR-013 is validated. Proceed to wire the bridge with `--coach litert`:

```bash
python3 -m src.pitwall --coach litert \
    --litert-model ~/storage/shared/Pitwall/models/gemma-4-E2B-it.task
```

### `PASS` with gen time 50–500 ms

LiteRT works but the hot-path budget (<50 ms) is exceeded. Acceptable for the warm-path use cases (pre-brief, post-session debrief — both are non-realtime). Hot-path pace notes will need to fall back to `RuleCoach` per-burst, OR you can lower `--max-tokens` aggressively (12 tokens fits in pace-note budget). Document the latency in `docs/architecture.md` and revisit in Phase 4.

### `PASS` with gen time > 500 ms

The TPU isn't actually being used — MediaPipe likely fell back to CPU XNNPack. Two options: live with it for the May 23 demo (warm-path-only coaching), OR get the AICore / Gemini Nano path going on the Pixel side, which uses the Tensor TPU directly via Android system APIs (not Python). The AICore path requires Kotlin and contradicts ADR-013's "backend owns inference" — flag it in ADR-013 if this is the route taken.

### `FAIL` at step 1 or 2

MediaPipe doesn't load in Termux. ADR-012's commitment to on-device LiteRT is invalidated. Open an ADR amendment proposing one of:

- Cloud Gemini Nano via the Pixel's AICore (Kotlin-only, contradicts ADR-013 — move LLM back to Kotlin).
- Hosted Gemini API with explicit network requirement (contradicts the offline goal).
- Stay with `RuleCoach` only and ship the templated coach voice for May 23 (still useful — RuleCoach already produces marker-aware pace notes per `markers.md`).

### `FAIL` at step 3

Model file missing. Re-run step 2.

### `FAIL` at step 4 or 5

The model loads but doesn't run. Most common cause: MediaPipe version mismatch with the `.task` file format. Try `pip install --upgrade mediapipe` and re-download a fresher `.task` from the LiteRT community repo.

## Step 6 — Report back

Whatever the outcome, paste the script's full output into a short report. Include:

- Pixel model + RAM
- Android version
- Termux + Python version
- `pip show mediapipe | head -3`
- `df -h ~/storage/shared` (just to confirm storage)
- The validation script output verbatim

Push the report to `docs/litert_termux_run_<date>.md` so we have a record of the validation result for the May 23 sprint review.

## Why this matters

Three architectural decisions hang on this validation:

- [ADR-012](adr/012-coach-engine-adapter.md) committed to on-device LiteRT-LM via MediaPipe Genai, removing the OpenAI-compat / llama.cpp fallback. Without a `PASS` here, that decision needs amendment.
- [ADR-013](adr/013-frontend-backend-boundary.md) committed to backend-owned inference (`LitertCoach` runs in Python). Without a `PASS`, the migration list needs to revert to "Kotlin keeps the LiteRT call".
- [ADR-014](adr/014-sonoma-as-the-product.md) Phase 1 + 2 endpoints (`/coach/brief`, `/coach/debrief`, `/analyze` with `pace_note`) all assume the Python coach can produce LLM output. Without a `PASS`, those endpoints fall back to `RuleCoach` (templated) — still useful, but not the LLM voice.

A failure here is not a project-killer — `RuleCoach` is shipped and produces marker-aware pace notes today. But it does change the headline sprint deliverable from "Gemma 4 on-device coach" to "templated rally co-driver with markers", which is worth knowing before May 23, not on the day.
