# Pitwall Paddock (Jetpack)

Native **Android (Jetpack Compose)** shell for the Sonoma pre-brief: **Google Maps** markers, **WebView** for a hosted/Three.js briefing, **pick-3** focus chips, and a live **on-track** view that uses **Play Services location** to show distance to your three focus corners. It calls the same **Pitwall HTTP bridge** as the Flutter app (`/health` today; extend as Taha adds endpoints).

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
3. Start the Python bridge from the repo root: `python3 tools/pitwall_bridge.py` (or your team’s process).
4. Build: `./gradlew :app:assembleDebug` (set `JAVA_HOME` to JDK 17 if the build errors with a cryptic `IllegalArgumentException` and a version string).

## UI (tabs)

- **TRACK** — Sonoma map (cyan polyline, colored markers), marker tooltip + **VIEW** opens **marker detail** (replay block, Ross notes, entry/apex stats, **TACKLE** to add to your pick-3). **N SELECTED** → **Briefing**.
- **BRIEFING** — Pre-briefing summary cards (mock data from `DemoContent`) and **COMMENCE TRACK SESSION** → back to track.
- **RANKING** — Marker mastery leaderboard + Gemma insights (mock).
- **GARAGE** — Bridge health, API base URL, **Post-race / session feedback** (expandable PITWALL-style module catalog: temp, track, pits, flags, tires, traffic, telemetry, laps, ideal lap, speed/corner, AI, multi-season, live timing — **stat hints** are chips for future API wiring), **location** card.
- **Post-session screen** (no bottom nav) — open from **Garage** or **Ranking**; back returns to the previous tab.

**Bottom navigation** — uses system **navigation bar insets** (not a fixed 64dp height) so labels are not clipped on gesture nav or edge-to-edge.

## Run

- **Android Studio:** Open the `android-app` folder, Run `app`.
- **CLI:** `./gradlew :app:installDebug` with a device or emulator connected.

## Package

- `applicationId`: `com.pitwall.paddock` — distinct from the Flutter app (`com.pitwall.app`).

## Next steps (with Taha’s API)

- Replace `MockSonomaData` with `GET` map/marker JSON from the backend.
- `POST` pre-brief with `markers_selected` per ADR-014.
- Wire `OnTrack` to continuous location and cue logic when the bridge exposes the right payloads.

The existing Flutter stack under `flutter/` is unchanged; this module is a parallel **Jetpack** client for UX work on **Maps + WebView** and the Pixel.
