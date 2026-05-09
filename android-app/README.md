# Pitwall Paddock (Jetpack)

### Standalone parallel APK (`pitwall-parallel`) — **bundled Vue PWA in WebView**

- **Application ID:** `com.pitwall.parallel` (side-by-side with Paddock `com.pitwall.paddock`).
- **UI:** The **exact** `vite build` output from [`src/pwa`](../../src/pwa) is packaged under `assets/pwa-www` and served at **`http://127.0.0.1:8765/`** so it matches the Vue app’s default `API_BASE` and the same port as `python3 -m src.pitwall` / Termux. The main screen is a fullscreen **WebView** (not a rewrite).
- **Embedded server:** Ktor copies assets to app storage, then serves **static files + SPA history fallback** and a **standalone Flask-shaped API** on the **same origin**: DuckDB (`filesDir/pitwall_embedded.duckdb`) for sessions, telemetry frames, coaching notes, lap rows, **Parquet export** for DuckDB-Wasm, `/insights`, coaching stubs, bundle proxies after `POST /coach/debrief`, **SSE** `/cues/stream`, and more — **no Python/Termux process** is required for normal use of this APK.
- **vs. desktop bridge:** The repo’s **Flask** service still has the richest behaviour (ADK, full track geometry analytics, VBO import, some edge routes). The embedded server covers the **PWA’s primary HTTP workflows** so the parallel APK is self-contained. For side-by-side testing with the Python bridge, set `PITWALL_USE_EMBEDDED_BRIDGE=false` and run Flask on **:8765** (see [docs/api.md](../../docs/api.md)); do not run both on the same port.
- **Build:** `cd src/pwa && npm ci && npm run build` then `./gradlew :pitwall-parallel:assembleDebug` (task `syncPwaDist` copies `../src/pwa/dist` into `assets/pwa-www` when the dist folder exists).
- **APK:** `pitwall-parallel/build/outputs/apk/debug/pitwall-parallel-debug.apk`

---

Native **Android (Jetpack Compose)** shell for the Sonoma pre-brief: **Google Maps** markers, **WebView** for a hosted/Three.js briefing, **pick-3** focus chips, and a live **on-track** view that uses **Play Services location** to show distance to your three focus corners. By default it calls the **Python Flask bridge** on your dev machine (same HTTP contract as the rest of the repo). Optionally, you can enable an **embedded Ktor** loopback server plus **MediaPipe GenAI** inside the APK for a parallel native stack (see below) without changing the Vue or Python sources.

## Requirements

- **Android Studio** Koala+ or a working command-line build with:
  - **Android SDK** Platform **35** (install via SDK Manager)
  - **JDK 17** for compiling the app. If you have **no** JDK 17, Gradle can **auto-download** one: the project includes the **Foojay Toolchains** convention (see `settings.gradle.kts`) — use **File → Sync Project with Gradle Files** and allow the download on first sync.
  - **Android Studio Gradle JDK:** In **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**, pick **jbr-17** / **17** (Embedded is often fine) so Studio’s JDK matches. If you still see *“Cannot find a Java installation … languageVersion=17”*, run `brew install openjdk@17` and set `JAVA_HOME` to the Homebrew `openjdk@17` path, or rely on the Foojay auto-download after sync.
- **New JREs only (e.g. 25 / 25.0.2):** the Gradle **runtime** for `gradlew` should be **17–23**; do not use the project’s Kotlin DSL on Java 25 without a separate JDK 17 for Android — using Studio with **jbr-17** avoids that.
- **Google Maps SDK key** (optional for map tiles): [Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/start) — enable the API, create an API key, restrict it to package `com.pitwall.paddock` and your **debug** SHA-1, then set `MAPS_API_KEY` in `local.properties` (see `local.properties.example`).

## First-time setup

1. Copy `local.properties.example` to `local.properties` and set `sdk.dir` to your Android SDK (e.g. `~/Library/Android/sdk` on macOS).
2. Add to `local.properties` as needed:
   - `MAPS_API_KEY=...` — without this, the **Map** tab shows a message instead of tiles.
   - `PITWALL_API_BASE_URL=...` — default is `http://10.0.2.2:8765` (emulator → host). For a device on the same Wi‑Fi, use your machine’s LAN IP.
   - **Embedded bridge (optional):** `PITWALL_USE_EMBEDDED_BRIDGE=true` binds an in-process **Ktor** server to `127.0.0.1:8765` and sets Retrofit’s effective base URL to that loopback address (see **Native parallel stack**).
   - **LLM `.task` path (optional):** `PITWALL_LLM_MODEL_PATH=/absolute/path/to/model.task` — used when the embedded bridge runs; if the file is missing, `/analyze` returns a stub string instead of loading MediaPipe.
3. Start the Python bridge from the repo root **unless** `PITWALL_USE_EMBEDDED_BRIDGE=true`: `python3 tools/pitwall_bridge.py` (or your team’s process).
4. Build: `./gradlew :app:assembleDebug` (set `JAVA_HOME` to JDK 17 if the build errors with a cryptic `IllegalArgumentException` and a version string).

## UI (tabs)

- **TRACK** — Sonoma map (cyan polyline, colored markers), marker tooltip + **VIEW** opens **marker detail** (replay block, Ross notes, entry/apex stats, **TACKLE** to add to your pick-3). **N SELECTED** → **Briefing**.
- **BRIEFING** — Pre-briefing summary cards (mock data from `DemoContent`) and **COMMENCE TRACK SESSION** → back to track.
- **RANKING** — Marker mastery leaderboard + Gemma insights (mock).
- **GARAGE** — Bridge health, effective API base URL, **Demo burst → /analyze** (Retrofit slice against `GET /health` + `POST /analyze`), **Post-race / session feedback** (expandable PITWALL-style module catalog: temp, track, pits, flags, tires, traffic, telemetry, laps, ideal lap, speed/corner, AI, multi-season, live timing — **stat hints** are chips for future API wiring), **location** card.
- **Post-session screen** (no bottom nav) — open from **Garage** or **Ranking**; back returns to the previous tab.

**Bottom navigation** — uses system **navigation bar insets** (not a fixed 64dp height) so labels are not clipped on gesture nav or edge-to-edge.

## Run

- **Android Studio:** Open the `android-app` folder, Run `app`.
- **CLI:** `./gradlew :app:installDebug` with a device or emulator connected.

## Package

- `applicationId`: `com.pitwall.paddock` — distinct from the Flutter app (`com.pitwall.app`).

## Native parallel stack (embedded Ktor + MediaPipe)

This is **additive**: the Vue PWA and Python Flask bridge in the repo root are unchanged. The `:pitwall-bridge-ktor` Android library embeds **Ktor (CIO)** on **`127.0.0.1:8765`** with static **`vite` output**, **`GET /health`**, and **`POST /analyze`** (not the full Flask route surface).

| Topic | Notes |
|--------|--------|
| **`pitwall-parallel`** | Full-screen **WebView** → `http://127.0.0.1:8765/` loads the **same** bundled `src/pwa/dist` as the Termux mental model (same port + `API_BASE`). No Retrofit in this module. |
| **`app` (Paddock)** | Garage demo still uses **Retrofit** against `BuildConfig.PITWALL_API_BASE_URL_EFFECTIVE` when you exercise `/health` + `/analyze` from Compose. |
| Enable | Set `PITWALL_USE_EMBEDDED_BRIDGE=true` in `local.properties` (and module-specific flags as documented in `pitwall-parallel/build.gradle.kts`). For Paddock, Retrofit then targets `http://127.0.0.1:8765/` when embedded is on. |
| Port conflict | Only **one** process can bind **8765**. Do **not** run the Python bridge at the same time as the embedded server. |
| MediaPipe `.task` | Set `PITWALL_LLM_MODEL_PATH` to an **absolute** path on device (large bundles are often sideloaded). Example dev workflow: `adb shell mkdir -p /data/local/tmp/llm` then `adb push gemma.task /data/local/tmp/llm/` and point `PITWALL_LLM_MODEL_PATH` at that file. **Physical hardware** is strongly recommended; emulators often lack reliable NPU/GPU paths for LLM inference. |
| Play Asset Delivery | Production-sized models exceed APK limits; ship the `.task` as an **asset pack** (install-time, fast-follow, or on-demand) and pass the mounted absolute path into the same property at runtime. |
| Persistence | **Room** stores each embedded `/analyze` result (`pitwall_parallel.db`). **DuckDB JDBC** is validated at startup with `SELECT 1` against `filesDir/pitwall_analytics.duckdb` (analytics parity spike). |

## Next steps (with Taha’s API)

- Replace `MockSonomaData` with `GET` map/marker JSON from the backend.
- `POST` pre-brief with `markers_selected` per ADR-014.
- Wire `OnTrack` to continuous location and cue logic when the bridge exposes the right payloads.

The existing Flutter stack under `flutter/` is unchanged; this module is a parallel **Jetpack** client for UX work on **Maps + WebView** and the Pixel.
