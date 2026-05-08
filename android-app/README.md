# Pitwall Android (Jetpack Compose)

Native Android client for Pitwall: **same HTTP bridge** as [`src/pwa/`](../src/pwa/) (`docs/api.md`). This module replaced the older Maps/WebView prototype that lived here previously.

## Prerequisites

| Requirement | Notes |
|-------------|--------|
| **JDK 17** | Android Gradle Plugin 8.7.x — use Android Studio’s bundled JBR or Temurin 17. |
| **Android SDK** | Platform **API 35** recommended (compileSdk 35). Install via Android Studio SDK Manager. |
| **Android Studio** | Koala+ — open the **`android-app`** folder. |

## First-time setup

1. Copy `local.properties.example` to **`local.properties`** (gitignored) and set:

   ```properties
   sdk.dir=/path/to/Android/sdk
   ```

   macOS default: `~/Library/Android/sdk`.

2. Optional — override the Pitwall bridge base URL (must include trailing slash in Gradle-generated `BuildConfig`, default is `http://127.0.0.1:8765/`):

   ```properties
   # Emulator talking to Flask on the host machine:
   PITWALL_API_BASE_URL=http://10.0.2.2:8765/

   # Physical device + Termux bridge on the same phone:
   PITWALL_API_BASE_URL=http://127.0.0.1:8765/
   ```

3. Start the Python bridge from the repo root (see root [`README.md`](../README.md)), then build:

   ```bash
   cd android-app
   ./gradlew :app:assembleDebug
   ./gradlew :app:installDebug   # device or emulator connected
   ```

## Android Skills (contributors)

[Android Skills](https://github.com/android/skills) are **SKILL.md** packs for coding agents (Gemini, Antigravity, IDE assistants), **not** runtime dependencies.

Install the [Android CLI](https://developer.android.com/studio/command-line) as documented upstream, then from the repo root (or your agent config directory):

```bash
android skills add --skill=edge-to-edge --project=.
android skills add --skill=navigation-3 --project=.
```

Use **edge-to-edge** before polishing fullscreen HUD layouts; **navigation-3** when expanding the route graph in `ui/navigation/`.

## App architecture

| Area | Location |
|------|----------|
| **Routes** | [`Routes.kt`](app/src/main/java/com/pitwall/app/ui/navigation/Routes.kt) — aligned with [`src/pwa/src/app/router/index.ts`](../src/pwa/src/app/router/index.ts). |
| **Nav host** | [`PitwallNavHost.kt`](app/src/main/java/com/pitwall/app/ui/navigation/PitwallNavHost.kt) |
| **REST API** | [`PitwallApi.kt`](app/src/main/java/com/pitwall/app/data/remote/PitwallApi.kt), DTOs in [`BridgeDtos.kt`](app/src/main/java/com/pitwall/app/data/remote/BridgeDtos.kt) — extend alongside [`bridge.ts`](../src/pwa/src/shared/api/bridge.ts). |
| **SSE (live cues)** | [`HudViewModel.kt`](app/src/main/java/com/pitwall/app/ui/hud/HudViewModel.kt) → `GET /cues/stream` |

Cleartext HTTP to localhost is allowed via [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml) for development and on-device Termux.

## Parity status

| Status | Scope |
|--------|--------|
| **Implemented** | Same Flask bridge as PWA (`docs/api.md`): health, sessions, session detail, lap / ideal / distribution / sectors, scorecard + bundle sections, signals, coach flows, insights, evolution, diagnostics, **three-slot save store** (`Routes.SAVE`), **`GET /sessions` merged into active slot** after bridge session list load, **session bridge tools** (sync preview, Parquet export to cache, burst reset), **track reference** (markers / danger / weather / elevation), garage + analysis hubs, HUD SSE, settings + **system night** (`NightModeController`). Many analytics screens still render structured JSON (see `SqlConsoleScreen`, replay, etc.). |
| **Not native-parity** | PWA shell visuals, DuckDB-Wasm in-browser SQL, `POST /coach/ask/stream`, sprite/audio polish. Leaderboard remains mock until a shared API exists. |

## Package / application id

- **`applicationId`**: `com.pitwall.app`

## License

Follow the Pitwall repo license once published; same as root [`README.md`](../README.md).
