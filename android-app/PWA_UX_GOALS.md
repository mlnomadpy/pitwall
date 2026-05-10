# Android ↔ PWA experience goals

This app targets **functional parity** with `src/pwa/` and **UX parity** with the Pitwall cyber-racing shell: same routes, save-slot contract, bridge behaviour, and predictable navigation — implemented with Material 3, responsive layouts (phone vs tablet width), motion preferences (`reducedMotion`), and theme tokens aligned to the web palette (ink, slate, cyan primary, curb accents).

**Non-goals (today):** pixel-perfect clone of Vue components, in-browser DuckDB, WebAudio SFX. These remain web-first unless we add native equivalents.

**When adding a screen:** mirror the PWA route and data sources from `docs/api.md`; prefer typed DTOs + shared naming with `BridgeDtos.kt`; use `PitwallTileCard` / `PitwallSlotCard` for hub surfaces so visual language stays consistent.

**Display › Night mode** updates both the in-app `PitwallTheme` (save slot) and **system UI night** via `NightModeController` + `Theme.Material3.DayNight` (see `NightModeController.kt`, `MainActivity`). Coach select and car setup use the same tile/panel components as the garage for a single visual system.
